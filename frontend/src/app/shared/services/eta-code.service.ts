import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CodeResponse {
  id: number;
  itemCode: string;
  codeType: string;
  description: string | null;
  status: string;
  etaCodeId: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface CodePageResponse {
  content: CodeResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface PublishedCodeResponse {
  itemCode: string;
  codeType: string;
  description: string;
  publishedBy: string;
  publishedAt: string | null;
}

export interface PublishedCodePageResponse {
  content: PublishedCodeResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CodeRequest {
  itemCode: string;
  codeType: string;
  description?: string;
}

@Injectable({ providedIn: 'root' })
export class EtaCodeService {
  private http = inject(HttpClient);

  register(request: CodeRequest): Observable<CodeResponse> {
    return this.http.post<CodeResponse>('/api/eta/codes', request);
  }

  list(params: {
    status?: string;
    search?: string;
    page?: number;
    size?: number;
  }): Observable<CodePageResponse> {
    let httpParams = new HttpParams();
    if (params.status) httpParams = httpParams.set('status', params.status);
    if (params.search) httpParams = httpParams.set('search', params.search);
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size);
    return this.http.get<CodePageResponse>('/api/eta/codes', { params: httpParams });
  }

  searchPublished(query: string, page = 0, size = 20): Observable<PublishedCodePageResponse> {
    const params = new HttpParams()
      .set('query', query)
      .set('page', page)
      .set('size', size);
    return this.http.get<PublishedCodePageResponse>('/api/eta/codes/search-published', { params });
  }

  update(id: number, request: CodeRequest): Observable<CodeResponse> {
    return this.http.put<CodeResponse>(`/api/eta/codes/${id}`, request);
  }
}
