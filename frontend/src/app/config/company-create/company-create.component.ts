import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AdminService } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-company-create',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './company-create.component.html',
  styles: `
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .full-width { grid-column: 1 / -1; }
    .form-actions { display: flex; gap: 12px; margin-top: 16px; justify-content: flex-end; }
  `,
})
export class CompanyCreateComponent {
  form: FormGroup;
  submitting = false;

  private fb = inject(FormBuilder);
  private adminService = inject(AdminService);
  private router = inject(Router);
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

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    this.adminService.createCompany(this.form.value).subscribe({
      next: (company) => {
        this.toast.success(`Company "${company.nameEn}" created`);
        this.router.navigate(['/config/company-create', company.id, 'branches']);
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to create company');
        this.submitting = false;
      },
    });
  }
}
