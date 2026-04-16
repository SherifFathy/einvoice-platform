import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from '../components/data-table/data-table.component';

export interface CustomerResponse {
  id: number;
  nameAr: string | null;
  nameEn: string;
  vatNumber: string | null;
  idType: string | null;
  idValue: string | null;
  street: string | null;
  buildingNumber: string | null;
  city: string | null;
  district: string | null;
  postalCode: string | null;
  countryCode: string;
  customerType: string;
  contactEmail: string | null;
  contactPhone: string | null;
  isActive: boolean;
  createdAt: string;
}

export interface CustomerRequest {
  nameAr?: string;
  nameEn: string;
  vatNumber?: string;
  idType?: string;
  idValue?: string;
  street?: string;
  buildingNumber?: string;
  city?: string;
  district?: string;
  postalCode?: string;
  countryCode?: string;
  customerType: string;
  contactEmail?: string;
  contactPhone?: string;
}

export interface ImportResponse {
  totalRows: number;
  importedCount: number;
  errorCount: number;
  errors: ImportError[];
}

export interface ImportError {
  row: number;
  field: string;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class CustomerService {
  private http = inject(HttpClient);
  private readonly apiUrl = '/api/customers';

  list(page: number, size: number, search?: string, type?: string,
      vatNumber?: string): Observable<PageResponse<CustomerResponse>> {
    let url = `${this.apiUrl}?page=${page}&size=${size}`;
    if (search) url += `&search=${encodeURIComponent(search)}`;
    if (type) url += `&type=${type}`;
    if (vatNumber) url += `&vatNumber=${encodeURIComponent(vatNumber)}`;
    return this.http.get<PageResponse<CustomerResponse>>(url);
  }

  get(id: number): Observable<CustomerResponse> {
    return this.http.get<CustomerResponse>(`${this.apiUrl}/${id}`);
  }

  create(request: CustomerRequest): Observable<CustomerResponse> {
    return this.http.post<CustomerResponse>(this.apiUrl, request);
  }

  update(id: number, request: CustomerRequest): Observable<CustomerResponse> {
    return this.http.patch<CustomerResponse>(`${this.apiUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  downloadTemplate(): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/template`, {
      responseType: 'blob'
    });
  }

  importCustomers(file: File): Observable<ImportResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportResponse>(`${this.apiUrl}/import`, formData);
  }
}
