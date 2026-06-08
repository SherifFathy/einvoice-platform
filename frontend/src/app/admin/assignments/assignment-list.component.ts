import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AdminService, AssignmentResponse, UserResponse, CompanyResponse } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { AssignmentFormComponent, AssignmentFormDialogData } from './assignment-form.component';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-assignment-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatSelectModule,
    MatFormFieldModule,
    MatTooltipModule,
  ],
  templateUrl: './assignment-list.component.html',
  styles: `
    table { width: 100%; }
    .actions { display: flex; gap: 4px; }
    .toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
    .user-selector { min-width: 300px; }
    .active-badge { color: #2e7d32; font-weight: 600; }
  `,
})
export class AssignmentListComponent implements OnInit {
  users: UserResponse[] = [];
  companies: CompanyResponse[] = [];
  assignments: AssignmentResponse[] = [];
  selectedUserId: string | null = null;
  displayedColumns = ['company', 'authorityEnv', 'transactionType', 'roleCode', 'isActive', 'actions'];
  loading = false;

  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);

  ngOnInit(): void {
    this.adminService.listUsers(true).subscribe({
      next: (users) => {
        this.users = users;
        if (users.length > 0) {
          this.selectedUserId = users[0].id;
          this.loadAssignments();
        }
      },
      error: () => this.toast.error('Failed to load users'),
    });
    this.adminService.listCompanies(true).subscribe({
      next: (companies) => { this.companies = companies; },
      error: () => {},
    });
  }

  getCompanyName(companyId: string): string {
    const company = this.companies.find((c) => c.id === companyId);
    return company ? company.nameEn : companyId;
  }

  onUserChange(): void {
    this.loadAssignments();
  }

  loadAssignments(): void {
    if (!this.selectedUserId) return;
    this.loading = true;
    this.adminService.listAssignments(this.selectedUserId).subscribe({
      next: (assignments) => { this.assignments = assignments; this.loading = false; },
      error: () => { this.toast.error('Failed to load assignments'); this.loading = false; },
    });
  }

  openCreateDialog(): void {
    if (!this.selectedUserId) return;
    const dialogRef = this.dialog.open(AssignmentFormComponent, {
      width: '480px',
      disableClose: true,
      data: {
        userId: this.selectedUserId,
      } as AssignmentFormDialogData,
    });
    dialogRef.afterClosed().subscribe((result: boolean) => {
      if (result) this.loadAssignments();
    });
  }

  deleteAssignment(assignment: AssignmentResponse): void {
    if (!this.selectedUserId) return;
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Delete Assignment',
        message: `Are you sure you want to delete this assignment?`,
        confirmLabel: 'Delete',
      } as ConfirmDialogData,
    });
    dialogRef.afterClosed().subscribe((confirmed: boolean) => {
      if (!confirmed) return;
      this.adminService.deleteAssignment(this.selectedUserId!, assignment.id).subscribe({
        next: () => {
          this.toast.success('Assignment deleted');
          this.loadAssignments();
        },
        error: () => this.toast.error('Failed to delete assignment'),
      });
    });
  }
}
