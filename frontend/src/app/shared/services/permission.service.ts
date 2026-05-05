import { Injectable, inject } from '@angular/core';
import { SessionContextService } from './session-context.service';

@Injectable({ providedIn: 'root' })
export class PermissionService {
  private sessionCtx = inject(SessionContextService);

  hasPermission(companyId: string, moduleKey: string, action: string): boolean {
    const ctx = this.sessionCtx.currentContext;
    if (!ctx) return false;

    if (ctx.isSuperUser && ctx.mode === 'OPERATIONAL_MODE') {
      return true;
    }

    const company = ctx.companies.find((c) => c.companyId === companyId);
    if (!company) return false;

    const mod = company.modules[moduleKey];
    if (!mod || !mod.visible) return false;

    const key = action.toLowerCase() as keyof typeof mod.permissions;
    return mod.permissions[key] ?? false;
  }
}
