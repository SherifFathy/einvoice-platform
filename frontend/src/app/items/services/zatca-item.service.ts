import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ZatcaItemResponse {
  id: string;
  companyId: string;
  internalCode: string;
  itemCode: string | null;
  nameAr: string | null;
  nameEn: string;
  unitType: string | null;
  unitPrice: number | null;
  vatCategory: string;
  vatRate: number | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ZatcaItemWriteRequest {
  internalCode: string;
  itemCode?: string;
  nameAr?: string;
  nameEn: string;
  unitType?: string;
  unitPrice?: number;
  vatCategory: string;
  vatRate?: number;
  isActive?: boolean;
}

export interface ZatcaItemPage {
  items: ZatcaItemResponse[];
  page: {
    page: number;
    size: number;
    total: number;
  };
}

@Injectable({ providedIn: 'root' })
export class ZatcaItemService {
  private http = inject(HttpClient);

  list(companyId: string, page: number, size: number, q?: string, includeInactive?: boolean, filterCompanyId?: string): Observable<ZatcaItemPage> {
    let url = `/api/companies/${companyId}/zatca/items?page=${page}&size=${size}`;
    if (q) url += `&q=${encodeURIComponent(q)}`;
    if (includeInactive) url += `&includeInactive=true`;
    if (filterCompanyId) url += `&companyId=${encodeURIComponent(filterCompanyId)}`;
    return this.http.get<ZatcaItemPage>(url);
  }

  get(companyId: string, id: string): Observable<ZatcaItemResponse> {
    return this.http.get<ZatcaItemResponse>(`/api/companies/${companyId}/zatca/items/${id}`);
  }

  create(companyId: string, payload: ZatcaItemWriteRequest): Observable<ZatcaItemResponse> {
    return this.http.post<ZatcaItemResponse>(`/api/companies/${companyId}/zatca/items`, payload);
  }

  update(companyId: string, id: string, payload: ZatcaItemWriteRequest): Observable<ZatcaItemResponse> {
    return this.http.put<ZatcaItemResponse>(`/api/companies/${companyId}/zatca/items/${id}`, payload);
  }

  delete(companyId: string, id: string): Observable<void> {
    return this.http.delete<void>(`/api/companies/${companyId}/zatca/items/${id}`);
  }
}
