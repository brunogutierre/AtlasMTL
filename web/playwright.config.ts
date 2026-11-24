import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests of the web app in a real browser. The API and the map tiles are stubbed in
 * the browser (e2e/stubs.ts), so the tests need no server, database or model key.
 */
export default defineConfig({
  testDir: 'e2e',
  // Software WebGL (SwiftShader) is memory-hungry: one browser at a time keeps the map stable.
  workers: 1,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4400',
    trace: 'retain-on-failure',
    // MapLibre needs WebGL, which headless Chromium provides through SwiftShader.
    launchOptions: { args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npx ng serve --port 4400',
    url: 'http://localhost:4400',
    reuseExistingServer: !process.env['CI'],
    timeout: 120_000,
  },
});
