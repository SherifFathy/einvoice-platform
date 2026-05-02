import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from '../components/data-table/data-table.component';

export interface CompanyResponse {
  id: number;
  nameAr: string;
  nameEn: string;
  vatNumber: string;
  crNumber: string | null;
  isActive: boolean;
  createdAt: string;
}

export interface CreateCompanyRequest {
  nameAr: string;
  nameEn: string;
  vatNumber: string;
  crNumber?: string;
}

export interface BranchResponse {
  id: number;
  companyId: number;
  nameAr: string;
  nameEn: string;
  branchCode: string;
  street: string | null;
  buildingNumber: string | null;
  additionalNumber: string | null;
  city: string | null;
  district: string | null;
  postalCode: string | null;
  countryCode: string | null;
  additionalStreet: string | null;
  isActive: boolean;
  createdAt: string;
}

export interface CreateBranchRequest {
  nameAr: string;
  nameEn: string;
  branchCode: string;
  street?: string;
  buildingNumber?: string;
  additionalNumber?: string;
  city?: string;
  district?: string;
  postalCode?: string;
  countryCode?: string;
  additionalStreet?: string;
}

export interface AuthorityConfigResponse {
  id: number;
  branchId: number;
  authority: string;
  environment: string;
  hasCredentials: boolean;
  hasCertificate: boolean;
  hasCsid: boolean;
  hasPrivateKey: boolean;
  hasTokenData: boolean;
  certificateExpiryDate: string | null;
  invoiceCounter: number;
  invoicePrefix: string | null;
  invoiceStartingNumber: number;
  invoiceResetPolicy: string;
  enabledDocumentTypes: string;
  isActive: boolean;
}

export interface CreateAuthorityConfigRequest {
  authority: string;
  environment: string;
  invoicePrefix?: string;
  invoiceStartingNumber?: number;
  invoiceResetPolicy?: string;
}

export interface UpdateAuthorityConfigRequest {
  invoicePrefix?: string;
  invoiceStartingNumber?: number;
  invoiceResetPolicy?: string;
}

export interface AdminUserResponse {
  id: number;
  name: string;
  email: string;
  isActive: boolean;
  isSuperUser: boolean;
  createdAt: string;
  companies: AdminUserCompanyAssignment[];
}

export interface AdminUserCompanyAssignment {
  companyId: number;
  companyName: string;
  role: string;
  isActive: boolean;
}

export interface CreateUserRequest {
  name: string;
  email: string;
  password: string;
}

export interface UpdateUserRequest {
  name?: string;
  email?: string;
}

export interface AssignUserCompanyRequest {
  companyId: number;
  role: string;
}

