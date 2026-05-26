import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { ZatcaService, CertificateStatusResponse } from '../../shared/services/zatca.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-zatca-certificate',
  imports: [
    CommonModule, RouterModule,
    MatCardModule, MatButtonModule, MatProgressSpinnerModule,
    MatIconModule, MatSnackBarModule,
  ],
  templateUrl: './zatca-certificate.component.html',
  styles: [`
    .cert-container { max-width: 600px; margin: 0 auto; padding: 16px; }
    .cert-status { display: flex; align-items: center; gap: 16px; margin: 16px 0; }
    .status-icon { font-size: 48px; height: 48px; width: 48px; }
    .status-icon.valid { color: #4caf50; }
    .status-icon.warning { color: #ff9800; }
    .status-icon.expired { color: #f44336; }
    .status-icon.none { color: #9e9e9e; }
    .detail-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #eee; }
    .detail-label { font-weight: 500; }
    .warning-banner { background: #fff3e0; padding: 12px; border-radius: 4px; margin: 12px 0; color: #e65100; }
  `],
})
export class ZatcaCertificateComponent implements OnInit {
  private zatcaService = inject(ZatcaService);
  private toast = inject(ToastNotificationService);
  private route = inject(ActivatedRoute);

  branchId = 0;
  environment = 'ZATCA_SANDBOX';
  loading = false;
  renewing = false;
  status: CertificateStatusResponse | null = null;

  ngOnInit(): void {
    this.branchId = Number(this.route.snapshot.paramMap.get('branchId') ?? 0);
    this.loadStatus();
  }

  loadStatus(): void {
    this.loading = true;
    this.zatcaService.getCertificateStatus(this.branchId, this.environment).subscribe({
      next: (res) => { this.status = res; this.loading = false; },
      error: (err) => {
        this.loading = false;
        this.toast.show('Failed to load certificate status: ' + (err.error?.error ?? err.message));
      },
    });
  }

  renewCertificate(): void {
    this.renewing = true;
    this.zatcaService.renewCertificate(this.branchId, this.environment).subscribe({
      next: () => {
        this.renewing = false;
        this.toast.show('Certificate renewed successfully');
        this.loadStatus();
      },
      error: (err) => {
        this.renewing = false;
        this.toast.show('Renewal failed: ' + (err.error?.error ?? err.message));
      },
    });
  }

  getStatusIcon(): string {
    if (!this.status?.hasCertificate) return 'cert_off';
    if (this.status.daysUntilExpiry < 0) return 'warning';
    if (this.status.expiryWarning) return 'warning';
    return 'verified';
  }

  getStatusClass(): string {
    if (!this.status?.hasCertificate) return 'none';
    if (this.status.daysUntilExpiry < 0) return 'expired';
    if (this.status.expiryWarning) return 'warning';
    return 'valid';
  }
}
