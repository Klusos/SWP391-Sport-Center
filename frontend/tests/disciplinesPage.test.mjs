import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { formatSportIndex } from '../src/public-site/pages/disciplinePresentation.js'
import { getCourseStateForSport, isValidPublicSportId, requestCoursesForSport } from '../src/public-site/pages/disciplineCourseRequest.js'
import { courseAgePolicyLabel } from '../src/courseAgeEligibility.js'

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
  assert.match(page, /routeCourseState\.status === 'loading'/)
  assert.match(page, /routeCourseState\.status === 'error'/)
  assert.match(page, /routeCourseState\.status === 'empty'/)
  assert.match(page, /sportLoading \? <p className="disciplines-state" role="status">/)
  assert.match(page, /role="alert"/)
  assert.match(page, /sports\.length === 0/)
})

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

const flushPromises = () => new Promise((resolve) => setImmediate(resolve))

test('switching sports hides the old roster immediately and ignores a delayed old success', async () => {
  const requestA = deferred()
  const requestB = deferred()
  const states = []
  const cleanupA = requestCoursesForSport('1', () => requestA.promise, (state) => states.push(state))
  cleanupA()
  requestCoursesForSport('2', () => requestB.promise, (state) => states.push(state))
  requestB.resolve([{ id: 22, name: 'Sport B course' }])
  await flushPromises()
  requestA.resolve([{ id: 11, name: 'Sport A course' }])
  await flushPromises()

  assert.deepEqual(states.map(({ sportId, status }) => [sportId, status]), [
    ['1', 'loading'], ['2', 'loading'], ['2', 'success'],
  ])
  assert.deepEqual(states.at(-1).courses, [{ id: 22, name: 'Sport B course' }])
})

test('older success arriving after newer success cannot replace the selected sport results', async () => {
  const older = deferred()
  const newer = deferred()
  const states = []
  const invalidateOlder = requestCoursesForSport('10', () => older.promise, (state) => states.push(state))
  requestCoursesForSport('20', () => newer.promise, (state) => states.push(state))
  newer.resolve([{ id: 20 }])
  await flushPromises()
  invalidateOlder()
  older.resolve([{ id: 10 }])
  await flushPromises()
  assert.deepEqual(states.at(-1).courses, [{ id: 20 }])
  assert.equal(states.at(-1).sportId, '20')
})

test('delayed failure from an invalidated sport cannot overwrite newer successful results', async () => {
  const older = deferred()
  const newer = deferred()
  const states = []
  const invalidateOlder = requestCoursesForSport('3', () => older.promise, (state) => states.push(state))
  requestCoursesForSport('4', () => newer.promise, (state) => states.push(state))
  newer.resolve([{ id: 40 }])
  await flushPromises()
  invalidateOlder()
  older.reject(new Error('old request failed'))
  await flushPromises()
  assert.equal(states.at(-1).status, 'success')
  assert.equal(states.at(-1).sportId, '4')
})

test('current request failure, empty result, and recovery have exclusive states', async () => {
  const states = []
  await requestCoursesForSport('5', () => Promise.reject(new Error('service unavailable')), (state) => states.push(state))
  await flushPromises()
  assert.deepEqual(states.at(-1), { sportId: '5', status: 'error', courses: [], error: 'service unavailable' })

  await requestCoursesForSport('6', () => Promise.resolve([]), (state) => states.push(state))
  await flushPromises()
  assert.deepEqual(states.at(-1), { sportId: '6', status: 'empty', courses: [], error: '' })

  await requestCoursesForSport('7', () => Promise.resolve([{ id: 70 }]), (state) => states.push(state))
  await flushPromises()
  assert.deepEqual(states.at(-1), { sportId: '7', status: 'success', courses: [{ id: 70 }], error: '' })
})

test('invalid sport IDs do not call the API and publish a safe error state', () => {
  let called = false
  const states = []
  requestCoursesForSport('../admin', () => { called = true }, (state) => states.push(state))
  assert.equal(called, false)
  assert.deepEqual(states, [{ sportId: '../admin', status: 'error', courses: [], error: 'Mã bộ môn không hợp lệ.' }])
  assert.equal(isValidPublicSportId('12'), true)
  assert.equal(isValidPublicSportId('0'), false)
  assert.equal(isValidPublicSportId('1/2'), false)
})