export interface BulkPermissionRequest {
  companyId: number;
  lovContextId: number;
  permissions: string[];
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);
  private readonly apiUrl = '/api/admin';

  listCompanies(page: number, size: number): Observable<PageResponse<CompanyResponse>> {
    return this.http.get<PageResponse<CompanyResponse>>(
        `${this.apiUrl}/companies?page=${page}&size=${size}`);
  }

  createCompany(request: CreateCompanyRequest): Observable<CompanyResponse> {
    return this.http.post<CompanyResponse>(`${this.apiUrl}/companies`, request);
  }

  activateCompany(id: number): Observable<CompanyResponse> {
    return this.http.post<CompanyResponse>(`${this.apiUrl}/companies/${id}/activate`, {});
  }

  deactivateCompany(id: number): Observable<CompanyResponse> {
    return this.http.post<CompanyResponse>(`${this.apiUrl}/companies/${id}/deactivate`, {});
  }

  updateCompany(id: number, request: Record<string, string>): Observable<CompanyResponse> {
    return this.http.put<CompanyResponse>(`${this.apiUrl}/companies/${id}`, request);
  }

  listBranches(companyId: number): Observable<BranchResponse[]> {
    return this.http.get<BranchResponse[]>(
        `${this.apiUrl}/companies/${companyId}/branches`);
  }

  createBranch(companyId: number, request: CreateBranchRequest): Observable<BranchResponse> {
    return this.http.post<BranchResponse>(
        `${this.apiUrl}/companies/${companyId}/branches`, request);
  }

  updateBranch(companyId: number, branchId: number, request: CreateBranchRequest): Observable<BranchResponse> {
    return this.http.put<BranchResponse>(
        `${this.apiUrl}/companies/${companyId}/branches/${branchId}`, request);
  }

  deleteBranch(companyId: number, branchId: number): Observable<void> {
    return this.http.delete<void>(
        `${this.apiUrl}/companies/${companyId}/branches/${branchId}`);
  }

  listAuthorityConfigs(companyId: number, branchId: number): Observable<AuthorityConfigResponse[]> {
    return this.http.get<AuthorityConfigResponse[]>(
        `${this.apiUrl}/companies/${companyId}/branches/${branchId}/authority-configs`);
  }

  createAuthorityConfig(companyId: number, branchId: number,
      request: CreateAuthorityConfigRequest): Observable<AuthorityConfigResponse> {
    return this.http.post<AuthorityConfigResponse>(
        `${this.apiUrl}/companies/${companyId}/branches/${branchId}/authority-configs`, request);
  }

  updateAuthorityConfigSettings(companyId: number, branchId: number, configId: number,
      request: UpdateAuthorityConfigRequest): Observable<AuthorityConfigResponse> {
    return this.http.post<AuthorityConfigResponse>(
        `${this.apiUrl}/companies/${companyId}/branches/${branchId}/authority-configs/${configId}/settings`,
        request);
  }

  uploadCredentials(companyId: number, branchId: number, configId: number,
      data: ArrayBuffer): Observable<AuthorityConfigResponse> {
    return this.http.post<AuthorityConfigResponse>(
        `${this.apiUrl}/companies/${companyId}/branches/${branchId}/authority-configs/${configId}/credentials`,
        data, { headers: { 'Content-Type': 'application/octet-stream' } });
  }

  uploadCertificate(companyId: number, branchId: number, configId: number,
      data: ArrayBuffer): Observable<AuthorityConfigResponse> {
    return this.http.post<AuthorityConfigResponse>(
        `${this.apiUrl}/companies/${companyId}/branches/${branchId}/authority-configs/${configId}/certificate`,
        data, { headers: { 'Content-Type': 'application/octet-stream' } });
  }

  listUsers(): Observable<AdminUserResponse[]> {
    return this.http.get<AdminUserResponse[]>(`${this.apiUrl}/users`);
  }

  createUser(request: CreateUserRequest): Observable<AdminUserResponse> {
    return this.http.post<AdminUserResponse>(`${this.apiUrl}/users`, request);
  }

  updateUser(id: number, request: UpdateUserRequest): Observable<AdminUserResponse> {
    return this.http.put<AdminUserResponse>(`${this.apiUrl}/users/${id}`, request);
  }

  resetPassword(id: number, newPassword: string): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/users/${id}/password`, { newPassword });
  }

  activateUser(id: number): Observable<AdminUserResponse> {
    return this.http.put<AdminUserResponse>(`${this.apiUrl}/users/${id}/activate`, {});
  }

  deactivateUser(id: number): Observable<AdminUserResponse> {
    return this.http.put<AdminUserResponse>(`${this.apiUrl}/users/${id}/deactivate`, {});
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/users/${id}`);
  }

  assignUserToCompany(userId: number, request: AssignUserCompanyRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/users/${userId}/companies`, request);
  }

  removeUserFromCompany(userId: number, companyId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/users/${userId}/companies/${companyId}`);
  }

  bulkSetPermissions(userId: number, request: BulkPermissionRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/users/${userId}/permissions`, request);
  }

  getUserPermissions(userId: number, companyId: number, lovContextId: number): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/users/${userId}/permissions`,
        { params: { companyId: companyId.toString(), lovContextId: lovContextId.toString() } });
  }
}
