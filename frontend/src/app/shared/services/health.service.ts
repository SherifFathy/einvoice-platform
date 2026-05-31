import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface HelloResponse {
  message: string;
  timestamp: string;
  profiles: string[];
}

@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly apiUrl = '/api/health/hello';
  private http = inject(HttpClient);

  getHello(): Observable<HelloResponse> {
    return this.http.get<HelloResponse>(this.apiUrl);
  }
}
