import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface EtaConfigResponse {
  id: string | null;
  companyId: string;
  clientId: string | null;
  clientSecret1: string | null;
  clientSecret2: string | null;
  tokenName: string | null;
  tokenPass: string | null;
  submissionUrl: string | null;
  tokenUrl: string | null;
  posSerial: string | null;
  posOsVersion: string | null;
  posModel: string | null;
  isActive: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface EtaConfigWriteRequest {
  clientId: string;
  clientSecret1: string;
  clientSecret2: string;
  tokenName?: string;
  tokenPass?: string;
  submissionUrl: string;
  tokenUrl: string;
  posSerial?: string;
  posOsVersion?: string;
  posModel?: string;
}

@Injectable({ providedIn: 'root' })
export class EtaConfigService {
  private http = inject(HttpClient);

  read(companyId: string): Observable<EtaConfigResponse> {
    return this.http.get<EtaConfigResponse>(`/api/companies/${companyId}/eta/config`);
  }

  replace(companyId: string, payload: EtaConfigWriteRequest): Observable<EtaConfigResponse> {
    return this.http.put<EtaConfigResponse>(`/api/companies/${companyId}/eta/config`, payload);
  }
}
