import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { AdminService, CompanyResponse } from '../../shared/services/admin.service';

import { ToastNotificationService } from '../../shared/services/toast.service';

export interface AssignmentFormDialogData {
  userId: string;
}

const AUTHORITY_ENVIRONMENTS = [
  { id: 1, authority: 'ETA', environment: 'PRODUCTION', label: 'ETA Production' },
  { id: 2, authority: 'ETA', environment: 'PREPROD', label: 'ETA Pre-Production' },
  { id: 3, authority: 'ETA', environment: 'SIMULATION', label: 'ETA Simulation' },
  { id: 4, authority: 'ZATCA', environment: 'PRODUCTION', label: 'ZATCA Production' },
  { id: 5, authority: 'ZATCA', environment: 'SANDBOX', label: 'ZATCA Sandbox' },
];

const ETA_TRANSACTION_TYPES = ['INVOICE', 'RECEIPT', 'CUSTOMERS', 'ITEMS', 'CONFIG'];
const ZATCA_TRANSACTION_TYPES = ['STANDARD', 'SIMPLIFIED', 'CUSTOMERS', 'ITEMS', 'CONFIG'];

const ROLE_CODES = ['COMPANY_ADMIN', 'ACCOUNTANT', 'VIEWER'];

@Component({
  selector: 'app-assignment-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatButtonModule,
    MatSelectModule,
  ],
  templateUrl: './assignment-form.component.html',
  styles: `
    .dialog-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px; }
    .form-error { color: #c62828; font-size: 12px; margin-top: 4px; }
    form { display: flex; flex-direction: column; gap: 4px; }
  `,
})
export class AssignmentFormComponent implements OnInit {
  form: FormGroup;
  submitting = false;
  errorMessage: string | null = null;
  companies: CompanyResponse[] = [];
  authorityEnvironments = AUTHORITY_ENVIRONMENTS;
  roleCodes = ROLE_CODES;

  private fb = inject(FormBuilder);
  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);
  private dialogRef = inject(MatDialogRef<AssignmentFormComponent>);
  private data = inject(MAT_DIALOG_DATA) as AssignmentFormDialogData;

  constructor() {
    this.form = this.fb.group({
      companyId: [null as string | null, Validators.required],
      authorityEnvironmentId: [null as number | null, Validators.required],
      transactionType: [null as string | null, Validators.required],
      roleCode: [null as string | null, Validators.required],
    });

    this.form.get('authorityEnvironmentId')?.valueChanges.subscribe(() => {
      this.form.get('transactionType')?.setValue(null);
    });
  }

  ngOnInit(): void {
    this.adminService.listCompanies(true).subscribe({
      next: (companies) => { this.companies = companies; },
      error: () => this.toast.error('Failed to load companies'),
    });
  }

  get transactionTypes(): string[] {
    const envId = this.form.get('authorityEnvironmentId')?.value;
    if (!envId) return [];
    const env = AUTHORITY_ENVIRONMENTS.find(e => e.id === +envId);
    if (!env) return [];
    return env.authority === 'ETA' ? ETA_TRANSACTION_TYPES : ZATCA_TRANSACTION_TYPES;
  }

  save(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    this.errorMessage = null;
    const val = this.form.value;

    const request = {
      companyId: val.companyId,
      authorityEnvironmentId: +val.authorityEnvironmentId,
      transactionType: val.transactionType,
      roleCode: val.roleCode,
    };

    this.adminService.createAssignment(this.data.userId, request).subscribe({
      next: () => {
        this.toast.success('Assignment created');
        this.dialogRef.close(true);
      },
      error: (err) => {
        const code = err.error?.code;
        if (code === 'TAX_NUMBER_DUPLICATE_IN_CONTEXT') {
          this.errorMessage = 'Tax number already exists in this context.';
        } else if (code === 'INVALID_ROLE_FOR_AUTHORITY') {
          this.errorMessage = 'This role is not valid for the selected authority and transaction type.';
        } else if (code === 'ASSIGNMENT_EXISTS') {
          this.errorMessage = 'An assignment with these parameters already exists.';
        } else if (code === 'VALIDATION_ERROR') {
          this.errorMessage = err.error?.message || 'Validation failed';
        } else {
          this.errorMessage = err.error?.message || 'Failed to create assignment';
        }
        this.submitting = false;
      },
    });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
