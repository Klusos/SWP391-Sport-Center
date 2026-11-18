import { expect, test } from '@playwright/test'

const sportsApiPath = '/api/public/sports'

test.describe('Public Disciplines page', () => {
  let sports

  test.beforeEach(async ({ page }) => {
    const sportsResponsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === sportsApiPath && response.request().method() === 'GET'
    })

    await page.goto('/bo-mon')
    const sportsResponse = await sportsResponsePromise
    expect(sportsResponse.ok()).toBe(true)
    sports = await sportsResponse.json()
    expect(Array.isArray(sports)).toBe(true)
    expect(sports.length).toBeGreaterThan(0)
  })

  test('renders the sports returned by the public API', async ({ page }) => {
    const cards = page.locator('.disciplines-sport-card')

    await expect(cards).toHaveCount(sports.length)
    await expect(cards.first()).toBeVisible()

    for (const sport of sports) {
      const card = page.locator(`a[href="/bo-mon/${sport.id}"]`)
      await expect(card).toBeVisible()
      await expect(card.getByRole('heading', { level: 3, name: sport.name })).toBeVisible()
    }
  })

  test('loads each card image', async ({ page }) => {
    const images = page.locator('.disciplines-sport-card img')

    await expect(images).toHaveCount(sports.length)
    await expect.poll(() => images.evaluateAll((nodes) => nodes.every((image) => image.complete && image.naturalWidth > 0))).toBe(true)
  })

  test('opens a sport detail page from its card', async ({ page }) => {
    const firstSport = sports[0]
    const card = page.locator(`a[href="/bo-mon/${firstSport.id}"]`)

    await card.click()

    await expect(page).toHaveURL(new RegExp(`/bo-mon/${firstSport.id}$`))
    await expect(page.getByRole('heading', { level: 1, name: firstSport.name })).toBeVisible()
  })

  for (const viewport of [
    { name: 'desktop', width: 1440, height: 900 },
    { name: 'mobile', width: 390, height: 844 },
  ]) {
    test(`has no horizontal overflow at ${viewport.name} width`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height })

      await expect(page.locator('.disciplines-sport-card').first()).toBeVisible()
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    })
  }
})
