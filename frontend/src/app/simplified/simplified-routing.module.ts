import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

const routes: Routes = [
  { path: '', loadComponent: () => import('./zatca-simplified-list.component').then(m => m.ZatcaSimplifiedListComponent) },
  { path: 'new', loadComponent: () => import('./zatca-simplified-form.component').then(m => m.ZatcaSimplifiedFormComponent) },
  { path: ':id', loadComponent: () => import('./zatca-simplified-detail.component').then(m => m.ZatcaSimplifiedDetailComponent) },
  { path: ':id/edit', loadComponent: () => import('./zatca-simplified-form.component').then(m => m.ZatcaSimplifiedFormComponent) },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class SimplifiedRoutingModule {}
