import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./config.component').then((m) => m.ConfigComponent),
  },
  {
    path: 'eta',
    loadComponent: () =>
      import('./eta-config/eta-config.component').then((m) => m.EtaConfigComponent),
  },
  {
    path: 'zatca',
    loadComponent: () =>
      import('./zatca-config/zatca-config.component').then((m) => m.ZatcaConfigComponent),
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ConfigRoutingModule {}
