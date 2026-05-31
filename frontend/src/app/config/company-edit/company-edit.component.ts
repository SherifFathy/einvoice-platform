import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AdminService } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-company-edit',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './company-edit.component.html',
  styles: `
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .form-actions { display: flex; gap: 12px; margin-top: 16px; justify-content: flex-end; }
  `,
})
export class CompanyEditComponent implements OnInit {
  form: FormGroup;
  submitting = false;
  loading = false;
  companyId!: number;

  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);

  constructor() {
    this.form = this.fb.group({
      nameAr: ['', Validators.required],
      nameEn: ['', Validators.required],
      vatNumber: ['', [Validators.required, Validators.minLength(10)]],
      crNumber: [''],
    });
  }

  ngOnInit(): void {
    this.companyId = Number(this.route.snapshot.paramMap.get('companyId'));
    this.loading = true;
    this.adminService.listCompanies(0, 1000).subscribe({
      next: (res) => {
        const company = res.content.find((c) => c.id === this.companyId);
        if (company) {
          this.form.patchValue({
            nameAr: company.nameAr,
            nameEn: company.nameEn,
            vatNumber: company.vatNumber,
            crNumber: company.crNumber,
          });
        }
        this.loading = false;
      },
      error: () => {
        this.toast.error('Failed to load company');
        this.loading = false;
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid || !this.companyId) return;
    this.submitting = true;
    const request: Record<string, string> = {};
    const raw = this.form.value;
    for (const key of ['nameAr', 'nameEn', 'vatNumber', 'crNumber']) {
      if (raw[key] !== null && raw[key] !== undefined) {
        request[key] = raw[key];
      }
    }
    this.adminService.updateCompany(this.companyId, request).subscribe({
      next: () => {
        this.toast.success('Company updated');
        this.router.navigate(['/config/company-create']);
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to update company');
        this.submitting = false;
      },
    });
  }
}
