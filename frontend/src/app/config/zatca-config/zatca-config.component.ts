import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { Subject, takeUntil } from 'rxjs';
import { ZatcaConfigService, ZatcaConfigWriteRequest } from '../services/zatca-config.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-zatca-config',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatDatepickerModule, MatNativeDateModule,
    HasPermissionDirective,
  ],
  templateUrl: './zatca-config.component.html',
  styleUrls: ['./zatca-config.component.scss'],
})
export class ZatcaConfigComponent implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private configService = inject(ZatcaConfigService);
  private sessionCtx = inject(SessionContextService);
  private toast = inject(ToastNotificationService);
  private destroy$ = new Subject<void>();

  form: FormGroup;
  submitting = false;
  loading = false;
  saved = false;
  chainStateInitialized = false;
  selectedCompanyId = '';

  constructor() {
    this.form = this.fb.group({
      privateKey: ['', Validators.required],
      deviceUuid: ['', Validators.required],
      csr: ['', Validators.required],
      complianceCertificate: ['', Validators.required],
      complianceApiSecret: ['', Validators.required],
      productionCertificate: [''],
      productionApiSecret: [''],
      certificateExpiryDate: [''],
    });
  }

  ngOnInit(): void {
    this.sessionCtx.context$.pipe(takeUntil(this.destroy$)).subscribe((ctx) => {
      if (ctx && ctx.companies.length > 0 && !this.selectedCompanyId) {
        this.selectedCompanyId = ctx.companies[0].companyId;
        this.loadConfig();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadConfig(): void {
    if (!this.selectedCompanyId) return;
    this.loading = true;
    this.configService.read(this.selectedCompanyId).subscribe({
      next: (config) => {
        if (config.id) {
          this.form.patchValue({
            privateKey: config.privateKey || '',
            deviceUuid: config.deviceUuid || '',
            csr: config.csr || '',
            complianceCertificate: config.complianceCertificate || '',
            complianceApiSecret: config.complianceApiSecret || '',
            productionCertificate: config.productionCertificate || '',
            productionApiSecret: config.productionApiSecret || '',
            certificateExpiryDate: config.certificateExpiryDate || '',
          });
          this.chainStateInitialized = config.chainStateInitialized;
          this.saved = true;
        }
        this.loading = false;
      },
      error: () => {
        this.toast.error('Failed to load ZATCA configuration');
        this.loading = false;
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    this.saved = false;
    const formValue = this.form.value;

    const payload: ZatcaConfigWriteRequest = {
      privateKey: formValue.privateKey,
      deviceUuid: formValue.deviceUuid,
      csr: formValue.csr,
      complianceCertificate: formValue.complianceCertificate,
      complianceApiSecret: formValue.complianceApiSecret,
    };

    if (formValue.certificateExpiryDate instanceof Date) {
      const dt = formValue.certificateExpiryDate as Date;
      const y = dt.getFullYear();
      const m = String(dt.getMonth() + 1).padStart(2, '0');
      const d = String(dt.getDate()).padStart(2, '0');
      payload.certificateExpiryDate = `${y}-${m}-${d}`;
    } else if (formValue.certificateExpiryDate) {
      payload.certificateExpiryDate = formValue.certificateExpiryDate;
    }
    if (formValue.productionCertificate) {
      payload.productionCertificate = formValue.productionCertificate;
    }
    if (formValue.productionApiSecret) {
      payload.productionApiSecret = formValue.productionApiSecret;
    }

    this.configService.replace(this.selectedCompanyId, payload).subscribe({
      next: (response) => {
        this.toast.success('ZATCA configuration saved');
        this.submitting = false;
        this.saved = true;
        this.chainStateInitialized = response.chainStateInitialized;
      },
      error: (err) => {
        const code = err.error?.code || '';
        const message = err.error?.message || 'Failed to save ZATCA configuration';
        this.toast.error(code ? `${code}: ${message}` : message);
        this.submitting = false;
      },
    });
  }
}
