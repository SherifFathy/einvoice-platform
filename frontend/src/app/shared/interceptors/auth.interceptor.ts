import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();

  const skipAuth =
    req.url.includes('/api/auth/login') ||
    req.url.includes('/api/auth/refresh') ||
    req.url.includes('/api/auth/logout');

  let authReq = req;
  if (token && !skipAuth) {
    const headers: Record<string, string> = {
      Authorization: `Bearer ${token}`,
    };

    const env = authService.getActiveEnvironment();
    if (env) {
      headers['X-Environment'] = env;
    }

    const lovContextId = authService.getLovContextId();
    if (lovContextId !== null) {
      headers['X-Lov-Context'] = String(lovContextId);
    }

    authReq = req.clone({ setHeaders: headers });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && token && !skipAuth) {
        const refreshToken = authService.getRefreshToken();
        if (refreshToken) {
          return authService.refresh(refreshToken).pipe(
            switchMap((response) => {
              const retryHeaders: Record<string, string> = {
                Authorization: `Bearer ${response.accessToken}`,
              };
              const retryEnv = authService.getActiveEnvironment();
              if (retryEnv) {
                retryHeaders['X-Environment'] = retryEnv;
              }
              const retryLovContextId = authService.getLovContextId();
              if (retryLovContextId !== null) {
                retryHeaders['X-Lov-Context'] = String(retryLovContextId);
              }
              const retryReq = req.clone({ setHeaders: retryHeaders });
              return next(retryReq);
            }),
            catchError(() => {
              authService.clearAuth();
              return throwError(() => error);
            }),
          );
        }
      }
      return throwError(() => error);
    }),
  );
};
