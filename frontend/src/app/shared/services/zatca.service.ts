import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface OnboardingStatusResponse {
  branchId: number;
  currentStep: string;
  completedSteps: string[];
  remainingSteps: string[];
  status: string;
  message: string;
  lastError: string | null;
  startedAt: string | null;
  completedAt: string | null;
}

export interface CertificateStatusResponse {
  branchId: number;
  environment: string;
  hasCertificate: boolean;
  expiryDate: string | null;
  daysUntilExpiry: number;
  expiryWarning: boolean;
  onboardingStatus: string;
}

export interface ImportCsidResponse {
  branchId: number;
  environment: string;
  certificateExpiryDate: string;
  status: string;
}

export interface RenewCertificateResponse {
  branchId: number;
  environment: string;
  newCertificateExpiryDate: string | null;
  status: string;
}

export interface OnboardRequest {
  environment: string;
  csrData?: {
    commonName?: string;
    organizationUnit?: string;
    organization?: string;
    country?: string;
    serialNumber?: string;
    otp?: string;
  };
}

@Injectable({ providedIn: 'root' })
export class ZatcaService {
  private http = inject(HttpClient);

  onboard(branchId: number, request: OnboardRequest): Observable<OnboardingStatusResponse> {
    return this.http.post<OnboardingStatusResponse>(
      `/api/branches/${branchId}/zatca/onboard`, request);
  }

  getOnboardingStatus(branchId: number, environment: string): Observable<OnboardingStatusResponse> {
    return this.http.get<OnboardingStatusResponse>(
      `/api/branches/${branchId}/zatca/onboard/status?environment=${environment}`);
  }

  importCsid(branchId: number, environment: string,
      certificate: File, privateKey: File, csidSecret: string): Observable<ImportCsidResponse> {
    const formData = new FormData();
    formData.append('environment', environment);
    formData.append('certificate', certificate);
    formData.append('privateKey', privateKey);
    formData.append('csidSecret', csidSecret);
    return this.http.post<ImportCsidResponse>(
      `/api/branches/${branchId}/zatca/import-csid`, formData);
  }

  renewCertificate(branchId: number, environment: string): Observable<RenewCertificateResponse> {
    return this.http.post<RenewCertificateResponse>(
      `/api/branches/${branchId}/zatca/renew-certificate`, { environment });
  }

  getCertificateStatus(branchId: number, environment: string): Observable<CertificateStatusResponse> {
    return this.http.get<CertificateStatusResponse>(
      `/api/branches/${branchId}/zatca/certificate-status?environment=${environment}`);
  }
}
