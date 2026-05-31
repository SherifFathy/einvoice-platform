import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ZatcaConfigResponse {
  id: string | null;
  companyId: string;
  privateKey: string | null;
  deviceUuid: string | null;
  csr: string | null;
  complianceCertificate: string | null;
  complianceApiSecret: string | null;
  productionCertificate: string | null;
  productionApiSecret: string | null;
  certificateExpiryDate: string | null;
  chainStateInitialized: boolean;
  isActive: boolean | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ZatcaConfigWriteRequest {
  privateKey: string;
  deviceUuid: string;
  csr: string;
  complianceCertificate: string;
  complianceApiSecret: string;
  productionCertificate?: string;
  productionApiSecret?: string;
  certificateExpiryDate?: string;
}

@Injectable({ providedIn: 'root' })
export class ZatcaConfigService {
  private http = inject(HttpClient);

  read(companyId: string): Observable<ZatcaConfigResponse> {
    return this.http.get<ZatcaConfigResponse>(`/api/companies/${companyId}/zatca/config`);
  }

  replace(companyId: string, payload: ZatcaConfigWriteRequest): Observable<ZatcaConfigResponse> {
    return this.http.put<ZatcaConfigResponse>(`/api/companies/${companyId}/zatca/config`, payload);
  }
}
