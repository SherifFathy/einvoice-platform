import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'auth',
    loadComponent: () => import('./auth/auth.component').then((m) => m.AuthComponent),
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'invoices',
    loadComponent: () =>
      import('./invoices/invoices.component').then((m) => m.InvoicesComponent),
  },
  {
    path: 'customers',
    loadComponent: () =>
      import('./customers/customers.component').then((m) => m.CustomersComponent),
  },
  {
    path: 'items',
    loadComponent: () => import('./items/items.component').then((m) => m.ItemsComponent),
  },
  {
    path: 'config',
    loadComponent: () =>
      import('./config/config.component').then((m) => m.ConfigComponent),
  },
  {
    path: 'logs',
    loadComponent: () => import('./logs/logs.component').then((m) => m.LogsComponent),
  },
  {
    path: 'jobs',
    loadComponent: () => import('./jobs/jobs.component').then((m) => m.JobsComponent),
  },
];
