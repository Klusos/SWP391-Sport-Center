import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { facilities } from '../src/public-site/data/content.js'

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8')

test('Admin class management remains available through Courses without a duplicate sidebar entry', async () => {
  const sidebar = await read('../src/components/Sidebar.jsx')
  const app = await read('../src/App.jsx')
  assert.match(sidebar, /\[\"courses\",/)
  assert.doesNotMatch(sidebar, /\[\"classes\",/)
  assert.match(app, /path=\"\/staff\/courses\"[\s\S]*?element=\{<CourseList/)
  assert.match(app, /path=\"\/admin\/classes\"[\s\S]*?Navigate to=\"\/staff\/courses\"/)
})

test('Home facility cards use the same accurately mapped facility records as Facilities', async () => {
  const imageByKey = new Map(facilities.map((facility) => [facility.homeKey, facility.img]))
  assert.equal(imageByKey.get('gym'), '/images/gym.jpg')
  assert.equal(imageByKey.get('pool'), '/images/facility-2.png')
  assert.equal(imageByKey.get('studio'), '/images/facility-3.png')
  const home = await read('../src/public-site/pages/Home.jsx')
  assert.match(home, /homeFacilityImages\[index\]/)
  assert.match(home, /<Img src=\{homeFacilityImages\[index\]\}/)
})

test('Home hero uses the supplied local sports complex photograph and responsive cover styling', async () => {
  const css = await read('../src/public-site/styles.css')
  const home = await read('../src/public-site/pages/Home.jsx')
  assert.match(css, /url\(\"\/images\/trungtam\.jpg\"\)/)
  assert.match(css, /background-size: cover|\/ cover no-repeat/)
  assert.match(css, /linear-gradient\(/)
  assert.match(home, /KH\u1eceE M\u1ed6I NG\u00c0Y\.<br \/>M\u1ea0NH M\u1ed6I B\u01af\u1edaC\./)
})

test('Member List search placeholder is rendered as Vietnamese text', async () => {
  const memberList = await read('../src/pages/MemberList.jsx')
  const expected = 'T' + String.fromCodePoint(0x00EC) + 'm t' + String.fromCodePoint(0x00EA) + 'n, email, S' + String.fromCodePoint(0x0110) + 'T ho' + String.fromCodePoint(0x1EB7) + 'c ID'
  assert.ok(memberList.includes('placeholder="' + expected + '"'))
  assert.doesNotMatch(memberList, /placeholder=\"T\\u00ecm/)
})

