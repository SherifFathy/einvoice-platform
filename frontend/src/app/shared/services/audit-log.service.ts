import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from '../components/data-table/data-table.component';

export interface AuditLogResponse {
  id: number;
  companyId: number;
  userId: number;
  action: string;
  entityType: string;
  entityId: string;
  payloadBefore: string | null;
  payloadAfter: string | null;
  ipAddress: string | null;
  timestamp: string;
}

@Injectable({ providedIn: 'root' })
export class AuditLogService {
  private http = inject(HttpClient);
  private readonly apiUrl = '/api/audit-logs';

  list(page: number, size: number, entityType?: string,
      entityId?: string, from?: string, to?: string): Observable<PageResponse<AuditLogResponse>> {
    let url = `${this.apiUrl}?page=${page}&size=${size}`;
    if (entityType) url += `&entityType=${encodeURIComponent(entityType)}`;
    if (entityId) url += `&entityId=${encodeURIComponent(entityId)}`;
    if (from) url += `&from=${encodeURIComponent(from)}`;
    if (to) url += `&to=${encodeURIComponent(to)}`;
    return this.http.get<PageResponse<AuditLogResponse>>(url);
  }
}
