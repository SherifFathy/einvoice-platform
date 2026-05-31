import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CompanyResponse {
  id: string;
  nameEn: string;
  nameAr: string;
  taxNumber: string;
  crNumber: string | null;
  logoPath: string | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CompanyCreateRequest {
  nameEn: string;
  nameAr: string;
  taxNumber: string;
  crNumber?: string;
}

export interface CompanyUpdateRequest {
  nameEn: string;
  nameAr: string;
  taxNumber: string;
  crNumber?: string;
}

export interface BranchResponse {
  id: string;
  companyId: string;
  nameEn: string;
  nameAr: string;
  branchCode: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  region: string | null;
  postalCode: string | null;
  country: string;
  buildingNumber: string | null;
  additionalNo: string | null;
  taxpayerActivityCode: string | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface BranchCreateRequest {
  nameEn: string;
  nameAr: string;
  branchCode?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  region?: string;
  postalCode?: string;
  country?: string;
  buildingNumber?: string;
  additionalNo?: string;
  taxpayerActivityCode?: string;
}

export interface BranchUpdateRequest {
  nameEn: string;
  nameAr: string;
  branchCode?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  region?: string;
  postalCode?: string;
  country?: string;
  buildingNumber?: string;
  additionalNo?: string;
  taxpayerActivityCode?: string;
}

export interface UserResponse {
  id: string;
  name: string;
  email: string;
  isSuperUser: boolean;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UserCreateRequest {
  name: string;
  email: string;
  password: string;
  isSuperUser?: boolean;
}

export interface UserUpdateRequest {
  name?: string;
  email?: string;
  password?: string;
  isSuperUser?: boolean;
}

export interface AssignmentResponse {
  id: string;
  userId: string;
  companyId: string;
  authorityEnvironmentId: number;
  transactionType: string;
  roleCode: string;
  isActive: boolean;
  grantedBy: string | null;
  grantedAt: string;
}

export interface AssignmentCreateRequest {
  companyId: string;
  authorityEnvironmentId: number;
  transactionType: string;
  roleCode: string;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);
  private readonly apiUrl = '/api/admin';

  listCompanies(includeInactive = false): Observable<CompanyResponse[]> {
    return this.http.get<CompanyResponse[]>(
        `${this.apiUrl}/companies?includeInactive=${includeInactive}`);
  }

  createCompany(request: CompanyCreateRequest): Observable<CompanyResponse> {
    return this.http.post<CompanyResponse>(`${this.apiUrl}/companies`, request);
  }

  updateCompany(id: string, request: CompanyUpdateRequest): Observable<CompanyResponse> {
    return this.http.put<CompanyResponse>(`${this.apiUrl}/companies/${id}`, request);
  }

  deactivateCompany(id: string): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/companies/${id}/deactivate`, {});
  }

  listBranches(companyId: string): Observable<BranchResponse[]> {
    return this.http.get<BranchResponse[]>(
        `${this.apiUrl}/companies/${companyId}/branches`);
  }

  createBranch(companyId: string, request: BranchCreateRequest): Observable<BranchResponse> {
    return this.http.post<BranchResponse>(
        `${this.apiUrl}/companies/${companyId}/branches`, request);
  }

  updateBranch(id: string, request: BranchUpdateRequest): Observable<BranchResponse> {
    return this.http.put<BranchResponse>(`${this.apiUrl}/branches/${id}`, request);
  }

  listUsers(includeInactive = false): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(
        `${this.apiUrl}/users?includeInactive=${includeInactive}`);
  }

  createUser(request: UserCreateRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${this.apiUrl}/users`, request);
  }

  updateUser(id: string, request: UserUpdateRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>(`${this.apiUrl}/users/${id}`, request);
  }

  activateUser(id: string): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/users/${id}/activate`, {});
  }

  deactivateUser(id: string): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/users/${id}/deactivate`, {});
  }

  listAssignments(userId: string): Observable<AssignmentResponse[]> {
    return this.http.get<AssignmentResponse[]>(
        `${this.apiUrl}/users/${userId}/assignments`);
  }

  createAssignment(userId: string, request: AssignmentCreateRequest): Observable<AssignmentResponse> {
    return this.http.post<AssignmentResponse>(
        `${this.apiUrl}/users/${userId}/assignments`, request);
  }

  deleteAssignment(userId: string, assignmentId: string): Observable<void> {
    return this.http.delete<void>(
        `${this.apiUrl}/users/${userId}/assignments/${assignmentId}`);
  }
}
