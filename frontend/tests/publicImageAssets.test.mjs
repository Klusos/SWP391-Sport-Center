import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { resolveImgSource } from '../src/public-site/components/imageSource.js'

const publicImages = [
  '/images/admission-hero.png',
  '/images/coach-1.png',
  '/images/coach-2.png',
  '/images/coach-3.png',
  '/images/coach-4.png',
  '/images/coach-5.png',
  '/images/coach-6.png',
  '/images/coaches-hero.png',
  '/images/disciplines-hero.png',
  '/images/facility-1.png',
  '/images/facility-2.png',
  '/images/facility-3.png',
  '/images/facility-4.png',
  '/images/gym.jpg',
  '/images/trungtam.jpg',
  '/images/stadium-hero.png',
]

test('public image paths stay on the frontend origin', () => {
  for (const path of publicImages) {
    const result = resolveImgSource(path, () => assert.fail('local public asset was sent to the API resolver'))
    assert.equal(result, path)
  }
})

test('backend image paths still use the existing API image resolver', () => {
  assert.equal(resolveImgSource('/uploads/coach.png', (path) => `http://localhost:8080${path}`), 'http://localhost:8080/uploads/coach.png')
  assert.equal(resolveImgSource('https://cdn.example.test/coach.png', (path) => `resolved:${path}`), 'resolved:https://cdn.example.test/coach.png')
  assert.equal(resolveImgSource('', () => assert.fail('empty source should not be resolved')), '')
})

test('all referenced local public image files exist', async () => {
  for (const path of publicImages) {
    const file = new URL(`../public${path}`, import.meta.url)
    await assert.doesNotReject(readFile(file), `${path} should exist under frontend/public`)
  }
})

test('Landing owns the root route, retired pages redirect, and remaining Public routes stay registered', async () => {
  const app = await readFile(new URL('../src/App.jsx', import.meta.url), 'utf8')
  const landing = await readFile(new URL('../src/landing/LandingPage.jsx', import.meta.url), 'utf8')
  assert.match(app, /<Route path="\/" element=\{landingRoute\} \/>/)
  assert.match(app, /path="\/landing-preview"[\s\S]*?<Navigate to=\{\{ pathname: "\/", search: location\.search, hash: location\.hash \}\} replace \/>/)
  assert.doesNotMatch(app, /import Home from "\.\/public-site\/pages\/Home"/)
  assert.match(app, /<Route path="\/bo-mon" element={<Disciplines \/>} \/>/)
  assert.match(app, /<Route path="\/bo-mon\/:sportId" element={<Disciplines \/>} \/>/)
  assert.match(app, /path="\/huan-luyen-vien" element=\{<LandingSectionRedirect sectionId="huan-luyen-vien" \/>\}/)
  assert.match(app, /<Route path="\/co-so-vat-chat" element={<Facilities \/>} \/>/)
  assert.match(app, /path="\/tuyen-sinh" element=\{<LandingSectionRedirect sectionId="tuyen-sinh" \/>\}/)
  assert.match(app, /path="\/membership-plans"[\s\S]*?LandingSectionRedirect sectionId="tuyen-sinh"/)
  assert.doesNotMatch(app, /pages\/(?:Admission|Coaches)/)
  assert.match(landing, /getPublicCoachesApi\(\)/)
  assert.match(landing, /getPublicSportsApi\(\)/)
  assert.match(landing, /<Img src="\/images\/trungtam\.jpg"/)
})
