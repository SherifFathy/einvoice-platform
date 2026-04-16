import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from '../components/data-table/data-table.component';

export interface ItemResponse {
  id: number;
  code: string;
  nameAr: string | null;
  nameEn: string;
  unitOfMeasure: string;
  unitPrice: number;
  vatCategory: string;
  vatRate: number;
  description: string | null;
  authorityScope: string;
  isActive: boolean;
  createdAt: string;
}

export interface ItemRequest {
  code: string;
  nameEn: string;
  nameAr?: string;
  unitOfMeasure: string;
  unitPrice: number;
  vatCategory: string;
  vatRate: number;
  description?: string;
  authorityScope?: string;
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
export class ItemService {
  private http = inject(HttpClient);
  private readonly apiUrl = '/api/items';

  list(page: number, size: number, search?: string,
      authorityScope?: string): Observable<PageResponse<ItemResponse>> {
    let url = `${this.apiUrl}?page=${page}&size=${size}`;
    if (search) url += `&search=${encodeURIComponent(search)}`;
    if (authorityScope) url += `&authorityScope=${authorityScope}`;
    return this.http.get<PageResponse<ItemResponse>>(url);
  }

  get(id: number): Observable<ItemResponse> {
    return this.http.get<ItemResponse>(`${this.apiUrl}/${id}`);
  }

  create(request: ItemRequest): Observable<ItemResponse> {
    return this.http.post<ItemResponse>(this.apiUrl, request);
  }

  update(id: number, request: ItemRequest): Observable<ItemResponse> {
    return this.http.put<ItemResponse>(`${this.apiUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  downloadTemplate(): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/template`, {
      responseType: 'blob'
    });
  }

  importItems(file: File): Observable<ImportResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportResponse>(`${this.apiUrl}/import`, formData);
  }
}
