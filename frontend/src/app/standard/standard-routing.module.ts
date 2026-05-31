import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

const routes: Routes = [
  { path: '', loadComponent: () => import('./zatca-standard-list.component').then(m => m.ZatcaStandardListComponent) },
  { path: 'new', loadComponent: () => import('./zatca-standard-form.component').then(m => m.ZatcaStandardFormComponent) },
  { path: ':id', loadComponent: () => import('./zatca-standard-detail.component').then(m => m.ZatcaStandardDetailComponent) },
  { path: ':id/edit', loadComponent: () => import('./zatca-standard-form.component').then(m => m.ZatcaStandardFormComponent) },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class StandardRoutingModule {}
