import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { SessionContextService } from './session-context.service';

export interface EnvironmentEntry {
  id: number;
  authority: string;
  environment: string;
  label: string;
  isActive: boolean;
}

export interface EnvironmentsResponse {
  environments: EnvironmentEntry[];
}

export interface CompanyEntry {
  companyId: string;
  nameEn: string;
  nameAr: string;
  taxNumber: string;
  isActive: boolean;
}

export interface CompaniesResponse {
  isSuperUser: boolean;
  companies: CompanyEntry[];
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  mode: string;
}

export interface SessionContext {
  userId: string;
  isSuperUser: boolean;
  mode: string;
  activeCompanyId: string | null;
  loginContext: {
    authority: string;
    environment: string;
    authorityEnvironmentId: number;
  };
  companies: {
    companyId: string;
    companyNameEn: string;
    companyNameAr: string;
    isActive: boolean;
    modules: Record<string, {
      visible: boolean;
      permissions: {
        view: boolean;
        create: boolean;
        edit: boolean;
        delete: boolean;
        cancel: boolean;
        transfer: boolean;
        refresh: boolean;
        submit: boolean;
      };
    }>;
  }[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private sessionCtx = inject(SessionContextService);
  private readonly apiUrl = '/api/auth';
  private readonly tokenKey = 'access_token';

  isLoggedIn = signal(false);

  constructor() {
    this.isLoggedIn.set(!!this.getToken());
  }

  listEnvironments(authority: string): Observable<EnvironmentsResponse> {
    return this.http.post<EnvironmentsResponse>(`${this.apiUrl}/environments`, { authority });
  }

  listCompanies(authority: string, environment: string, email: string): Observable<CompaniesResponse> {
    return this.http.post<CompaniesResponse>(`${this.apiUrl}/companies`, { authority, environment, email });
  }

  login(email: string, password: string, authority: string, environment: string,
         companyId: string | null): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, {
      email, password, authority, environment, companyId: companyId ?? undefined,
    }).pipe(
      tap((res) => {
        localStorage.setItem(this.tokenKey, res.accessToken);
        this.isLoggedIn.set(true);
      }),
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/logout`, {}).pipe(
      tap(() => this.clearAuth()),
    );
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  clearAuth(): void {
    localStorage.removeItem(this.tokenKey);
    this.isLoggedIn.set(false);
    this.sessionCtx.clear();
  }
}