test('a newly selected sport renders as loading without the previously selected sport courses', () => {
  const priorState = { sportId: '8', status: 'success', courses: [{ id: 80 }], error: '' }
  assert.deepEqual(getCourseStateForSport('9', priorState), {
    sportId: '9', status: 'loading', courses: [], error: '',
  })
  assert.deepEqual(getCourseStateForSport('bad-id', priorState), {
    sportId: 'bad-id', status: 'error', courses: [], error: 'Mã bộ môn không hợp lệ.',
  })
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

test('sport detail uses the selected API image with fallback and preserves API course IDs', async () => {
  const page = await readFile(new URL('../src/public-site/pages/Disciplines.jsx', import.meta.url), 'utf8')
  const css = await readFile(new URL('../src/public-site/pages/Disciplines.css', import.meta.url), 'utf8')
  assert.match(page, /resolveImageUrl\(selectedSport\?\.imageUrl\) \|\| '\/images\/disciplines-hero\.png'/)
  assert.match(page, /requestCoursesForSport\(sportId, getPublicCoursesBySportApi, setCourseState\)/)
  assert.match(page, /data-count=\{routeCourseState\.courses\.length\}/)
  assert.match(page, /course\.level &&/)
  assert.doesNotMatch(page, /course\.level \|\| ['"]COURSE['"]|course\.description \|\| course\.learningOutcome \|\|/)
  assert.match(page, /to=\{`\/khoa-hoc\/\$\{course\.id\}`\}/)
  assert.match(page, /className="disciplines-course-action"/)
  assert.match(page, /showBrand=\{!sportId\}/)
  assert.match(page, /<h2>\{sportId \? 'Khóa học hiện có'/)
  assert.match(page, /course\.description\s*\?/)
  assert.match(page, /Kết quả mong đợi:/)
  assert.match(page, /courseAgePolicyLabel\(course\)/)
  assert.match(css, /\.public-site \.sport-detail-page \.page-hero\.has-image \{[\s\S]*?min-height: 300px;[\s\S]*?aspect-ratio: auto/)
  assert.match(css, /\.disciplines-course-grid \{[\s\S]*?justify-content: center/)
  assert.match(css, /\.disciplines-course-grid \.disciplines-course-card \{[\s\S]*?max-width: 380px/)
  assert.doesNotMatch(css, /\.disciplines-course-grid\[data-count="1"\]/)
  assert.match(css, /\.sport-detail-page \.page-hero/)
  assert.match(css, /:focus-visible/)
})

test('course age copy distinguishes a recommendation from an enrollment requirement', () => {
  assert.equal(courseAgePolicyLabel({ agePolicy: 'RECOMMENDED' }), 'Độ tuổi khuyến nghị')
  assert.equal(courseAgePolicyLabel({ agePolicy: 'REQUIRED' }), 'Độ tuổi bắt buộc')
})

test('sport detail explicitly overrides the shared image hero sizing and aligns the catalog back link', async () => {
  const page = await readFile(new URL('../src/public-site/pages/Disciplines.jsx', import.meta.url), 'utf8')
  const css = await readFile(new URL('../src/public-site/pages/Disciplines.css', import.meta.url), 'utf8')
  assert.match(page, /<span className="disciplines-back-icon" aria-hidden="true">←<\/span><span>Tất cả bộ môn<\/span>/)
  assert.match(css, /\.public-site \.sport-detail-page \.page-hero\.has-image \{[\s\S]*?min-height: 300px;[\s\S]*?aspect-ratio: auto/)
  assert.match(css, /@media \(max-width: 900px\)[\s\S]*?min-height: 270px/)
  assert.match(css, /@media \(max-width: 600px\)[\s\S]*?min-height: 220px/)
  assert.match(css, /\.sport-detail-page \.disciplines-back \.link-arrow \{[\s\S]*?display: inline-flex;[\s\S]*?flex-direction: row;[\s\S]*?flex-wrap: nowrap;[\s\S]*?align-items: center;[\s\S]*?gap: 8px;[\s\S]*?width: max-content;[\s\S]*?white-space: nowrap/)
  assert.match(css, /\.sport-detail-page \.disciplines-back \.link-arrow \{[\s\S]*?margin-bottom: 0;/)
})
