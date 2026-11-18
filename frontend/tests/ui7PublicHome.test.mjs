import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { facilities } from '../src/public-site/data/content.js'

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8')

test('Admin class management remains available through Courses without a duplicate sidebar entry', async () => {
  const sidebar = await read('../src/components/Sidebar.jsx')
  const app = await read('../src/App.jsx')
  assert.match(sidebar, /\["courses",/)
  assert.doesNotMatch(sidebar, /\["classes",/)
  assert.match(app, /path="\/staff\/courses"[\s\S]*?element=\{<CourseList/)
  assert.match(app, /path="\/admin\/classes"[\s\S]*?Navigate to="\/staff\/courses"/)
})

test('Landing facilities reuse the accurately mapped records and images used by Facilities', async () => {
  const imageByKey = new Map(facilities.map((facility) => [facility.homeKey, facility.img]))
  assert.equal(imageByKey.get('gym'), '/images/gym.jpg')
  assert.equal(imageByKey.get('pool'), '/images/facility-2.png')
  assert.equal(imageByKey.get('studio'), '/images/facility-3.png')
  const landing = await read('../src/landing/LandingPage.jsx')
  assert.match(landing, /import \{ facilities \} from "\.\.\/public-site\/data\/content\.js"/)
  assert.match(landing, /facilities\.filter\(\(facility\) => facility\.homeKey\)/)
  assert.match(landing, /<Img src=\{f\.img\} alt=\{f\.title\}/)
})

test('Landing hero uses the existing local sports center photograph with scoped cover styling', async () => {
  const css = await read('../src/landing/landing.css')
  const landing = await read('../src/landing/LandingPage.jsx')
  assert.match(landing, /<Img src="\/images\/trungtam\.jpg" alt=/)
  assert.match(css, /\.lp-hero-photo\s*\{[^}]*object-fit:\s*cover/s)
  assert.doesNotMatch(landing, /pages\/Home|home-hero/)
})

test('Member List search placeholder is rendered as Vietnamese text', async () => {
  const memberList = await read('../src/pages/MemberList.jsx')
  const expected = 'T' + String.fromCodePoint(0x00EC) + 'm t' + String.fromCodePoint(0x00EA) + 'n, email, S' + String.fromCodePoint(0x0110) + 'T ho' + String.fromCodePoint(0x1EB7) + 'c ID'
  assert.ok(memberList.includes('placeholder="' + expected + '"'))
  assert.doesNotMatch(memberList, /placeholder="T\\u00ecm/)
})
