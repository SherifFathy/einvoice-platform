import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { AuthService } from '../../shared/services/auth.service';
import { CompanyConfigService, UserCompany } from '../../shared/services/company-config.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { PermissionMatrixComponent, PermissionMatrixData } from './permission-matrix/permission-matrix.component';

@Component({
  selector: 'app-users',
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatDialogModule,
  ],
  templateUrl: './users.component.html',
  styles: `
    table { width: 100%; }
    .env-chip { margin: 2px; font-size: 11px; }
    .active-badge { color: #2e7d32; }
    .inactive-badge { color: #999; }
  `,
})
export class UsersComponent implements OnInit {
  users: UserCompany[] = [];
  displayedColumns = ['name', 'email', 'role', 'environments', 'status', 'actions'];
  loading = false;
  companyId!: number;

  private authService = inject(AuthService);
  private companyService = inject(CompanyConfigService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);

  ngOnInit(): void {
    const companyId = this.authService.getActiveCompanyId();
    if (!companyId) return;
    this.companyId = companyId;
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading = true;
    this.companyService.listUsers(this.companyId).subscribe({
      next: (users) => { this.users = users; this.loading = false; },
      error: () => { this.toast.error('Failed to load users'); this.loading = false; },
    });
  }

  openPermissionMatrix(user: UserCompany): void {
    const dialogRef = this.dialog.open(PermissionMatrixComponent, {
      width: '500px',
      disableClose: true,
      data: { userId: user.id, userName: user.name, permittedEnvironments: user.permittedEnvironments } as PermissionMatrixData,
    });

    dialogRef.afterClosed().subscribe((result: boolean) => {
      if (result) this.loadUsers();
    });
  }

  toggleActive(user: UserCompany): void {
    const action = user.isActive
        ? this.companyService.deactivateUser(user.id)
        : this.companyService.activateUser(user.id);
    action.subscribe({
      next: () => {
        this.toast.success(user.isActive ? 'User deactivated' : 'User activated');
        this.loadUsers();
      },
      error: () => this.toast.error('Failed to update user status'),
    });
  }
}
