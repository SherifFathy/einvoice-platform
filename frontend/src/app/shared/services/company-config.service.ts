import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BranchResponse, BranchCreateRequest } from './admin.service';

type CreateBranchRequest = BranchCreateRequest;

export interface CompanyProfile {
  id: number;
  nameAr: string;
  nameEn: string;
  vatNumber: string;
  crNumber: string | null;
  isActive: boolean;
  createdAt: string;
}

export interface UpdateCompanyRequest {
  nameAr?: string;
  nameEn?: string;
  vatNumber?: string;
  crNumber?: string;
}

export interface UserCompany {
  id: number;
  name: string;
  email: string;
  role: string;
  isActive: boolean;
  permittedEnvironments: string[];
}

export interface PermissionRequest {
  environments: string[];
}

@Injectable({ providedIn: 'root' })
export class CompanyConfigService {
  private http = inject(HttpClient);

  getCompany(id: number): Observable<CompanyProfile> {
    return this.http.get<CompanyProfile>(`/api/companies/${id}`);
  }

  updateCompany(id: number, request: UpdateCompanyRequest): Observable<CompanyProfile> {
    return this.http.put<CompanyProfile>(`/api/companies/${id}`, request);
  }

  listBranches(companyId: number): Observable<BranchResponse[]> {
    return this.http.get<BranchResponse[]>(`/api/companies/${companyId}/branches`);
  }

  createBranch(companyId: number, request: CreateBranchRequest): Observable<BranchResponse> {
    return this.http.post<BranchResponse>(`/api/companies/${companyId}/branches`, request);
  }

  updateBranch(companyId: number, branchId: number, request: CreateBranchRequest): Observable<BranchResponse> {
    return this.http.put<BranchResponse>(`/api/companies/${companyId}/branches/${branchId}`, request);
  }

  deactivateBranch(companyId: number, branchId: number): Observable<void> {
    return this.http.delete<void>(`/api/companies/${companyId}/branches/${branchId}`);
  }

  listAuthorityConfigs(companyId: number, branchId: number): Observable<Record<string, unknown>[]> {
    return this.http.get<Record<string, unknown>[]>(`/api/companies/${companyId}/branches/${branchId}/authority-configs`);
  }

  upsertAuthorityConfig(companyId: number, branchId: number, request: Record<string, unknown>): Observable<Record<string, unknown>> {
    return this.http.put<Record<string, unknown>>(`/api/companies/${companyId}/branches/${branchId}/authority-configs`, request);
  }

  listUsers(companyId: number): Observable<UserCompany[]> {
    return this.http.get<UserCompany[]>(`/api/companies/${companyId}/users`);
  }

  assignPermissions(userId: number, request: PermissionRequest): Observable<UserCompany> {
    return this.http.post<UserCompany>(`/api/users/${userId}/permissions`, request);
  }

  activateUser(userId: number): Observable<UserCompany> {
    return this.http.put<UserCompany>(`/api/users/${userId}/activate`, {});
  }

  deactivateUser(userId: number): Observable<UserCompany> {
    return this.http.put<UserCompany>(`/api/users/${userId}/deactivate`, {});
  }

  listEnvironments(): Observable<string[]> {
    return this.http.get<string[]>('/api/environments');
  }
}
