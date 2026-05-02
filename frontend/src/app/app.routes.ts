import { Routes } from '@angular/router';
import { authGuard } from './shared/guards/auth.guard';
import { roleGuard } from './shared/guards/role.guard';
import { unsavedChangesGuard } from './shared/guards/unsaved-changes.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'auth',
    loadComponent: () => import('./auth/auth.component').then((m) => m.AuthComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: 'admin',
        canActivate: [roleGuard(['SUPER_USER'])],
        children: [
          {
            path: 'users',
            loadComponent: () =>
              import('./admin/users/admin-users.component').then((m) => m.AdminUsersComponent),
          },
        ],
      },
      {
        path: 'dashboard',
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./dashboard/dashboard.component').then((m) => m.DashboardComponent),
          },
        ],
      },
      {
        path: 'invoices',
        canActivate: [roleGuard(['SUPER_ADMIN', 'COMPANY_ADMIN', 'ACCOUNTANT'])],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./invoices/invoice-list/invoice-list-page.component').then((m) => m.InvoiceListPageComponent),
          },
          {
            path: 'new',
            canDeactivate: [unsavedChangesGuard],
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
            canDeactivate: [unsavedChangesGuard],
            loadComponent: () =>
              import('./invoices/invoice-form/invoice-form-page.component').then((m) => m.InvoiceFormPageComponent),
          },
        ],
      },
      {
        path: 'customers',
        canActivate: [roleGuard(['SUPER_ADMIN', 'COMPANY_ADMIN', 'ACCOUNTANT'])],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./customers/customers.component').then((m) => m.CustomersComponent),
          },
        ],
      },
      {
        path: 'items',
        canActivate: [roleGuard(['SUPER_ADMIN', 'COMPANY_ADMIN', 'ACCOUNTANT'])],
        children: [
          {
            path: '',
            loadComponent: () => import('./items/items.component').then((m) => m.ItemsComponent),
          },
        ],
      },
      {
        path: 'config',
        canActivate: [roleGuard(['SUPER_ADMIN', 'COMPANY_ADMIN'])],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./config/config.component').then((m) => m.ConfigComponent),
          },
          {
            path: 'company-create',
            canActivate: [roleGuard(['SUPER_ADMIN'])],
            children: [
              {
                path: '',
                loadComponent: () =>
                  import('./config/company-create/company-create.component').then((m) => m.CompanyCreateComponent),
              },
              {
                path: ':companyId/branches',
                loadComponent: () =>
                  import('./config/branches/branches.component').then((m) => m.BranchesComponent),
              },
              {
                path: ':companyId/edit',
                loadComponent: () =>
                  import('./config/company-edit/company-edit.component').then((m) => m.CompanyEditComponent),
              },
              {
                path: ':companyId/branches/:branchId/authority-config',
                loadComponent: () =>
                  import('./config/authority-config/authority-config.component').then((m) => m.AuthorityConfigComponent),
              },
            ],
          },
          {
            path: 'companies/:companyId/branches',
            loadComponent: () =>
              import('./config/branches/branches.component').then((m) => m.BranchesComponent),
          },
          {
            path: 'companies/:companyId/branches/new',
            loadComponent: () =>
              import('./config/branches/branches.component').then((m) => m.BranchesComponent),
          },
          {
            path: 'companies/:companyId/branches/:branchId/edit',
            loadComponent: () =>
              import('./config/branches/branches.component').then((m) => m.BranchesComponent),
          },
          {
            path: 'company-profile',
            loadComponent: () =>
              import('./config/company-profile/company-profile.component').then((m) => m.CompanyProfileComponent),
          },
          {
            path: 'users',
            loadComponent: () =>
              import('./config/users/users.component').then((m) => m.UsersComponent),
          },
          {
            path: 'branches/:branchId/zatca-onboarding',
            loadComponent: () =>
              import('./config/zatca-onboarding/zatca-onboarding.component').then((m) => m.ZatcaOnboardingComponent),
          },
          {
            path: 'branches/:branchId/zatca-certificate',
            loadComponent: () =>
              import('./config/zatca-certificate/zatca-certificate.component').then((m) => m.ZatcaCertificateComponent),
          },
        ],
      },
      {
        path: 'eta-codes',
        canActivate: [roleGuard(['SUPER_ADMIN', 'COMPANY_ADMIN', 'ACCOUNTANT'])],
        loadComponent: () =>
          import('./config/eta-codes/eta-codes.component').then((m) => m.EtaCodesComponent),
      },
      {
        path: 'logs',
        canActivate: [roleGuard(['SUPER_ADMIN', 'COMPANY_ADMIN'])],
        children: [
          {
            path: '',
            loadComponent: () => import('./logs/logs.component').then((m) => m.LogsComponent),
          },
        ],
      },
      {
        path: 'jobs',
        children: [
          {
            path: '',
            loadComponent: () => import('./jobs/jobs.component').then((m) => m.JobsComponent),
          },
        ],
      },
    ],
  },
  {
    path: '**',
    redirectTo: 'dashboard',
  },
];
