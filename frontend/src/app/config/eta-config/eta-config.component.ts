import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { Subject, takeUntil } from 'rxjs';
import { EtaConfigService } from '../services/eta-config.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-eta-config',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, HasPermissionDirective,
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
    this.sessionCtx.context$.pipe(takeUntil(this.destroy$)).subscribe((ctx) => {
      if (ctx && ctx.companies.length > 0 && !this.selectedCompanyId) {
        this.selectedCompanyId = ctx.companies[0].companyId;
        this.isProductionEnv = ctx.loginContext?.authorityEnvironmentId === 1;
        this.updateTokenFieldValidators();
        this.loadConfig();
      }
    });
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
