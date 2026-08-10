#!/usr/bin/env node
/**
 * Agent 评测 runner
 * 读取 tasks.jsonl，逐个任务走真实对话链路（登录 -> 建会话 -> 发消息 -> SSE 事件流），
 * 校验工具调用序列与成功标准，输出 report.md 与终端摘要。
 *
 * 用法：
 *   node agent-eval/run-eval.mjs
 * 环境变量：
 *   EVAL_BASE_URL   后端地址，默认 http://localhost:8080
 *   EVAL_USER / EVAL_PASSWORD  登录账号，默认 admin / 12345678
 *   EVAL_ONLY                  只跑指定任务 id，逗号分隔（如 "query_projects,gen_cases_from_doc"）
 */

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const BASE = process.env.EVAL_BASE_URL || 'http://localhost:8080'
const USERNAME = process.env.EVAL_USER || 'admin'
const PASSWORD = process.env.EVAL_PASSWORD || '12345678'

async function login() {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USERNAME, password: PASSWORD })
  })
  const data = await res.json()
  if (data.code !== 1 || !data.data?.token) {
    throw new Error('登录失败: ' + JSON.stringify(data))
  }
  return data.data.token
}

async function api(token, pathname, options = {}) {
  const res = await fetch(`${BASE}${pathname}`, {
    method: options.method || 'GET',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...(options.headers || {})
    },
    body: options.body ? JSON.stringify(options.body) : undefined
  })
  return res.json()
}

async function streamEvents(token, cid) {
  const res = await fetch(`${BASE}/api/agent/conversations/${cid}/stream?lastEventId=0`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  if (!res.ok || !res.body) {
    throw new Error(`SSE 连接失败: HTTP ${res.status}`)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  const events = []
  let buffer = ''
  let current = null
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, idx).trim()
      buffer = buffer.slice(idx + 1)
      if (line.startsWith('event:')) {
        current = { type: line.slice(6).trim(), data: {} }
      } else if (line.startsWith('data:') && current) {
        try {
          const parsed = JSON.parse(line.slice(5).trim())
          current.data = parsed.data || {}
        } catch {
          current.data = {}
        }
        events.push(current)
        current = null
      }
    }
  }
  return events
}

function evaluateCustom(criteria, ctx) {
  if (!criteria || Object.keys(criteria).length === 0) return true
  if (criteria.textContains && !ctx.text.includes(criteria.textContains)) return false
  if (criteria.textNotContains && ctx.text.includes(criteria.textNotContains)) return false
  if (criteria.stopReason && ctx.stopReason !== criteria.stopReason) return false
  if (criteria.toolCalled && !ctx.toolCalls.includes(criteria.toolCalled)) return false
  return true
}

