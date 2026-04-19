import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PollingStatusResponse {
  enabled: boolean;
  running: boolean;
  invoicesInReview: number;
  lastPollAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class EtaPollingService {
  private http = inject(HttpClient);
  private readonly apiUrl = '/api/admin/eta-polling';

  getStatus(): Observable<PollingStatusResponse> {
    return this.http.get<PollingStatusResponse>(`${this.apiUrl}/status`);
  }

  stop(): Observable<PollingStatusResponse> {
    return this.http.post<PollingStatusResponse>(`${this.apiUrl}/stop`, null);
  }

  resume(): Observable<PollingStatusResponse> {
    return this.http.post<PollingStatusResponse>(`${this.apiUrl}/resume`, null);
  }
}
