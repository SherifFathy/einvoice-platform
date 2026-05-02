import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { AdminService, AdminUserResponse } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { UserEditDialogComponent } from './user-edit-dialog.component';
import { UserPermissionsDialogComponent } from './user-permissions-dialog.component';

@Component({
  selector: 'app-admin-users',
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatChipsModule,
  ],
  templateUrl: './admin-users.component.html',
  styles: `
    table { width: 100%; }
    .active-badge { color: #2e7d32; }
    .inactive-badge { color: #999; }
    .company-chip { margin: 2px; font-size: 11px; }
    .actions { display: flex; gap: 4px; }
  `,
})
export class AdminUsersComponent implements OnInit {
  users: AdminUserResponse[] = [];
  displayedColumns = ['name', 'email', 'status', 'companies', 'actions'];
  loading = false;

  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading = true;
    this.adminService.listUsers().subscribe({
      next: (users) => { this.users = users; this.loading = false; },
      error: () => { this.toast.error('Failed to load users'); this.loading = false; },
    });
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(UserEditDialogComponent, {
      width: '480px',
      disableClose: true,
      data: { mode: 'create' as const },
    });
    dialogRef.afterClosed().subscribe((result: boolean) => {
      if (result) this.loadUsers();
    });
  }

  openEditDialog(user: AdminUserResponse): void {
    const dialogRef = this.dialog.open(UserEditDialogComponent, {
      width: '480px',
      disableClose: true,
      data: { mode: 'edit' as const, user },
    });
    dialogRef.afterClosed().subscribe((result: boolean) => {
      if (result) this.loadUsers();
    });
  }

  toggleActive(user: AdminUserResponse): void {
    const action = user.isActive
        ? this.adminService.deactivateUser(user.id)
        : this.adminService.activateUser(user.id);
    action.subscribe({
      next: () => {
        this.toast.success(user.isActive ? 'User deactivated' : 'User activated');
        this.loadUsers();
      },
      error: () => this.toast.error('Failed to update user status'),
    });
  }

  deleteUser(user: AdminUserResponse): void {
    if (!confirm(`Delete user ${user.name}? This action cannot be undone.`)) return;
    this.adminService.deleteUser(user.id).subscribe({
      next: () => {
        this.toast.success('User deleted');
        this.loadUsers();
      },
      error: (err) => this.toast.error(err.error?.message || 'Failed to delete user'),
    });
  }

  openPermissionsDialog(user: AdminUserResponse): void {
    const dialogRef = this.dialog.open(UserPermissionsDialogComponent, {
      width: '700px',
      disableClose: true,
      data: { user },
    });
    dialogRef.afterClosed().subscribe((result: boolean) => {
      if (result) this.loadUsers();
    });
  }
}
