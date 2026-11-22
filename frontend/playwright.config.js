import { defineConfig, devices } from '@playwright/test'
import process from 'node:process'
import { playwrightUseOptions, resolvePlaywrightBrowser } from './scripts/playwrightBrowser.js'

const browser = resolvePlaywrightBrowser()

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.js',
  fullyParallel: true,
  reporter: 'list',
  use: {
    ...devices['Desktop Chrome'],
    ...playwrightUseOptions(browser),
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
})
