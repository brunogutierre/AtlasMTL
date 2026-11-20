import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TesAlert, TesCard, TesSpinner, TesStat } from '@brunogutierre/tessera';
import { StatsApi } from '../api';
import { I18n } from '../i18n';

/** Public, aggregate usage numbers: volume, cache, cost, speed and tools. */
@Component({
  selector: 'app-stats-page',
  imports: [TesAlert, TesCard, TesSpinner, TesStat],
  templateUrl: './stats-page.html',
  styleUrl: './stats-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatsPage {
  protected readonly i18n = inject(I18n);
  protected readonly stats = inject(StatsApi).statsResource();

  private readonly locale = computed(() => (this.i18n.language() === 'fr' ? 'fr-CA' : 'en-CA'));
  /** The busiest day sets the scale of the daily bars. */
  protected readonly busiest = computed(() =>
    Math.max(1, ...(this.stats.hasValue() ? this.stats.value().days.map((d) => d.questions) : [])),
  );

  protected number(value: number, digits = 0): string {
    return new Intl.NumberFormat(this.locale(), { maximumFractionDigits: digits }).format(value);
  }

  protected percent(share: number): string {
    return new Intl.NumberFormat(this.locale(), { style: 'percent', maximumFractionDigits: 1 }).format(share);
  }

  protected usd(amount: number): string {
    return new Intl.NumberFormat(this.locale(), {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 4,
    }).format(amount);
  }

  protected seconds(ms: number): string {
    return `${this.number(ms / 1000, 1)} s`;
  }

  protected day(iso: string): string {
    return new Intl.DateTimeFormat(this.locale(), { month: 'short', day: 'numeric', timeZone: 'UTC' }).format(
      new Date(`${iso}T00:00:00Z`),
    );
  }

  protected tool(name: string): string {
    return this.i18n.t().trace.tools[name] ?? name;
  }
}
