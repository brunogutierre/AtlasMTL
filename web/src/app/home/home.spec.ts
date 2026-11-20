import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideTessera } from '@brunogutierre/tessera';
import { I18n } from '../i18n';
import { MAP_ENGINE, type MapHandle } from '../map/map-engine';
import { Home } from './home';

const noMap: MapHandle = {
  show() {},
  fit() {},
  setStyle() {},
  select() {},
  onSelect() {},
  destroy() {},
};

describe('Home', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideTessera({ theme: 'enterprise', mode: 'light' }),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAP_ENGINE, useValue: () => noMap },
      ],
    });
    TestBed.inject(I18n).language.set('en');
  });

  it('shows the chat next to the map, with the borough outlines loaded', async () => {
    const fixture = TestBed.createComponent(Home);
    TestBed.tick(); // starts the boroughs request
    TestBed.inject(HttpTestingController).expectOne('/api/boroughs').flush([]);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('#chat-title')?.textContent).toBe('Ask a question');
    expect(element.querySelector('app-chat-panel textarea')).not.toBeNull();
    expect(element.querySelector('app-map-view')).not.toBeNull();
  });
});
