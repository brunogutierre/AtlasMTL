import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { stubApi } from './stubs';

/** Serious and critical WCAG 2.2 AA problems on the current page. */
async function accessibilityProblems(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag22aa']).analyze();
  return results.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
}

test('answers a question with a trace, a result list and the map', async ({ page }) => {
  const questions = await stubApi(page);
  await page.goto('/');

  await page.getByRole('textbox', { name: 'Your question' }).fill('Large parks in the Plateau?');
  await page.keyboard.press('Enter');

  await expect(page.getByText('I found 2 large parks in Le Plateau-Mont-Royal: La Fontaine and Jeanne-Mance.')).toBeVisible();
  expect(questions).toEqual(['Large parks in the Plateau?']);
  await page.getByText('How I found it (1 step)').click();
  await expect(page.locator('.trace li')).toHaveText(/Searched for places.*category: PARK.*→ 2 found/);

  const fontaine = page.getByRole('button', { name: /La Fontaine/ });
  await fontaine.click();
  await expect(fontaine).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('heading', { name: 'On the map (2)' })).toBeVisible();
});

test('asks an example question from the gallery', async ({ page }) => {
  const questions = await stubApi(page);
  await page.goto('/');

  await page.getByRole('button', { name: 'Which borough has the most parks per km²?' }).click();

  await expect(page.getByText(/La Fontaine and Jeanne-Mance/)).toBeVisible();
  expect(questions).toEqual(['Which borough has the most parks per km²?']);
});

test('explains a spent budget in French', async ({ page }) => {
  await stubApi(page, { status: 429, body: { title: 'Limit reached', code: 'global_budget' } });
  await page.goto('/');
  await page.getByRole('button', { name: 'Français' }).click();

  await page.getByRole('textbox', { name: 'Votre question' }).fill('Écoles à Anjou ?');
  await page.keyboard.press('Enter');

  await expect(page.getByText(/Le budget du jour pour les nouvelles réponses est épuisé/)).toBeVisible();
});

test('shows the public statistics', async ({ page }) => {
  await stubApi(page);
  await page.goto('/');

  await page.getByRole('link', { name: 'Stats' }).click();

  await expect(page).toHaveURL(/\/stats$/);
  await expect(page.getByRole('heading', { name: 'How AtlasMTL is used' })).toBeVisible();
  await expect(page.getByText('1,234')).toBeVisible();
  await expect(page.getByText('37.5%')).toBeVisible();
});

for (const colorScheme of ['light', 'dark'] as const) {
  test(`has no serious accessibility problems (${colorScheme})`, async ({ page }) => {
    await page.emulateMedia({ colorScheme });
    await stubApi(page);
    await page.goto('/');
    expect(await accessibilityProblems(page)).toEqual([]);

    await page.getByRole('textbox', { name: 'Your question' }).fill('Large parks?');
    await page.keyboard.press('Enter');
    await expect(page.getByText(/Jeanne-Mance\./)).toBeVisible();
    expect(await accessibilityProblems(page)).toEqual([]);

    await page.getByRole('link', { name: 'Stats' }).click();
    await expect(page.getByRole('heading', { name: 'How AtlasMTL is used' })).toBeVisible();
    expect(await accessibilityProblems(page)).toEqual([]);
  });
}
