import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../shared/services/auth.service';
import { CompanyConfigService, CompanyProfile } from '../../shared/services/company-config.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-company-profile',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './company-profile.component.html',
  styles: `
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .full-width { grid-column: 1 / -1; }
    .form-actions { display: flex; gap: 12px; margin-top: 16px; justify-content: flex-end; }
  `,
})
export class CompanyProfileComponent implements OnInit {
  form: FormGroup;
  submitting = false;
  loading = false;
  companyId!: number;

  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private companyService = inject(CompanyConfigService);
  private toast = inject(ToastNotificationService);

  constructor() {
    this.form = this.fb.group({
      nameAr: ['', Validators.required],
      nameEn: ['', Validators.required],
      vatNumber: ['', [Validators.required, Validators.minLength(10)]],
      crNumber: [''],
      street: [''],
      buildingNumber: [''],
      city: [''],
      district: [''],
      postalCode: [''],
      countryCode: ['SA'],
      additionalId: [''],
    });
  }

  ngOnInit(): void {
    const companyId = this.authService.getActiveCompanyId();
    if (!companyId) return;
    this.companyId = companyId;
    this.loading = true;
    this.companyService.getCompany(companyId).subscribe({
      next: (company) => {
        this.form.patchValue({
          nameAr: company.nameAr,
          nameEn: company.nameEn,
          vatNumber: company.vatNumber,
          crNumber: company.crNumber,
          street: company.street,
          buildingNumber: company.buildingNumber,
          city: company.city,
          district: company.district,
          postalCode: company.postalCode,
          countryCode: company.countryCode,
          additionalId: company.additionalId,
        });
        this.loading = false;
      },
      error: () => {
        this.toast.error('Failed to load company profile');
        this.loading = false;
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid || !this.companyId) return;
    this.submitting = true;
    this.companyService.updateCompany(this.companyId, this.form.value).subscribe({
      next: () => {
        this.toast.success('Company profile updated');
        this.submitting = false;
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to update company profile');
        this.submitting = false;
      },
    });
  }
}
