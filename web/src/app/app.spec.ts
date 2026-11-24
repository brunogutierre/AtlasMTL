import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTessera } from '@brunogutierre/tessera';
import { App } from './app';
import { I18n } from './i18n';

describe('App', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideTessera({ theme: 'enterprise', mode: 'light' }), provideRouter([])],
    });
  });

  async function render() {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('renders the frame, the page links and the data attribution', async () => {
    TestBed.inject(I18n).language.set('en');
    const { element } = await render();

    expect(element.querySelector('h1')?.textContent).toBe('AtlasMTL');
    expect([...element.querySelectorAll('.pages a')].map((a) => a.textContent?.trim())).toEqual([
      'Ask',
      'Stats',
    ]);
    expect(element.querySelector('.attribution')?.textContent).toContain('CC BY 4.0');
  });

  it('switches to French and remembers it', async () => {
    TestBed.inject(I18n).language.set('en');
    const { fixture, element } = await render();

    (element.querySelector('.settings > button') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(element.querySelector('.pages a')?.textContent?.trim()).toBe('Demander');
    expect(document.documentElement.lang).toBe('fr');
    expect(localStorage.getItem('atlasmtl.language')).toBe('fr');
  });

  it('switches the color mode', async () => {
    const { fixture, element } = await render();
    const dark = element.querySelectorAll<HTMLButtonElement>('[role=group] button')[1];

    dark.click();
    await fixture.whenStable();

    expect(document.documentElement.getAttribute('data-tes-mode')).toBe('dark');
    expect(dark.getAttribute('aria-pressed')).toBe('true');
  });
});
