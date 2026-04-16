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
  street: string | null;
  buildingNumber: string | null;
  city: string | null;
  district: string | null;
  postalCode: string | null;
  countryCode: string;
  additionalId: string | null;
  isActive: boolean;
  createdAt: string;
}

export interface CreateCompanyRequest {
  nameAr: string;
  nameEn: string;
  vatNumber: string;
  crNumber?: string;
  street?: string;
  buildingNumber?: string;
  city?: string;
  district?: string;
  postalCode?: string;
  countryCode?: string;
  additionalId?: string;
}

export interface BranchResponse {
  id: number;
  companyId: number;
  nameAr: string;
  nameEn: string;
  branchCode: string;
  isActive: boolean;
  createdAt: string;
}

export interface CreateBranchRequest {
  nameAr: string;
  nameEn: string;
  branchCode: string;
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

export interface AssignUserRequest {
  email: string;
  role: string;
  name?: string;
}

export interface AssignUserResponse {
  userId: number;
  companyId: number;
  role: string;
  userCreated: boolean;
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

  assignUser(companyId: number, request: AssignUserRequest): Observable<AssignUserResponse> {
    return this.http.post<AssignUserResponse>(
        `${this.apiUrl}/companies/${companyId}/assign-user`, request);
  }

  removeUserFromCompany(companyId: number, userId: number): Observable<void> {
    return this.http.delete<void>(
        `${this.apiUrl}/companies/${companyId}/users/${userId}`);
  }
}
