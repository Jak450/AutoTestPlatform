#!/usr/bin/env node
/**
 * Agent 评测 runner（多轮版）
 * 读取 tasks.jsonl，每个任务跑 EVAL_ROUNDS 轮（默认 1），每轮走完整对话链路
 * （登录 -> 建会话 -> 发消息 -> SSE 事件流），校验工具调用与成功标准，
 * 输出按任务的通过率分布与整体汇总。
 *
 * 用法：
 *   node agent-eval/run-eval.mjs --rounds 3
 * 环境变量：
 *   EVAL_BASE_URL   后端地址，默认 http://localhost:8080
 *   EVAL_USER / EVAL_PASSWORD  登录账号，默认 admin / 12345678
 *   EVAL_ONLY       只跑指定任务 id，逗号分隔
 *   EVAL_ROUNDS     每任务轮数（也可用 --rounds N）
 */

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const BASE = process.env.EVAL_BASE_URL || 'http://localhost:8080'
const USERNAME = process.env.EVAL_USER || 'admin'
const PASSWORD = process.env.EVAL_PASSWORD || '12345678'
const ROUNDS = parseRounds()

function parseRounds() {
  const argIdx = process.argv.indexOf('--rounds')
  if (argIdx >= 0 && process.argv[argIdx + 1]) {
    const n = Number(process.argv[argIdx + 1])
    if (Number.isFinite(n) && n >= 1) return Math.min(Math.floor(n), 10)
  }
  const env = Number(process.env.EVAL_ROUNDS || '1')
  return Number.isFinite(env) && env >= 1 ? Math.min(Math.floor(env), 10) : 1
}

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

async function runRound(token, task) {
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
    body: JSON.stringify({ content: task.prompt, idempotencyKey: `eval-${task.id}-${Date.now()}-${Math.random()}` })
  })

  const events = await streamEvents(token, cid)
  const toolCalls = events.filter((e) => e.type === 'tool_execution_start').map((e) => e.data.toolName || '')
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
    pass,
    toolCalls,
    stopReason,
    turns,
    tokens,
    errors: events.filter((e) => e.type === 'error').map((e) => e.data?.message || '').filter(Boolean)
  }
}

function summarize(taskResults) {
  let totalRounds = 0
  let totalPass = 0
  let totalTokens = 0
  let totalTurns = 0
  for (const t of taskResults) {
    totalRounds += t.rounds
    totalPass += t.pass
    totalTokens += t.tokens
    totalTurns += t.turns
  }
  return {
    tasks: taskResults.length,
    rounds: totalRounds,
    pass: totalPass,
    fail: totalRounds - totalPass,
    passRate: totalRounds ? ((totalPass / totalRounds) * 100).toFixed(0) + '%' : '-',
    avgTurns: totalRounds ? (totalTurns / totalRounds).toFixed(1) : '-',
    avgTokens: totalRounds ? Math.round(totalTokens / totalRounds) : 0
  }
}

function render(summary, taskResults) {
  const lines = [
    '# Agent 评测报告（多轮）',
    '',
    `- 生成时间: ${new Date().toLocaleString()}`,
    `- 任务数: ${summary.tasks}，总轮次: ${summary.rounds}（每任务 ${ROUNDS} 轮）`,
    `- 通过率: ${summary.pass}/${summary.rounds} (${summary.passRate})`,
    `- 平均轮数: ${summary.avgTurns}`,
    `- token 总量: ${summary.totalTokens}，平均 ${summary.avgTokens}/轮`,
    '',
    '| 任务 | 通过率 | 工具调用（最近一轮） | stopReason（最近一轮） | 平均轮数 | 平均tokens |',
    '|---|---|---|---|---|---|'
  ]
  for (const t of taskResults) {
    const last = t.roundsResults[t.roundsResults.length - 1] || {}
    lines.push(`| ${t.id} | ${t.pass}/${t.rounds} | ${(last.toolCalls || []).join(', ') || '-'} | ${last.stopReason || '-'} | ${t.avgTurns} | ${t.avgTokens} |`)
  }
  for (const t of taskResults) {
    if (t.pass < t.rounds) {
      lines.push('', `**${t.id}** 失败明细（通过 ${t.pass}/${t.rounds}）：`)
      t.roundsResults.forEach((r, i) => {
        if (!r.pass) {
          lines.push(`- 第 ${i + 1} 轮: 工具=[${r.toolCalls.join(', ')}] stop=${r.stopReason}${r.errors.length ? ' errors=' + r.errors.join('; ') : ''}`)
        }
      })
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

  const taskResults = []
  for (const task of filtered) {
    process.stdout.write(`▶ ${task.id} (${ROUNDS} 轮) ... `)
    const roundsResults = []
    for (let r = 0; r < ROUNDS; r++) {
      try {
        roundsResults.push(await runRound(token, task))
      } catch (e) {
        roundsResults.push({ pass: false, toolCalls: [], stopReason: 'error', turns: 0, tokens: null, errors: [e.message] })
      }
    }
    const pass = roundsResults.filter((x) => x.pass).length
    const turns = Math.round(roundsResults.reduce((s, x) => s + (x.turns || 0), 0) / ROUNDS)
    const tokens = Math.round(roundsResults.reduce((s, x) => s + (x.tokens || 0), 0) / ROUNDS)
    taskResults.push({ id: task.id, prompt: task.prompt, rounds: ROUNDS, pass, turns, tokens, avgTurns: turns, avgTokens: tokens, roundsResults })
    process.stdout.write(`${pass}/${ROUNDS}\n`)
  }

  const summary = summarize(taskResults)
  fs.writeFileSync(path.join(__dirname, 'report.md'), render(summary, taskResults))
  console.log(JSON.stringify(summary, null, 2))
  console.log('报告已写入 agent-eval/report.md')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
