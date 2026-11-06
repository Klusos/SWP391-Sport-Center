import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { visibleAdminDashboardSports } from '../src/adminDashboard.js'

const makeSports = (count) => Array.from({ length: count }, (_, index) => ({ id: index + 100, name: `Sport ${index + 1}` }))

test('six sports are all visible by default and need no expansion control', async () => {
  const sports = makeSports(6)
  assert.deepEqual(visibleAdminDashboardSports(sports), sports)
  const source = await readFile(new URL('../src/pages/Dashboard.jsx', import.meta.url), 'utf8')
  assert.match(source, /sports\.length > 6/)
})

test('seven sports show six initially, expand to all, and collapse back to six', () => {
  const sports = makeSports(7)
  assert.deepEqual(visibleAdminDashboardSports(sports).map((sport) => sport.id), [100, 101, 102, 103, 104, 105])
  assert.deepEqual(visibleAdminDashboardSports(sports, true), sports)
  assert.deepEqual(visibleAdminDashboardSports(sports, false), sports.slice(0, 6))
})

test('Admin dashboard KPI and sports list use the same API response without hardcoded records', async () => {
  const dashboard = await readFile(new URL('../src/pages/Dashboard.jsx', import.meta.url), 'utf8')
  const api = await readFile(new URL('../src/api.js', import.meta.url), 'utf8')
  const adminView = dashboard.slice(dashboard.indexOf('function Admin('), dashboard.indexOf('const RECEPTIONIST_ICON'))
  assert.match(dashboard, /ADMIN:\s*\{[\s\S]*?sports:\s*getSportsApi/)
  assert.match(api, /export function getSportsApi\(token\)[\s\S]*?request\("\/api\/sports"/)
  assert.match(adminView, /value: total\(d\.sports\)/)
  assert.match(adminView, /const sports = list\(d\.sports\)/)
  assert.doesNotMatch(adminView, /Badminton|Basketball|Swimming|Tennis|Yoga|Fitness|1002/)
  assert.doesNotMatch(adminView, /\.sort\(|status\s*===\s*["']ACTIVE/)
})

test('all loaded sports can be expanded with accessible control and existing states remain', async () => {
  const dashboard = await readFile(new URL('../src/pages/Dashboard.jsx', import.meta.url), 'utf8')
  const adminView = dashboard.slice(dashboard.indexOf('function Admin('), dashboard.indexOf('const RECEPTIONIST_ICON'))
  assert.match(adminView, /aria-expanded=\{showAllSports\}/)
  assert.match(adminView, /Xem tất cả \(\$\{sports\.length\}\)/)
  assert.match(adminView, /Thu gọn/)
  assert.match(adminView, /errors\.sports[\s\S]*role="alert"/)
  assert.match(adminView, /sports\.length === 0/)
  assert.match(dashboard, /loading \? <div className="queue-state">/)
})

test('Admin sports rows have visible control focus styling and no clipping rule', async () => {
  const css = await readFile(new URL('../src/pages/Dashboard.css', import.meta.url), 'utf8')
  assert.match(css, /\.admin-dashboard \.admin-sports-toggle:focus-visible/)
  assert.doesNotMatch(css, /\.admin-dashboard[^{]*\{[^}]*max-height:[^}]*overflow:\s*hidden/s)
})
