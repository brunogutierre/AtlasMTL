import type { Routes } from '@angular/router';
import { Home } from './home/home';

export const routes: Routes = [
  { path: '', component: Home, title: 'AtlasMTL' },
  {
    path: 'stats',
    loadComponent: () => import('./stats/stats-page').then((m) => m.StatsPage),
    title: 'AtlasMTL · Stats',
  },
  { path: '**', redirectTo: '' },
];
