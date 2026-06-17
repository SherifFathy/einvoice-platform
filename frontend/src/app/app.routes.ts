import { Routes } from '@angular/router';
import { authGuard } from './shared/guards/auth.guard';
import { adminGuard } from './shared/guards/admin.guard';
import { operationalModeGuard } from './shared/guards/operational-mode.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./auth/auth.component').then((m) => m.AuthComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'submission-log',
        canActivate: [operationalModeGuard],
        loadComponent: () =>
          import('./submission-log/submission-log.component').then((m) => m.SubmissionLogComponent),
      },
      {
        path: 'admin',
        canActivate: [adminGuard],
        children: [
          {
            path: '',
            redirectTo: 'companies',
            pathMatch: 'full',
          },
          {
            path: 'companies',
            loadComponent: () =>
              import('./admin/companies/company-list.component').then((m) => m.CompanyListComponent),
          },
          {
            path: 'branches',
            loadComponent: () =>
              import('./admin/branches/branch-list.component').then((m) => m.BranchListComponent),
          },
          {
            path: 'branding',
            loadComponent: () =>
              import('./admin/branding/branding.component').then((m) => m.BrandingComponent),
          },
          {
            path: 'users',
            loadComponent: () =>
              import('./admin/users/user-list.component').then((m) => m.UserListComponent),
          },
          {
            path: 'assignments',
            loadComponent: () =>
              import('./admin/assignments/assignment-list.component').then((m) => m.AssignmentListComponent),
          },
        ],
      },
      {
        path: 'invoices/eta',
        children: [
          {
            path: '',
            loadComponent: () => import('./invoices/eta/eta-invoice-list.component').then(m => m.EtaInvoiceListComponent),
          },
          {
            path: 'new',
            loadComponent: () => import('./invoices/eta/eta-invoice-form.component').then(m => m.EtaInvoiceFormComponent),
          },
          {
            path: ':id',
            loadComponent: () => import('./invoices/eta/eta-invoice-detail.component').then(m => m.EtaInvoiceDetailComponent),
          },
          {
            path: ':id/edit',
            loadComponent: () => import('./invoices/eta/eta-invoice-form.component').then(m => m.EtaInvoiceFormComponent),
          },
        ],
      },
      {
        path: 'invoices',
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./invoices/invoice-list/invoice-list-page.component').then((m) => m.InvoiceListPageComponent),
          },
          {
            path: 'new',
            loadComponent: () =>
              import('./invoices/invoice-form/invoice-form-page.component').then((m) => m.InvoiceFormPageComponent),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./invoices/invoice-detail/invoice-detail-page.component').then((m) => m.InvoiceDetailPageComponent),
          },
          {
            path: ':id/edit',
            loadComponent: () =>
              import('./invoices/invoice-form/invoice-form-page.component').then((m) => m.InvoiceFormPageComponent),
          },
        ],
      },
      {
        path: 'standard',
        children: [
          {
            path: '',
            loadChildren: () => import('./standard/standard-routing.module').then(m => m.StandardRoutingModule),
          },
        ],
      },
      {
        path: 'simplified',
        children: [
          {
            path: '',
            loadChildren: () => import('./simplified/simplified-routing.module').then(m => m.SimplifiedRoutingModule),
          },
        ],
      },
      {
        path: 'receipts/eta',
        children: [
          {
            path: '',
            loadComponent: () => import('./receipts/eta/eta-receipt-list.component').then(m => m.EtaReceiptListComponent),
          },
          {
            path: 'new',
            loadComponent: () => import('./receipts/eta/eta-receipt-form.component').then(m => m.EtaReceiptFormComponent),
          },
          {
            path: ':id',
            loadComponent: () => import('./receipts/eta/eta-receipt-detail.component').then(m => m.EtaReceiptDetailComponent),
          },
          {
            path: ':id/edit',
            loadComponent: () => import('./receipts/eta/eta-receipt-form.component').then(m => m.EtaReceiptFormComponent),
          },
        ],
      },
      {
        path: 'customers',
        canActivate: [operationalModeGuard],
        children: [
          {
            path: '',
            loadChildren: () => import('./customers/customer-routing.module').then((m) => m.CustomerRoutingModule),
          },
        ],
      },
      {
        path: 'items',
        canActivate: [operationalModeGuard],
        children: [
          {
            path: '',
            loadChildren: () => import('./items/item-routing.module').then((m) => m.ItemRoutingModule),
          },
        ],
      },
      {
        path: 'config',
        canActivate: [operationalModeGuard],
        children: [
          {
            path: '',
            loadChildren: () => import('./config/config-routing.module').then((m) => m.ConfigRoutingModule),
          },
        ],
      },
      {
        path: 'logs',
        loadComponent: () => import('./logs/logs.component').then((m) => m.LogsComponent),
      },
      {
        path: 'jobs',
        loadComponent: () => import('./jobs/jobs.component').then((m) => m.JobsComponent),
      },
    ],
  },
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full',
  },
  {
    path: '**',
    redirectTo: 'dashboard',
  },
];
