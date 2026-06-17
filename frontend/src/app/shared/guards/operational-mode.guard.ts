import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { SessionContextService } from '../services/session-context.service';
import { ToastNotificationService } from '../services/toast.service';

export const operationalModeGuard: CanActivateFn = () => {
  const sessionCtx = inject(SessionContextService);
  const router = inject(Router);
  const toast = inject(ToastNotificationService);

  const ctx = sessionCtx.currentContext;
  if (ctx && (ctx.mode === 'OPERATIONAL_MODE' || ctx.mode === 'AUTHORITY_SCOPED')) {
    return true;
  }

  toast.warning('Select a company to use this section.');
  router.navigate(['/dashboard']);
  return false;
};
