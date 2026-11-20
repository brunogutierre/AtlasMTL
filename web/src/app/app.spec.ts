import { TestBed } from '@angular/core/testing';
import { provideTessera } from '@brunogutierre/tessera';
import { App } from './app';
import { I18n } from './i18n';

describe('App', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideTessera({ theme: 'enterprise', mode: 'light' })],
    });
  });

  async function render() {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('renders the shell and the data attribution', async () => {
    TestBed.inject(I18n).language.set('en');
    const { element } = await render();

    expect(element.querySelector('h1')?.textContent).toBe('AtlasMTL');
    expect(element.querySelector('#chat-title')?.textContent).toBe('Ask a question');
    expect(element.querySelector('.attribution')?.textContent).toContain('CC BY 4.0');
  });

  it('switches to French and remembers it', async () => {
    const i18n = TestBed.inject(I18n);
    i18n.language.set('en');
    const { fixture, element } = await render();

    (element.querySelector('nav > button') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(element.querySelector('#chat-title')?.textContent).toBe('Posez une question');
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
