import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { SessionContextService } from '../services/session-context.service';

export const adminGuard: CanActivateFn = () => {
  const sessionCtx = inject(SessionContextService);
  const router = inject(Router);

  const ctx = sessionCtx.currentContext;
  if (ctx?.isSuperUser) {
    return true;
  }

  router.navigate(['/dashboard']);
  return false;
};
