import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { toSignal } from '@angular/core/rxjs-interop';
import { SessionContextService } from '../../shared/services/session-context.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

interface SidebarItem {
  label: string;
  icon: string;
  route: string;
  moduleKey?: string;
  permission?: [string, string];
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule, MatListModule, MatIconModule, HasPermissionDirective],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent {
  private sessionCtx = inject(SessionContextService);
  private context = toSignal(this.sessionCtx.context$, { initialValue: null });

  private readonly etaItems: SidebarItem[] = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
    { label: 'Invoices', icon: 'receipt', route: '/invoices', permission: ['INVOICE', 'VIEW'] },
    { label: 'Receipts', icon: 'receipt_long', route: '/receipts/eta', permission: ['RECEIPT', 'VIEW'] },
    { label: 'Customers', icon: 'people', route: '/customers', permission: ['customers', 'VIEW'] },
    { label: 'Items', icon: 'inventory', route: '/items', permission: ['items', 'VIEW'] },
    { label: 'Configuration', icon: 'settings', route: '/config', permission: ['configuration', 'VIEW'] },
    { label: 'Logs', icon: 'history', route: '/logs' },
  ];

  private readonly zatcaItems: SidebarItem[] = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
    { label: 'Standard', icon: 'article', route: '/standard', moduleKey: 'standard' },
    { label: 'Simplified', icon: 'note', route: '/simplified', moduleKey: 'simplified' },
    { label: 'Customers', icon: 'people', route: '/customers', permission: ['customers', 'VIEW'] },
    { label: 'Items', icon: 'inventory', route: '/items', permission: ['items', 'VIEW'] },
    { label: 'Configuration', icon: 'settings', route: '/config', permission: ['configuration', 'VIEW'] },
    { label: 'Logs', icon: 'history', route: '/logs' },
  ];

  private readonly adminItems: SidebarItem[] = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
    { label: 'Companies', icon: 'business', route: '/admin/companies' },
    { label: 'Branches', icon: 'store', route: '/admin/branches' },
    { label: 'Users', icon: 'people', route: '/admin/users' },
    { label: 'Assignments', icon: 'assignment_ind', route: '/admin/assignments' },
    { label: 'Logs', icon: 'history', route: '/logs' },
  ];

  items = computed(() => {
    const ctx = this.context();
    if (!ctx) return [];

    if (ctx.mode === 'ADMIN_MODE') {
      return this.adminItems;
    }

    const authority = ctx.loginContext?.authority;
    const baseItems = authority === 'ETA' ? this.etaItems : this.zatcaItems;

    const visibleItems = baseItems.filter((item) => {
      if (!item.moduleKey && !item.permission) return true;
      if (item.permission) return true;
      return ctx.companies.some((c) => c.modules[item.moduleKey!]?.visible);
    });

    if (ctx.isSuperUser) {
      return [...visibleItems, { label: 'Admin', icon: 'admin_panel_settings', route: '/admin' }];
    }

    return visibleItems;
  });
}
