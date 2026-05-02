import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';

export interface UserInfo {
  id: number;
  name: string;
  email: string;
  activeCompanyId: number | null;
  role: string | null;
  permittedEnvironments: string[];
  availableCompanies: { id: number; name: string }[];
  activeEnvironment: string | null;
  activeAuthority: string | null;
  activeDocType: string | null;
  activeSubEnv: string | null;
  lovContextId: number | null;
  permissions: string[];
  isSuperUser: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserInfo;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private readonly apiUrl = '/api/auth';
  private readonly accessTokenKey = 'access_token';
  private readonly refreshTokenKey = 'refresh_token';
  private readonly environmentKey = 'active_environment';

  private currentUserSubject = new BehaviorSubject<UserInfo | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();
  isLoggedIn = signal(false);

  constructor() {
    this.loadStoredAuth();
  }

  login(email: string, password: string,
        authority: string, docType: string, subEnvironment: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/login`, {
        email, password, authority, docType, subEnvironment,
      })
      .pipe(tap((res) => this.handleAuthResponse(res)));
  }

  logout(refreshToken?: string): Observable<void> {
    const token = refreshToken || this.getRefreshToken();
    return this.http.post<void>(`${this.apiUrl}/logout`, {
      refreshToken: token,
    }).pipe(tap(() => this.clearAuth()));
  }

  refresh(refreshToken: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/refresh`, { refreshToken })
      .pipe(tap((res) => this.handleAuthResponse(res)));
  }

  switchCompany(companyId: number, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/switch-company`, { companyId, password })
      .pipe(tap((res) => this.handleAuthResponse(res)));
  }

  selectEnvironment(environment: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(
      `${this.apiUrl}/select-environment`,
      { environment },
    ).pipe(tap((res) => {
      this.handleAuthResponse(res);
      localStorage.setItem(this.environmentKey, environment);
    }));
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.accessTokenKey);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.refreshTokenKey);
  }

  getActiveEnvironment(): string | null {
    return this.currentUserSubject.value?.activeEnvironment
      ?? localStorage.getItem(this.environmentKey);
  }

  getLovContextId(): number | null {
    return this.currentUserSubject.value?.lovContextId ?? null;
  }

  getCurrentUser(): UserInfo | null {
    return this.currentUserSubject.value;
  }

  getCurrentRole(): string | null {
    return this.currentUserSubject.value?.role ?? null;
  }

  getActiveCompanyId(): number | null {
    return this.currentUserSubject.value?.activeCompanyId ?? null;
  }

  clearActiveEnvironment(): void {
    localStorage.removeItem(this.environmentKey);
  }

  clearAuth(): void {
    localStorage.removeItem(this.accessTokenKey);
    localStorage.removeItem(this.refreshTokenKey);
    localStorage.removeItem(this.environmentKey);
    this.currentUserSubject.next(null);
    this.isLoggedIn.set(false);
  }

  hasPermission(key: string): boolean {
    const user = this.currentUserSubject.value;
    if (!user) return false;
    if (user.isSuperUser) return true;
    return user.permissions.includes(key);
  }

  private handleAuthResponse(response: AuthResponse): void {
    const previousCompanyId = this.currentUserSubject.value?.activeCompanyId ?? null;
    localStorage.setItem(this.accessTokenKey, response.accessToken);
    localStorage.setItem(this.refreshTokenKey, response.refreshToken);
    if (previousCompanyId !== null && previousCompanyId !== response.user.activeCompanyId) {
      this.clearActiveEnvironment();
    }
    this.currentUserSubject.next(response.user);
    this.isLoggedIn.set(true);
  }

  private loadStoredAuth(): void {
    const token = this.getAccessToken();
    const refreshToken = this.getRefreshToken();
    if (!token) {
      return;
    }
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (payload.exp && payload.exp * 1000 < Date.now()) {
        if (refreshToken) {
          this.refresh(refreshToken).subscribe({
            error: () => this.clearAuth(),
          });
        } else {
          this.clearAuth();
        }
        return;
      }
      this.setUserFromPayload(payload);
    } catch {
      this.clearAuth();
    }
  }

  private setUserFromPayload(payload: Record<string, unknown>): void {
    this.currentUserSubject.next({
      id: Number(payload['sub']),
      name: (payload['name'] as string) ?? (payload['email'] as string) ?? '',
      email: (payload['email'] as string) ?? '',
      activeCompanyId: (payload['activeCompanyId'] as number) ?? null,
      role: (payload['role'] as string) ?? null,
      permittedEnvironments: (payload['permittedEnvironments'] as string[]) ?? [],
      availableCompanies: ((payload['availableCompanies'] as Record<string, unknown>[]) ?? []).map(
        (c) => ({
          id: Number(c['id']),
          name: String(c['name'] ?? ''),
        }),
      ),
      activeEnvironment: (payload['activeEnvironment'] as string) ?? null,
      activeAuthority: (payload['active_authority'] as string) ?? null,
      activeDocType: (payload['active_doc_type'] as string) ?? null,
      activeSubEnv: (payload['active_sub_env'] as string) ?? null,
      lovContextId: (payload['lov_context_id'] as number) ?? null,
      permissions: (payload['permissions'] as string[]) ?? [],
      isSuperUser: (payload['is_super_user'] as boolean) ?? false,
    });
    this.isLoggedIn.set(true);
  }
}
