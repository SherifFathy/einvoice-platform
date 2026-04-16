import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { CompanyConfigService } from '../../../shared/services/company-config.service';
import { ToastNotificationService } from '../../../shared/services/toast.service';

export interface PermissionMatrixData {
  userId: number;
  userName: string;
  permittedEnvironments: string[];
}

@Component({
  selector: 'app-permission-matrix',
  imports: [
    CommonModule,
    MatDialogModule,
    MatCheckboxModule,
    MatButtonModule,
    MatListModule,
  ],
  templateUrl: './permission-matrix.component.html',
  styles: `
    .env-list { display: flex; flex-direction: column; gap: 8px; }
    .dialog-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px; }
  `,
})
export class PermissionMatrixComponent implements OnInit {
  environments: string[] = [];
  selected: Set<string>;
  submitting = false;

  private companyService = inject(CompanyConfigService);
  private toast = inject(ToastNotificationService);
  private dialogRef = inject(MatDialogRef<PermissionMatrixComponent>);
  private dialogData = inject(MAT_DIALOG_DATA) as PermissionMatrixData;

  constructor() {
    this.selected = new Set(this.dialogData.permittedEnvironments);
  }

  get data(): PermissionMatrixData {
    return this.dialogData;
  }

  ngOnInit(): void {
    this.companyService.listEnvironments().subscribe({
      next: (envs) => { this.environments = envs; },
      error: () => { this.environments = []; },
    });
  }

  toggle(env: string): void {
    if (this.selected.has(env)) {
      this.selected.delete(env);
    } else {
      this.selected.add(env);
    }
  }

  save(): void {
    this.submitting = true;
    this.companyService.assignPermissions(this.data.userId, {
      environments: Array.from(this.selected),
    }).subscribe({
      next: () => {
        this.toast.success('Permissions updated');
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.toast.error(err.error?.message || 'Failed to update permissions');
        this.submitting = false;
      },
    });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
