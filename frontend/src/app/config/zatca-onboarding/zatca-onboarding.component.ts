import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { ZatcaService, OnboardingStatusResponse } from '../../shared/services/zatca.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-zatca-onboarding',
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, RouterModule,
    MatCardModule, MatButtonModule, MatInputModule,
    MatSelectModule, MatProgressSpinnerModule, MatIconModule, MatSnackBarModule,
  ],
  templateUrl: './zatca-onboarding.component.html',
  styles: [`
    .onboarding-container { max-width: 800px; margin: 0 auto; padding: 16px; }
    .step-indicator { display: flex; gap: 8px; margin-bottom: 24px; }
    .step-dot { width: 12px; height: 12px; border-radius: 50%; background: #ccc; }
    .step-dot.completed { background: #4caf50; }
    .step-dot.current { background: #1976d2; }
    .form-row { display: flex; gap: 16px; margin-bottom: 16px; }
    .form-row > * { flex: 1; }
    .error-message { color: #f44336; margin-top: 8px; }
    .success-message { color: #4caf50; margin-top: 8px; }
    .import-section { margin-top: 32px; padding-top: 24px; border-top: 1px solid #e0e0e0; }
    .file-upload { margin: 8px 0; }
  `],
})
export class ZatcaOnboardingComponent implements OnInit {
  private zatcaService = inject(ZatcaService);
  private toast = inject(ToastNotificationService);
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);

  branchId = 0;
  environment = 'ZATCA_SANDBOX';
  loading = false;
  status: OnboardingStatusResponse | null = null;
  importing = false;
  importCsidSecret = '';

  csrForm: FormGroup = this.fb.group({
    commonName: ['', Validators.required],
    organizationUnit: [''],
    organization: ['', Validators.required],
    country: ['SA', Validators.required],
    serialNumber: ['', Validators.required],
    otp: [''],
  });

  certificateFile: File | null = null;
  privateKeyFile: File | null = null;

  ngOnInit(): void {
    this.branchId = Number(this.route.snapshot.paramMap.get('branchId') ?? 0);
    this.loadStatus();
  }

  get displaySteps(): string[] {
    if (this.status) {
      return [...this.status.completedSteps, this.status.currentStep, ...this.status.remainingSteps]
        .filter((s, i, arr) => arr.indexOf(s) === i);
    }
    return [];
  }

  loadStatus(): void {
    this.loading = true;
    this.zatcaService.getOnboardingStatus(this.branchId, this.environment).subscribe({
      next: (res) => { this.status = res; this.loading = false; },
      error: (err) => {
        this.loading = false;
        this.toast.show('Failed to load status: ' + (err.error?.error ?? err.message));
      },
    });
  }

  startOnboarding(): void {
    if (this.csrForm.invalid) return;
    this.loading = true;
    this.zatcaService.onboard(this.branchId, {
      environment: this.environment,
      csrData: this.csrForm.value,
    }).subscribe({
      next: (res) => {
        this.status = res;
        this.loading = false;
        this.toast.show('Onboarding completed successfully!');
      },
      error: (err) => {
        this.loading = false;
        this.toast.show('Onboarding failed: ' + (err.error?.message ?? err.error?.error ?? err.message));
        this.loadStatus();
      },
    });
  }

  onCertificateSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) { this.certificateFile = input.files[0]; }
  }

  onPrivateKeySelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) { this.privateKeyFile = input.files[0]; }
  }

  importCsid(): void {
    if (!this.certificateFile || !this.privateKeyFile || !this.importCsidSecret) {
      this.toast.show('Certificate, private key, and CSID secret are required');
      return;
    }
    this.importing = true;
    this.zatcaService.importCsid(this.branchId, this.environment,
      this.certificateFile, this.privateKeyFile, this.importCsidSecret).subscribe({
      next: () => {
        this.importing = false;
        this.toast.show('CSID imported successfully');
        this.loadStatus();
      },
      error: (err) => {
        this.importing = false;
        this.toast.show('Import failed: ' + (err.error?.error ?? err.message));
      },
    });
  }

  isStepCompleted(step: string): boolean {
    return this.status?.completedSteps?.includes(step) ?? false;
  }

  isStepCurrent(step: string): boolean {
    return this.status?.currentStep === step;
  }
}
