import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  it('renders the product name', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('h1')?.textContent).toBe('AtlasMTL');
  });
});