async function runTask(token, task) {
  const conv = await api(token, '/api/agent/conversations', { method: 'POST', body: { title: `eval-${task.id}` } })
  const cid = conv.data?.id
  if (!cid) throw new Error(`建会话失败: ${JSON.stringify(conv)}`)

  if (task.setupUpload) {
    const form = new FormData()
    form.append('file', new Blob([task.setupUpload.content], { type: 'text/markdown' }), task.setupUpload.fileName)
    await fetch(`${BASE}/api/agent/conversations/${cid}/files`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form
    })
  }

  await fetch(`${BASE}/api/agent/conversations/${cid}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ content: task.prompt, idempotencyKey: `eval-${task.id}-${Date.now()}` })
  })

  const events = await streamEvents(token, cid)
  const toolCalls = events.filter((e) => e.type === 'tool_execution_start').map((e) => e.data.toolName || '')
  const toolErrors = events.filter((e) => e.type === 'tool_execution_end' && e.data.status === 'error')
  const agentEnd = events.find((e) => e.type === 'agent_end')
  const text = events.filter((e) => e.type === 'message_update').map((e) => e.data.delta || '').join('').trim()
  const stopReason = agentEnd?.data?.stopReason || 'none'
  const turns = events.filter((e) => e.type === 'turn_start').length
  const tokens = agentEnd?.data?.tokens ?? null

  const ctx = { text, toolCalls, stopReason, events }
  const checks = {
    requiredTools: (task.expectedTools || []).every((t) => toolCalls.includes(t)),
    noStreamError: !events.some((e) => e.type === 'error'),
    stopClean: ['stop', 'awaiting_confirmation', 'aborted'].includes(stopReason)
  }
  const custom = evaluateCustom(task.successCriteria, ctx)
  const pass = Object.values(checks).every(Boolean) && custom

  return {
    id: task.id,
    prompt: task.prompt,
    pass,
    checks,
    custom,
    toolCalls,
    toolErrors: toolErrors.length,
    stopReason,
    turns,
    tokens,
    errors: events.filter((e) => e.type === 'error').map((e) => e.data?.message || '').filter(Boolean)
  }
}

function summarize(results) {
  const pass = results.filter((r) => r.pass).length
  const total = results.length
  const avgTurns = total ? (results.reduce((s, r) => s + r.turns, 0) / total).toFixed(1) : '0'
  const totalTokens = results.reduce((s, r) => s + (r.tokens || 0), 0)
  return {
    total,
    pass,
    fail: total - pass,
    passRate: total ? ((pass / total) * 100).toFixed(0) + '%' : '-',
    avgTurns,
    totalTokens,
    avgTokens: total ? Math.round(totalTokens / total) : 0
  }
}

function render(summary, results) {
  const lines = [
    '# Agent 评测报告',
    '',
    `- 生成时间: ${new Date().toLocaleString()}`,
    `- 通过率: ${summary.pass}/${summary.total} (${summary.passRate})`,
    `- 平均轮数: ${summary.avgTurns}`,
    `- token 总量: ${summary.totalTokens}，平均 ${summary.avgTokens}/任务`,
    '',
    '| 任务 | 结果 | 工具调用 | stopReason | 轮数 | tokens |',
    '|---|---|---|---|---|---|'
  ]
  for (const r of results) {
    lines.push(`| ${r.id} | ${r.pass ? '✅' : '❌'} | ${r.toolCalls.join(', ') || '-'} | ${r.stopReason} | ${r.turns} | ${r.tokens ?? '-'} |`)
    if (!r.pass) {
      lines.push('', `**${r.id}** 失败明细：`)
      lines.push(`- checks: ${JSON.stringify(r.checks)}`)
      lines.push(`- custom: ${r.custom}`)
      if (r.errors.length) lines.push(`- errors: ${r.errors.join('; ')}`)
    }
  }
  lines.push('')
  return lines.join('\n')
}

async function main() {
  const token = await login()
  const tasks = fs.readFileSync(path.join(__dirname, 'tasks.jsonl'), 'utf8')
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
    .map((l) => JSON.parse(l))

  const only = (process.env.EVAL_ONLY || '').split(',').map((s) => s.trim()).filter(Boolean)
  const filtered = only.length ? tasks.filter((t) => only.includes(t.id)) : tasks
  if (filtered.length === 0) {
    throw new Error('EVAL_ONLY 没有匹配到任何任务: ' + process.env.EVAL_ONLY)
  }

  const results = []
  for (const task of filtered) {
    process.stdout.write(`▶ ${task.id} ... `)
    try {
      const r = await runTask(token, task)
      results.push(r)
      process.stdout.write(`${r.pass ? 'PASS' : 'FAIL'} (${r.stopReason})\n`)
    } catch (e) {
      results.push({ id: task.id, prompt: task.prompt, pass: false, error: e.message })
      process.stdout.write(`ERROR ${e.message}\n`)
    }
  }

  const summary = summarize(results)
  fs.writeFileSync(path.join(__dirname, 'report.md'), render(summary, results))
  console.log(JSON.stringify(summary, null, 2))
  console.log('报告已写入 agent-eval/report.md')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
