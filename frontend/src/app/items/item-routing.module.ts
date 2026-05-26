import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

const routes: Routes = [
  { path: 'eta', loadComponent: () => import('./eta-item-list/eta-item-list.component').then(m => m.EtaItemListComponent) },
  { path: 'eta/new', loadComponent: () => import('./eta-item-form/eta-item-form.component').then(m => m.EtaItemFormComponent) },
  { path: 'eta/:id', loadComponent: () => import('./eta-item-form/eta-item-form.component').then(m => m.EtaItemFormComponent) },
  { path: 'zatca', loadComponent: () => import('./zatca-item-list/zatca-item-list.component').then(m => m.ZatcaItemListComponent) },
  { path: 'zatca/new', loadComponent: () => import('./zatca-item-form/zatca-item-form.component').then(m => m.ZatcaItemFormComponent) },
  { path: 'zatca/:id', loadComponent: () => import('./zatca-item-form/zatca-item-form.component').then(m => m.ZatcaItemFormComponent) },
  { path: '', redirectTo: 'eta', pathMatch: 'full' },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ItemRoutingModule {}
