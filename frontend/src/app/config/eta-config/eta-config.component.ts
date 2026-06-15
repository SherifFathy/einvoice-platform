import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { Subject, takeUntil } from 'rxjs';
import { EtaConfigService } from '../services/eta-config.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

/** A company the user may configure within the active authority+environment. */
interface CompanyOption {
  companyId: string;
  companyNameEn: string;
}

@Component({
  selector: 'app-eta-config',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatSelectModule, HasPermissionDirective,
  ],
  templateUrl: './eta-config.component.html',
  styleUrls: ['./eta-config.component.scss'],
})
export class EtaConfigComponent implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private configService = inject(EtaConfigService);
  private sessionCtx = inject(SessionContextService);
  private toast = inject(ToastNotificationService);
  private destroy$ = new Subject<void>();

  form: FormGroup;
  submitting = false;
  loading = false;
  saved = false;
  selectedCompanyId = '';
  companies: CompanyOption[] = [];
  isProductionEnv = false;

  constructor() {
    this.form = this.fb.group({
      clientId: ['', Validators.required],
      clientSecret1: ['', Validators.required],
      clientSecret2: ['', Validators.required],
      tokenName: [''],
      tokenPass: [''],
      submissionUrl: ['', Validators.required],
      tokenUrl: ['', Validators.required],
      posSerial: [''],
      posOsVersion: [''],
      posModel: [''],
    });
  }

  ngOnInit(): void {
    // Map the form to the active authority+environment scope. The company is
    // chosen from a switcher (the companies the user may access in this scope);
    // the JWT company (activeCompanyId), when present, is the default selection.
    this.sessionCtx.context$.pipe(takeUntil(this.destroy$)).subscribe((ctx) => {
      if (!ctx) return;
      this.companies = (ctx.companies ?? []).map((c) => ({
        companyId: c.companyId,
        companyNameEn: c.companyNameEn,
      }));
      this.isProductionEnv = ctx.loginContext?.authorityEnvironmentId === 1;
      this.updateTokenFieldValidators();

      // Keep the current selection if still valid; otherwise default to the JWT
      // company, then the first accessible company.
      const stillValid = this.companies.some((c) => c.companyId === this.selectedCompanyId);
      if (stillValid) return;
      const companyId = ctx.activeCompanyId ?? this.companies[0]?.companyId ?? '';
      if (companyId) {
        this.selectedCompanyId = companyId;
        this.loadConfig();
      }
    });
  }

  /** Switches the configuration to a different company and reloads its values. */
  onCompanyChange(companyId: string): void {
    if (!companyId || companyId === this.selectedCompanyId) return;
    this.selectedCompanyId = companyId;
    this.saved = false;
    this.form.reset();
    this.loadConfig();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateTokenFieldValidators(): void {
    const tokenName = this.form.get('tokenName');
    const tokenPass = this.form.get('tokenPass');
    if (this.isProductionEnv) {
      tokenName?.setValidators([Validators.required]);
      tokenPass?.setValidators([Validators.required]);
    } else {
      tokenName?.clearValidators();
      tokenPass?.clearValidators();
    }
    tokenName?.updateValueAndValidity();
    tokenPass?.updateValueAndValidity();
  }

  private loadConfig(): void {
    if (!this.selectedCompanyId) return;
    this.loading = true;
    this.configService.read(this.selectedCompanyId).subscribe({
      next: (config) => {
        if (config.id) {
          this.form.patchValue({
            clientId: config.clientId || '',
            clientSecret1: config.clientSecret1 || '',
            clientSecret2: config.clientSecret2 || '',
            tokenName: config.tokenName || '',
            tokenPass: config.tokenPass || '',
            submissionUrl: config.submissionUrl || '',
            tokenUrl: config.tokenUrl || '',
            posSerial: config.posSerial || '',
            posOsVersion: config.posOsVersion || '',
            posModel: config.posModel || '',
          });
          this.saved = true;
        }
        this.loading = false;
      },
      error: () => {
        this.toast.error('Failed to load ETA configuration');
        this.loading = false;
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    const payload = { ...this.form.value };

    this.configService.replace(this.selectedCompanyId, payload).subscribe({
      next: () => {
        this.toast.success('ETA configuration saved');
        this.submitting = false;
        this.saved = true;
      },
      error: (err) => {
        const code = err.error?.code || '';
        const message = err.error?.message || 'Failed to save ETA configuration';
        this.toast.error(code ? `${code}: ${message}` : message);
        this.submitting = false;
      },
    });
  }
}
