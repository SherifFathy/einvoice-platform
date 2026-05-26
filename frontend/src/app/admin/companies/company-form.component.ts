import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AdminService, CompanyResponse } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

export interface CompanyFormDialogData {
  mode: 'create' | 'edit';
  company?: CompanyResponse;
}

@Component({
  selector: 'app-company-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './company-form.component.html',
  styles: `
    .dialog-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px; }
    .form-error { color: #c62828; font-size: 12px; margin-top: 4px; }
    form { display: flex; flex-direction: column; gap: 4px; }
  `,
})
export class CompanyFormComponent {
  form: FormGroup;
  submitting = false;
  errorMessage: string | null = null;

  private fb = inject(FormBuilder);
  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);
  private dialogRef = inject(MatDialogRef<CompanyFormComponent>);
  private data = inject(MAT_DIALOG_DATA) as CompanyFormDialogData;

  constructor() {
    const company = this.data.company;
    this.form = this.fb.group({
      nameEn: [company?.nameEn ?? '', Validators.required],
      nameAr: [company?.nameAr ?? '', Validators.required],
      taxNumber: [company?.taxNumber ?? '', Validators.required],
      crNumber: [company?.crNumber ?? ''],
    });
  }

  get isCreate(): boolean {
    return this.data.mode === 'create';
  }

  save(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    this.errorMessage = null;
    const val = this.form.value;

    const request = {
      nameEn: val.nameEn,
      nameAr: val.nameAr,
      taxNumber: val.taxNumber,
      crNumber: val.crNumber || undefined,
    };

    const obs$ = this.isCreate
      ? this.adminService.createCompany(request)
      : this.adminService.updateCompany(this.data.company!.id, request);

    obs$.subscribe({
      next: () => {
        this.toast.success(this.isCreate ? 'Company created' : 'Company updated');
        this.dialogRef.close(true);
      },
      error: (err) => {
        const code = err.error?.code;
        if (code === 'VALIDATION_ERROR') {
          this.errorMessage = err.error?.message || 'Validation failed';
        } else {
          this.errorMessage = err.error?.message || 'Failed to save company';
        }
        this.submitting = false;
      },
    });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
