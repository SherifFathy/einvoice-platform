import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { AdminService, UserResponse } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { UserFormComponent, UserFormDialogData } from './user-form.component';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatSlideToggleModule,
    MatDialogModule,
    MatTooltipModule,
    MatChipsModule,
  ],
  templateUrl: './user-list.component.html',
  styles: `
    table { width: 100%; }
    .active-badge { color: #2e7d32; font-weight: 600; }
    .inactive-badge { color: #999; }
    .super-badge { color: #1565c0; font-weight: 600; }
    .actions { display: flex; gap: 4px; }
    .toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
  `,
})
export class UserListComponent implements OnInit {
  users: UserResponse[] = [];
  displayedColumns = ['name', 'email', 'isSuperUser', 'status', 'actions'];
  loading = false;
  includeInactive = false;

  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading = true;
    this.adminService.listUsers(this.includeInactive).subscribe({
      next: (users) => { this.users = users; this.loading = false; },
      error: () => { this.toast.error('Failed to load users'); this.loading = false; },
    });
  }

  onIncludeInactiveToggle(): void {
    this.includeInactive = !this.includeInactive;
    this.loadUsers();
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(UserFormComponent, {
      width: '480px',
      disableClose: true,
      data: { mode: 'create' as const } as UserFormDialogData,
    });
    dialogRef.afterClosed().subscribe((result: boolean) => {
      if (result) this.loadUsers();
    });
  }

  openEditDialog(user: UserResponse): void {
    const dialogRef = this.dialog.open(UserFormComponent, {
      width: '480px',
      disableClose: true,
      data: { mode: 'edit' as const, user } as UserFormDialogData,
    });
    dialogRef.afterClosed().subscribe((result: boolean) => {
      if (result) this.loadUsers();
    });
  }

  toggleActive(user: UserResponse): void {
    const isActive = user.isActive;
    const actionLabel = isActive ? 'Deactivate' : 'Activate';

    if (isActive) {
      const dialogRef = this.dialog.open(ConfirmDialogComponent, {
        width: '400px',
        data: {
          title: `${actionLabel} User`,
          message: `Are you sure you want to ${actionLabel.toLowerCase()} "${user.name}"?`,
          confirmLabel: actionLabel,
        } as ConfirmDialogData,
      });
      dialogRef.afterClosed().subscribe((confirmed: boolean) => {
        if (!confirmed) return;
        this.performToggle(user);
      });
    } else {
      this.performToggle(user);
    }
  }

  private performToggle(user: UserResponse): void {
    const action = user.isActive
      ? this.adminService.deactivateUser(user.id)
      : this.adminService.activateUser(user.id);

    action.subscribe({
      next: () => {
        this.toast.success(user.isActive ? 'User deactivated' : 'User activated');
        this.loadUsers();
      },
      error: (err) => {
        const code = err.error?.code;
        if (code === 'LAST_SUPER_USER_PROTECTED') {
          this.toast.error('Cannot deactivate the last super user');
        } else {
          this.toast.error('Failed to update user status');
        }
      },
    });
  }
}
