import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { SessionContextService } from '../services/session-context.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const sessionCtx = inject(SessionContextService);
  const router = inject(Router);
  const token = authService.getToken();

  const skipAuth =
    req.url.includes('/api/auth/environments') ||
    req.url.includes('/api/auth/companies') ||
    req.url.includes('/api/auth/login');

  let authReq = req;
  if (token && !skipAuth) {
    authReq = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !skipAuth) {
        authService.clearAuth();
        sessionCtx.clear();
        router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
