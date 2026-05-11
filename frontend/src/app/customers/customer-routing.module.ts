import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

const routes: Routes = [
  { path: 'eta', loadComponent: () => import('./eta-customer-list/eta-customer-list.component').then(m => m.EtaCustomerListComponent) },
  { path: 'eta/new', loadComponent: () => import('./eta-customer-form/eta-customer-form.component').then(m => m.EtaCustomerFormComponent) },
  { path: 'eta/:id', loadComponent: () => import('./eta-customer-form/eta-customer-form.component').then(m => m.EtaCustomerFormComponent) },
  { path: 'zatca', loadComponent: () => import('./zatca-customer-list/zatca-customer-list.component').then(m => m.ZatcaCustomerListComponent) },
  { path: 'zatca/new', loadComponent: () => import('./zatca-customer-form/zatca-customer-form.component').then(m => m.ZatcaCustomerFormComponent) },
  { path: 'zatca/:id', loadComponent: () => import('./zatca-customer-form/zatca-customer-form.component').then(m => m.ZatcaCustomerFormComponent) },
  { path: '', redirectTo: 'eta', pathMatch: 'full' },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class CustomerRoutingModule {}
