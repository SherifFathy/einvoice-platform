import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface EtaItemResponse {
  id: string;
  companyId: string;
  internalCode: string;
  itemType: string;
  itemCode: string;
  nameAr: string | null;
  nameEn: string;
  unitType: string | null;
  unitPrice: number | null;
  taxType: string | null;
  taxSubtype: string | null;
  taxRate: number | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface EtaItemWriteRequest {
  internalCode: string;
  itemType: string;
  itemCode: string;
  nameAr?: string;
  nameEn: string;
  unitType?: string;
  unitPrice?: number;
  taxType?: string;
  taxSubtype?: string;
  taxRate?: number;
  isActive?: boolean;
}

export interface EtaItemPage {
  items: EtaItemResponse[];
  page: {
    page: number;
    size: number;
    total: number;
  };
}

@Injectable({ providedIn: 'root' })
export class EtaItemService {
  private http = inject(HttpClient);

  list(companyId: string, page: number, size: number, q?: string, includeInactive?: boolean, filterCompanyId?: string): Observable<EtaItemPage> {
    let url = `/api/companies/${companyId}/eta/items?page=${page}&size=${size}`;
    if (q) url += `&q=${encodeURIComponent(q)}`;
    if (includeInactive) url += `&includeInactive=true`;
    if (filterCompanyId) url += `&companyId=${encodeURIComponent(filterCompanyId)}`;
    return this.http.get<EtaItemPage>(url);
  }

  get(companyId: string, id: string): Observable<EtaItemResponse> {
    return this.http.get<EtaItemResponse>(`/api/companies/${companyId}/eta/items/${id}`);
  }

  create(companyId: string, payload: EtaItemWriteRequest): Observable<EtaItemResponse> {
    return this.http.post<EtaItemResponse>(`/api/companies/${companyId}/eta/items`, payload);
  }

  update(companyId: string, id: string, payload: EtaItemWriteRequest): Observable<EtaItemResponse> {
    return this.http.put<EtaItemResponse>(`/api/companies/${companyId}/eta/items/${id}`, payload);
  }

  delete(companyId: string, id: string): Observable<void> {
    return this.http.delete<void>(`/api/companies/${companyId}/eta/items/${id}`);
  }
}
