#!/usr/bin/env node
/**
 * 生产构建 + 校验相对资源路径，供 Tauri frontendDist 挂载。
 * 输出：apps/web/dist/ 与 dist/.tauri-frontend-ready.json
 */
import { execSync } from 'node:child_process'
import { readFileSync, writeFileSync, existsSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'

const root = join(fileURLToPath(new URL('.', import.meta.url)), '..')
const dist = join(root, 'dist')

console.log('[prepare-tauri-frontend] npm run build …')
execSync('npm run build', { cwd: root, stdio: 'inherit' })

const indexPath = join(dist, 'index.html')
if (!existsSync(indexPath)) {
  console.error('缺少 dist/index.html')
  process.exit(1)
}
const html = readFileSync(indexPath, 'utf8')
const absHits = [...html.matchAll(/(?:src|href)=["'](\/[^"']+)["']/g)].map((m) => m[1])
if (absHits.length) {
  console.error('index.html 仍含绝对路径（Tauri 会挂），请确认 vite base: "./"：', absHits)
  process.exit(1)
}
if (!html.includes('./assets/') && !html.includes('assets/')) {
  console.error('未发现相对 assets 引用，请检查构建产物')
  process.exit(1)
}

function walk(dir, list = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) walk(p, list)
    else list.push(p)
  }
  return list
}

const files = walk(dist).filter((p) => !p.endsWith('.tauri-frontend-ready.json'))
const hash = createHash('sha256')
for (const f of files.sort()) {
  hash.update(relative(dist, f))
  hash.update(readFileSync(f))
}
const digest = hash.digest('hex').slice(0, 16)
const stamp = {
  app: '阿乐老师的数学课堂',
  preparedAt: new Date().toISOString(),
  timezoneNote: 'UTC stamp; 用户区 Asia/Shanghai',
  distRel: 'apps/web/dist',
  frontendDistForTauri: '../apps/web/dist',
  fileCount: files.length,
  contentHash16: digest,
  indexTitle: (html.match(/<title>([^<]*)<\/title>/) || [])[1] || '',
}
writeFileSync(join(dist, '.tauri-frontend-ready.json'), JSON.stringify(stamp, null, 2) + '\n')
console.log('[prepare-tauri-frontend] OK', stamp)
