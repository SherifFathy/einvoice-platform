import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { AdminService, AdminUserResponse, AdminUserCompanyAssignment } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

const ALL_PERMISSIONS = [
  'CREATE_INVOICE', 'CREATE_CUSTOMER', 'CREATE_ITEM',
  'EDIT_INVOICE', 'EDIT_CUSTOMER', 'EDIT_ITEM',
  'DELETE_INVOICE', 'DELETE_CUSTOMER', 'DELETE_ITEM',
  'TRANSFER_INVOICE', 'REFRESH_INVOICE',
  'VIEW_INVOICE_LIST', 'VIEW_CUSTOMER_LIST', 'VIEW_ITEM_LIST',
];

const LOV_CONTEXTS = [
  { id: 1, label: 'ZATCA · Invoice · Sandbox' },
  { id: 2, label: 'ZATCA · Invoice · Simulation' },
  { id: 3, label: 'ZATCA · Invoice · Production' },
  { id: 4, label: 'ETA · Invoice · Preprod' },
  { id: 5, label: 'ETA · Invoice · Production' },
  { id: 6, label: 'ETA · Receipt · Preprod' },
  { id: 7, label: 'ETA · Receipt · Production' },
];

export interface UserPermissionsDialogData {
  user: AdminUserResponse;
}

@Component({
  selector: 'app-user-permissions-dialog',
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatCheckboxModule,
    MatButtonModule,
    MatSelectModule,
    MatFormFieldModule,
  ],
  templateUrl: './user-permissions-dialog.component.html',
  styles: `
    .dialog-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px; }
    .perm-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; margin-top: 12px; }
    .context-selector { margin-bottom: 12px; }
    .company-selector { margin-bottom: 12px; }
  `,
})
export class UserPermissionsDialogComponent implements OnInit {
  allPermissions = ALL_PERMISSIONS;
  lovContexts = LOV_CONTEXTS;
  selected: Set<string> = new Set();
  selectedCompanyId: number | null = null;
  selectedLovContextId: number | null = null;
  submitting = false;
  loading = false;

  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);
  private dialogRef = inject(MatDialogRef<UserPermissionsDialogComponent>);
  private data = inject(MAT_DIALOG_DATA) as UserPermissionsDialogData;

  get user(): AdminUserResponse {
    return this.data.user;
  }

  get companies(): AdminUserCompanyAssignment[] {
    return this.user.companies;
  }

  ngOnInit(): void {
    if (this.companies.length > 0) {
      this.selectedCompanyId = this.companies[0].companyId;
    }
    if (this.lovContexts.length > 0) {
      this.selectedLovContextId = this.lovContexts[0].id;
    }
    this.loadPermissions();
  }

  loadPermissions(): void {
    if (!this.selectedCompanyId || !this.selectedLovContextId) return;
    this.loading = true;
    this.adminService.getUserPermissions(this.user.id, this.selectedCompanyId, this.selectedLovContextId)
      .subscribe({
        next: (perms) => { this.selected = new Set(perms); this.loading = false; },
        error: () => { this.selected = new Set(); this.loading = false; },
      });
  }

  toggle(perm: string): void {
    if (this.selected.has(perm)) {
      this.selected.delete(perm);
    } else {
      this.selected.add(perm);
    }
  }

  selectAll(): void {
    this.selected = new Set(ALL_PERMISSIONS);
  }

  selectNone(): void {
    this.selected = new Set();
  }

  save(): void {
    if (!this.selectedCompanyId || !this.selectedLovContextId) {
      this.toast.error('Select a company and context');
      return;
    }
    this.submitting = true;
    this.adminService.bulkSetPermissions(this.user.id, {
      companyId: this.selectedCompanyId,
      lovContextId: this.selectedLovContextId,
      permissions: Array.from(this.selected),
    }).subscribe({
      next: () => {
        this.toast.success('Permissions updated');
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to update permissions');
        this.submitting = false;
      },
    });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
