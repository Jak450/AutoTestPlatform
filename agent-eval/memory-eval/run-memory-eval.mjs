import fs from 'node:fs'

const BASE = process.env.EVAL_BASE ?? 'http://127.0.0.1:8080'
const TOKEN = process.env.TOKEN
const topK = Number(process.argv.find((a) => a.startsWith('--topK='))?.split('=')[1] ?? 5)

if (!TOKEN) {
  console.error('缺少 TOKEN 环境变量')
  process.exit(1)
}

const lines = fs.readFileSync(
  new URL('./golden-set.jsonl', import.meta.url), 'utf8'
).trim().split('\n').filter(Boolean)

let recallSum = 0
let mrrSum = 0
const byScenario = {}

for (const line of lines) {
  const { query, expected, scenario = 'default' } = JSON.parse(line)
  const res = await fetch(
    `${BASE}/api/agent/memory/retrieve?query=${encodeURIComponent(query)}&topK=${topK}`,
    { headers: { Authorization: `Bearer ${TOKEN}` } }
  )
  if (!res.ok) {
    console.error(`查询失败 ${query}: HTTP ${res.status}`)
    process.exit(1)
  }
  const { data } = await res.json()
  const hits = data.items.map((i) => `${i.type}:${i.id}`)
  const hit = expected.map((e) => hits.indexOf(e))
  const recall = hit.filter((i) => i >= 0).length / expected.length
  const first = hit.filter((i) => i >= 0).sort((a, b) => a - b)[0]
  const mrr = first === undefined ? 0 : 1 / (first + 1)
  recallSum += recall
  mrrSum += mrr
  byScenario[scenario] ??= { recall: 0, mrr: 0, n: 0 }
  byScenario[scenario].recall += recall
  byScenario[scenario].mrr += mrr
  byScenario[scenario].n += 1
  if (recall < 1) {
    console.log(`MISS query=${query} expected=${expected.join(',')} hits=${hits.join(',')}`)
  }
}

const n = lines.length
console.log(`Recall@${topK}: ${(recallSum / n).toFixed(3)}`)
console.log(`MRR@${topK}: ${(mrrSum / n).toFixed(3)}`)
for (const [s, v] of Object.entries(byScenario)) {
  console.log(`  [${s}] n=${v.n} Recall=${(v.recall / v.n).toFixed(3)} MRR=${(v.mrr / v.n).toFixed(3)}`)
}
