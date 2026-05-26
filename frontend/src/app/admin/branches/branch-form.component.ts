import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AdminService, BranchResponse } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

export interface BranchFormDialogData {
  mode: 'create' | 'edit';
  companyId: string;
  branch?: BranchResponse;
}

@Component({
  selector: 'app-branch-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './branch-form.component.html',
  styles: `
    .dialog-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px; }
    .form-error { color: #c62828; font-size: 12px; margin-top: 4px; }
    form { display: flex; flex-direction: column; gap: 4px; }
    .form-row { display: flex; gap: 12px; }
    .form-row > * { flex: 1; }
    .section-divider { border-top: 1px solid #e0e0e0; margin: 12px 0; padding-top: 12px; }
    .section-title { font-weight: 600; margin-bottom: 8px; color: #555; }
  `,
})
export class BranchFormComponent {
  form: FormGroup;
  submitting = false;
  errorMessage: string | null = null;

  private fb = inject(FormBuilder);
  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);
  private dialogRef = inject(MatDialogRef<BranchFormComponent>);
  private data = inject(MAT_DIALOG_DATA) as BranchFormDialogData;

  constructor() {
    const branch = this.data.branch;
    this.form = this.fb.group({
      nameEn: [branch?.nameEn ?? '', Validators.required],
      nameAr: [branch?.nameAr ?? '', Validators.required],
      branchCode: [branch?.branchCode ?? ''],
      addressLine1: [branch?.addressLine1 ?? ''],
      addressLine2: [branch?.addressLine2 ?? ''],
      city: [branch?.city ?? ''],
      region: [branch?.region ?? ''],
      postalCode: [branch?.postalCode ?? ''],
      country: [branch?.country ?? 'SA'],
      buildingNumber: [branch?.buildingNumber ?? ''],
      additionalNo: [branch?.additionalNo ?? ''],
      taxpayerActivityCode: [branch?.taxpayerActivityCode ?? ''],
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
      branchCode: val.branchCode || undefined,
      addressLine1: val.addressLine1 || undefined,
      addressLine2: val.addressLine2 || undefined,
      city: val.city || undefined,
      region: val.region || undefined,
      postalCode: val.postalCode || undefined,
      country: val.country || undefined,
      buildingNumber: val.buildingNumber || undefined,
      additionalNo: val.additionalNo || undefined,
      taxpayerActivityCode: val.taxpayerActivityCode || undefined,
    };

    const obs$ = this.isCreate
      ? this.adminService.createBranch(this.data.companyId, request)
      : this.adminService.updateBranch(this.data.branch!.id, request);

    obs$.subscribe({
      next: () => {
        this.toast.success(this.isCreate ? 'Branch created' : 'Branch updated');
        this.dialogRef.close(true);
      },
      error: (err) => {
        const code = err.error?.code;
        if (code === 'BRANCH_CODE_DUPLICATE_IN_COMPANY') {
          this.errorMessage = 'Branch code already exists in this company.';
        } else if (code === 'VALIDATION_ERROR') {
          this.errorMessage = err.error?.message || 'Validation failed';
        } else {
          this.errorMessage = err.error?.message || 'Failed to save branch';
        }
        this.submitting = false;
      },
    });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
