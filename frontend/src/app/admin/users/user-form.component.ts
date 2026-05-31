import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { AdminService, UserResponse } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

export interface UserFormDialogData {
  mode: 'create' | 'edit';
  user?: UserResponse;
}

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCheckboxModule,
  ],
  templateUrl: './user-form.component.html',
  styles: `
    .dialog-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px; }
    .form-error { color: #c62828; font-size: 12px; margin-top: 4px; }
    form { display: flex; flex-direction: column; gap: 4px; }
    .password-hint { font-size: 11px; color: #888; margin-top: -8px; margin-bottom: 8px; }
  `,
})
export class UserFormComponent {
  form: FormGroup;
  submitting = false;
  errorMessage: string | null = null;

  private fb = inject(FormBuilder);
  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);
  private dialogRef = inject(MatDialogRef<UserFormComponent>);
  private data = inject(MAT_DIALOG_DATA) as UserFormDialogData;

  constructor() {
    const user = this.data.user;
    this.form = this.fb.group({
      name: [user?.name ?? '', Validators.required],
      email: [user?.email ?? '', [Validators.required, Validators.email]],
      password: ['', this.data.mode === 'create' ? Validators.required : []],
      isSuperUser: [user?.isSuperUser ?? false],
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

    if (this.isCreate) {
      const request = {
        name: val.name,
        email: val.email,
        password: val.password,
        isSuperUser: val.isSuperUser || undefined,
      };
      this.adminService.createUser(request).subscribe({
        next: () => {
          this.toast.success('User created');
          this.dialogRef.close(true);
        },
        error: (err) => this.handleError(err),
      });
    } else {
      const request: Record<string, unknown> = {};
      if (val.name !== this.data.user!.name) request['name'] = val.name;
      if (val.email !== this.data.user!.email) request['email'] = val.email;
      if (val.password) request['password'] = val.password;
      if (val.isSuperUser !== this.data.user?.['isSuperUser']) request['isSuperUser'] = val.isSuperUser;

      this.adminService.updateUser(this.data.user!.id, request).subscribe({
        next: () => {
          this.toast.success('User updated');
          this.dialogRef.close(true);
        },
        error: (err) => this.handleError(err),
      });
    }
  }

  private handleError(err: unknown): void {
    const errorObj = err as { error?: { code?: string; message?: string } };
    const code = errorObj.error?.code;
    if (code === 'EMAIL_ALREADY_EXISTS') {
      this.errorMessage = 'A user with this email already exists.';
    } else if (code === 'LAST_SUPER_USER_PROTECTED') {
      this.errorMessage = 'Cannot remove super user status from the last super user.';
    } else if (code === 'VALIDATION_ERROR') {
      this.errorMessage = errorObj.error?.message || 'Validation failed';
    } else {
      this.errorMessage = errorObj.error?.message || 'Failed to save user';
    }
    this.submitting = false;
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
