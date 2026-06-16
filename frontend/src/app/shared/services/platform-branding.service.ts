import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PlatformBrandingResponse {
  logoConfigured: boolean;
  logoMime: string | null;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class PlatformBrandingService {
  private http = inject(HttpClient);
  readonly logoVersion = signal(Date.now());

  logoUrl(version: number | string = this.logoVersion()): string {
    return `/api/platform/branding/logo?v=${version}`;
  }

  refreshLogo(): void {
    this.logoVersion.set(Date.now());
  }

  uploadLogo(file: File): Observable<PlatformBrandingResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<PlatformBrandingResponse>(
      '/api/platform/branding/logo',
      formData,
    );
  }

  deleteLogo(): Observable<void> {
    return this.http.delete<void>('/api/platform/branding/logo');
  }
}
