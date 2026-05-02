import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { AdminService, AdminUserResponse, AdminUserCompanyAssignment } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

export interface UserEditDialogData {
  mode: 'create' | 'edit';
  user?: AdminUserResponse;
}

@Component({
  selector: 'app-user-edit-dialog',
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatCheckboxModule,
    MatIconModule,
  ],
  templateUrl: './user-edit-dialog.component.html',
  styles: `
    .dialog-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px; }
    .assign-section { margin-top: 16px; border-top: 1px solid #eee; padding-top: 12px; }
    .company-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
  `,
})
export class UserEditDialogComponent {
  form: FormGroup;
  submitting = false;
  companies: { id: number; name: string }[] = [];
  roles = ['COMPANY_ADMIN', 'ACCOUNTANT', 'VIEWER'];
  newCompanyAssignments: { companyId: number; role: string }[] = [];
  existingAssignments: AdminUserCompanyAssignment[] = [];

  private fb = inject(FormBuilder);
  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);
  private dialogRef = inject(MatDialogRef<UserEditDialogComponent>);
  private data = inject(MAT_DIALOG_DATA) as UserEditDialogData;

  constructor() {
    this.form = this.fb.group({
      name: [this.data.user?.name ?? '', Validators.required],
      email: [this.data.user?.email ?? '', [Validators.required, Validators.email]],
      password: ['', this.data.mode === 'create' ? Validators.required : []],
    });
    if (this.data.user) {
      this.existingAssignments = this.data.user.companies;
    }
    this.loadCompanies();
  }

  get isCreate(): boolean {
    return this.data.mode === 'create';
  }

  loadCompanies(): void {
    this.adminService.listCompanies(0, 100).subscribe({
      next: (res) => { this.companies = res.content.map((c) => ({ id: c.id, name: c.nameEn })); },
      error: () => {},
    });
  }

  addCompanyAssignment(): void {
    this.newCompanyAssignments.push({ companyId: 0, role: 'ACCOUNTANT' });
  }

  removeCompanyAssignment(index: number): void {
    this.newCompanyAssignments.splice(index, 1);
  }

  removeExistingAssignment(companyId: number): void {
    if (!this.data.user) return;
    this.adminService.removeUserFromCompany(this.data.user.id, companyId).subscribe({
      next: () => {
        this.toast.success('Company assignment removed');
        this.existingAssignments = this.existingAssignments.filter((c) => c.companyId !== companyId);
      },
      error: () => this.toast.error('Failed to remove assignment'),
    });
  }

  save(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    const val = this.form.value;

    const obs$ = this.isCreate
      ? this.adminService.createUser({ name: val.name, email: val.email, password: val.password })
      : this.adminService.updateUser(this.data.user!.id, { name: val.name, email: val.email });

    obs$.subscribe({
      next: (user) => {
        const userId = this.isCreate ? user.id : this.data.user!.id;
        const assigns = this.newCompanyAssignments.filter((a) => a.companyId !== 0);
        let pending = assigns.length;

        if (pending === 0) {
          this.toast.success(this.isCreate ? 'User created' : 'User updated');
          this.dialogRef.close(true);
          return;
        }

        for (const a of assigns) {
          this.adminService.assignUserToCompany(userId, { companyId: a.companyId, role: a.role })
            .subscribe({
              next: () => { pending--; if (pending === 0) { this.toast.success('User saved'); this.dialogRef.close(true); } },
              error: () => { pending--; if (pending === 0) { this.toast.success('User saved (some assignments failed)'); this.dialogRef.close(true); } },
            });
        }
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to save user');
        this.submitting = false;
      },
    });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
