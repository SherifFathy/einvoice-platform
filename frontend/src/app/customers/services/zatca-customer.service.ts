import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ZatcaAddress {
  streetName: string;
  buildingNumber: string;
  city: string;
  postalCode: string;
  districtName: string;
  country: string;
}

export interface ZatcaCustomerResponse {
  id: string;
  companyId: string;
  nameEn: string;
  nameAr: string | null;
  vatNumber: string | null;
  customerType: string;
  isActive: boolean;
  addressData: Record<string, unknown> | null;
  contactEmail: string | null;
  contactPhone: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ZatcaCustomerWriteRequest {
  nameEn: string;
  nameAr?: string;
  vatNumber?: string;
  idType?: string;
  idValue?: string;
  customerType: string;
  isActive?: boolean;
  addressData?: Record<string, unknown>;
  contactEmail?: string;
  contactPhone?: string;
}

export interface ZatcaCustomerPage {
  items: ZatcaCustomerResponse[];
  page: {
    page: number;
    size: number;
    total: number;
  };
}

@Injectable({ providedIn: 'root' })
export class ZatcaCustomerService {
  private http = inject(HttpClient);

  list(companyId: string, page: number, size: number, q?: string, includeInactive?: boolean, filterCompanyId?: string): Observable<ZatcaCustomerPage> {
    let url = `/api/companies/${companyId}/zatca/customers?page=${page}&size=${size}`;
    if (q) url += `&q=${encodeURIComponent(q)}`;
    if (includeInactive) url += `&includeInactive=true`;
    if (filterCompanyId) url += `&companyId=${encodeURIComponent(filterCompanyId)}`;
    return this.http.get<ZatcaCustomerPage>(url);
  }

  get(companyId: string, id: string): Observable<ZatcaCustomerResponse> {
    return this.http.get<ZatcaCustomerResponse>(`/api/companies/${companyId}/zatca/customers/${id}`);
  }

  create(companyId: string, payload: ZatcaCustomerWriteRequest): Observable<ZatcaCustomerResponse> {
    return this.http.post<ZatcaCustomerResponse>(`/api/companies/${companyId}/zatca/customers`, payload);
  }

  update(companyId: string, id: string, payload: ZatcaCustomerWriteRequest): Observable<ZatcaCustomerResponse> {
    return this.http.put<ZatcaCustomerResponse>(`/api/companies/${companyId}/zatca/customers/${id}`, payload);
  }

  delete(companyId: string, id: string): Observable<void> {
    return this.http.delete<void>(`/api/companies/${companyId}/zatca/customers/${id}`);
  }
}
