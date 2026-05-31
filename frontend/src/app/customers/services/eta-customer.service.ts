import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface EtaAddress {
  country: string;
  governorate: string;
  regionCity: string;
  street: string;
  buildingNumber: string;
}

export interface EtaCustomerResponse {
  id: string;
  companyId: string;
  nameEn: string;
  nameAr: string | null;
  taxNumber: string | null;
  customerType: string;
  isActive: boolean;
  addressData: Record<string, unknown> | null;
  contactEmail: string | null;
  contactPhone: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EtaCustomerWriteRequest {
  nameEn: string;
  nameAr?: string;
  taxNumber?: string;
  idType?: string;
  idValue?: string;
  customerType: string;
  isActive?: boolean;
  addressData?: Record<string, unknown>;
  contactEmail?: string;
  contactPhone?: string;
}

export interface EtaCustomerPage {
  items: EtaCustomerResponse[];
  page: {
    page: number;
    size: number;
    total: number;
  };
}

@Injectable({ providedIn: 'root' })
export class EtaCustomerService {
  private http = inject(HttpClient);

  list(companyId: string, page: number, size: number, q?: string, includeInactive?: boolean, filterCompanyId?: string): Observable<EtaCustomerPage> {
    let url = `/api/companies/${companyId}/eta/customers?page=${page}&size=${size}`;
    if (q) url += `&q=${encodeURIComponent(q)}`;
    if (includeInactive) url += `&includeInactive=true`;
    if (filterCompanyId) url += `&companyId=${encodeURIComponent(filterCompanyId)}`;
    return this.http.get<EtaCustomerPage>(url);
  }

  get(companyId: string, id: string): Observable<EtaCustomerResponse> {
    return this.http.get<EtaCustomerResponse>(`/api/companies/${companyId}/eta/customers/${id}`);
  }

  create(companyId: string, payload: EtaCustomerWriteRequest): Observable<EtaCustomerResponse> {
    return this.http.post<EtaCustomerResponse>(`/api/companies/${companyId}/eta/customers`, payload);
  }

  update(companyId: string, id: string, payload: EtaCustomerWriteRequest): Observable<EtaCustomerResponse> {
    return this.http.put<EtaCustomerResponse>(`/api/companies/${companyId}/eta/customers/${id}`, payload);
  }

  delete(companyId: string, id: string): Observable<void> {
    return this.http.delete<void>(`/api/companies/${companyId}/eta/customers/${id}`);
  }
}
