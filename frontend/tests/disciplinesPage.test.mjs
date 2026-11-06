import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { formatSportIndex } from '../src/public-site/pages/disciplinePresentation.js'

test('sport numbering follows rendered API order beyond five records', () => {
  const sports = ['Badminton', 'Basketball', 'Swimming', 'Tennis', 'Yoga', 'Football', 'Volleyball']
  assert.deepEqual(sports.map((_, index) => formatSportIndex(index)), ['01', '02', '03', '04', '05', '06', '07'])
})

test('Disciplines cards and count stay driven by API records and IDs', async () => {
  const page = await readFile(new URL('../src/public-site/pages/Disciplines.jsx', import.meta.url), 'utf8')
  assert.match(page, /getPublicSportsApi\(\)/)
  assert.match(page, /sports\.map\(\(sport, index\)/)
  assert.match(page, /\{sports\.length\}/)
  assert.match(page, /to=\{`\/bo-mon\/\$\{sport\.id\}`\}/)
  assert.match(page, /src=\{sport\.imageUrl\}/)
  assert.doesNotMatch(page, /getSportIcon|disciplines-sport-icon/)
  assert.doesNotMatch(page, /sports\s*=\s*\[/)
})

test('loading, API error, and empty states remain present', async () => {
  const page = await readFile(new URL('../src/public-site/pages/Disciplines.jsx', import.meta.url), 'utf8')
  assert.match(page, /sportLoading \? <p className="disciplines-state" role="status">/)
  assert.match(page, /role="alert"/)
  assert.match(page, /sports\.length === 0/)
})

test('sport cards retain the shared image fallback and keyboard focus treatment', async () => {
  const page = await readFile(new URL('../src/public-site/pages/Disciplines.jsx', import.meta.url), 'utf8')
  const css = await readFile(new URL('../src/public-site/pages/Disciplines.css', import.meta.url), 'utf8')
  const image = await readFile(new URL('../src/public-site/components/Img.jsx', import.meta.url), 'utf8')
  assert.match(page, /<Img src=\{sport\.imageUrl\}/)
  assert.match(image, /resolveImgSource\(src, resolveImageUrl\)/)
  assert.match(css, /\.disciplines-sport-card:focus-visible/)
  assert.doesNotMatch(css, /\.disciplines-sport-icon/)
  assert.match(css, /prefers-reduced-motion: reduce/)
})
