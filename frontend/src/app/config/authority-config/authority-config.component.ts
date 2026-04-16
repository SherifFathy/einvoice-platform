import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { CompanyConfigService } from '../../shared/services/company-config.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

export interface AuthorityConfigResponse {
  id: number;
  branchId: number;
  authority: string;
  environment: string;
  hasCredentials: boolean;
  hasCertificate: boolean;
  certificateExpiryDate: string | null;
  invoiceCounter: number;
  invoicePrefix: string | null;
  invoiceStartingNumber: number | null;
  invoiceResetPolicy: string | null;
  isActive: boolean;
}

@Component({
  selector: 'app-authority-config',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './authority-config.component.html',
  styles: `
    .config-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
    .form-actions { display: flex; gap: 12px; margin-top: 16px; }
    table { width: 100%; }
    .badge { padding: 2px 8px; border-radius: 4px; font-size: 12px; }
    .badge-yes { background: #e8f5e9; color: #2e7d32; }
    .badge-no { background: #fafafa; color: #999; }
    .nav-actions { margin-top: 24px; display: flex; gap: 12px; }
  `,
})
export class AuthorityConfigComponent implements OnInit {
  companyId!: number;
  branchId!: number;
  configs: AuthorityConfigResponse[] = [];
  displayedColumns = ['authority', 'environment', 'hasCredentials', 'hasCertificate', 'invoicePrefix', 'invoiceResetPolicy'];
  form: FormGroup;
  submitting = false;
  loading = false;

  authorities = ['ZATCA', 'ETA'];
  resetPolicies = ['NEVER', 'ANNUAL', 'MONTHLY'];

  get filteredEnvironments(): string[] {
    const auth = this.form.get('authority')?.value;
    if (auth === 'ZATCA') return ['ZATCA_SANDBOX', 'ZATCA_SIMULATION', 'ZATCA_PRODUCTION'];
    if (auth === 'ETA') return ['ETA_PREPRODUCTION', 'ETA_PRODUCTION'];
    return [];
  }

  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private configService = inject(CompanyConfigService);
  private toast = inject(ToastNotificationService);

  constructor() {
    this.form = this.fb.group({
      authority: ['', Validators.required],
      environment: ['', Validators.required],
      credentials: [''],
      certificate: [''],
      privateKey: [''],
      invoicePrefix: [''],
      invoiceStartingNumber: [1],
      invoiceResetPolicy: ['NEVER'],
    });
  }

  ngOnInit(): void {
    this.companyId = Number(this.route.snapshot.paramMap.get('companyId'));
    this.branchId = Number(this.route.snapshot.paramMap.get('branchId'));
    this.loadConfigs();
  }

  loadConfigs(): void {
    this.loading = true;
    this.configService.listAuthorityConfigs(this.companyId, this.branchId).subscribe({
      next: (configs) => { this.configs = configs; this.loading = false; },
      error: () => { this.toast.error('Failed to load authority configs'); this.loading = false; },
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    const value = this.form.value;
    this.configService.upsertAuthorityConfig(this.companyId, this.branchId, {
      authority: value.authority,
      environment: value.environment,
      credentials: value.credentials || undefined,
      certificate: value.certificate || undefined,
      privateKey: value.privateKey || undefined,
      invoicePrefix: value.invoicePrefix || undefined,
      invoiceStartingNumber: value.invoiceStartingNumber || undefined,
      invoiceResetPolicy: value.invoiceResetPolicy || undefined,
    }).subscribe({
      next: () => {
        this.toast.success('Authority config saved');
        this.form.reset({ invoiceStartingNumber: 1, invoiceResetPolicy: 'NEVER' });
        this.submitting = false;
        this.loadConfigs();
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Failed to save config');
        this.submitting = false;
      },
    });
  }

  onCertificateUpload(event: Event, config: AuthorityConfigResponse): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    file.arrayBuffer().then(buffer => {
      const base64 = btoa(String.fromCharCode(...new Uint8Array(buffer)));
      this.configService.upsertAuthorityConfig(this.companyId, this.branchId, {
        authority: config.authority,
        environment: config.environment,
        certificate: base64,
      }).subscribe({
        next: () => { this.toast.success('Certificate uploaded'); this.loadConfigs(); },
        error: () => this.toast.error('Failed to upload certificate'),
      });
    });
  }

  onCredentialsUpload(event: Event, config: AuthorityConfigResponse): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    file.arrayBuffer().then(buffer => {
      const base64 = btoa(String.fromCharCode(...new Uint8Array(buffer)));
      this.configService.upsertAuthorityConfig(this.companyId, this.branchId, {
        authority: config.authority,
        environment: config.environment,
        credentials: base64,
      }).subscribe({
        next: () => { this.toast.success('Credentials uploaded'); this.loadConfigs(); },
        error: () => this.toast.error('Failed to upload credentials'),
      });
    });
  }

  goBack(): void {
    this.router.navigate(['/config/company-create', this.companyId, 'branches']);
  }

  goToAssignUser(): void {
    this.router.navigate(['/config/company-create', this.companyId, 'assign-user']);
  }
}
