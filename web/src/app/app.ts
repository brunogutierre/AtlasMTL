import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Placeholder shell; the map and chat panels arrive in milestone M3. */
@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
