import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const roleGuard = (allowedRoles: string[]): CanActivateFn => {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    const role = authService.getCurrentRole();
    if (role && allowedRoles.includes(role)) {
      return true;
    }

    router.navigate(['/dashboard']);
    return false;
  };
};
