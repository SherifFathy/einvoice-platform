import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AdminService, CompanyResponse } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { CompanyFormComponent } from './company-form.component';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-company-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatSlideToggleModule,
    MatDialogModule,
    MatTooltipModule,
  ],
  templateUrl: './company-list.component.html',
  styles: `
    table { width: 100%; }
    .active-badge { color: #2e7d32; font-weight: 600; }
    .inactive-badge { color: #999; }
    .actions { display: flex; gap: 4px; }
    .toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
  `,
})
export class CompanyListComponent implements OnInit {
  companies: CompanyResponse[] = [];
  displayedColumns = ['nameEn', 'nameAr', 'taxNumber', 'crNumber', 'status', 'actions'];
  loading = false;
  includeInactive = false;

  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);
  private sessionCtx = inject(SessionContextService);

  /**
   * Reloads the session context so dependent screens (the dashboard and the
   * configuration company switcher) immediately reflect company changes without
   * requiring a re-login. The backend session cache is invalidated server-side
   * on the same mutations.
   */
  private refreshSession(): void {
    this.sessionCtx.loadContext().subscribe({ error: () => {} });
  }

  ngOnInit(): void {
    this.loadCompanies();
  }

  loadCompanies(): void {
    this.loading = true;
    this.adminService.listCompanies(this.includeInactive).subscribe({
      next: (companies) => { this.companies = companies; this.loading = false; },
      error: () => { this.toast.error('Failed to load companies'); this.loading = false; },
    });
  }

  onIncludeInactiveToggle(): void {
    this.includeInactive = !this.includeInactive;
    this.loadCompanies();
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(CompanyFormComponent, {
      width: '520px',
      disableClose: true,
      data: { mode: 'create' as const },
    });
    dialogRef.afterClosed().subscribe((result: boolean) => {
      if (result) {
        this.loadCompanies();
        this.refreshSession();
      }
    });
  }

  openEditDialog(company: CompanyResponse): void {
    const dialogRef = this.dialog.open(CompanyFormComponent, {
      width: '520px',
      disableClose: true,
      data: { mode: 'edit' as const, company },
    });
    dialogRef.afterClosed().subscribe((result: boolean) => {
      if (result) {
        this.loadCompanies();
        this.refreshSession();
      }
    });
  }

  deactivateCompany(company: CompanyResponse): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Deactivate Company',
        message: `Are you sure you want to deactivate "${company.nameEn}"?`,
        confirmLabel: 'Deactivate',
      } as ConfirmDialogData,
    });
    dialogRef.afterClosed().subscribe((confirmed: boolean) => {
      if (!confirmed) return;
      this.adminService.deactivateCompany(company.id).subscribe({
        next: () => {
          this.toast.success('Company deactivated');
          this.loadCompanies();
          this.refreshSession();
        },
        error: (err) => this.toast.error(err.error?.message || 'Failed to deactivate company'),
      });
    });
  }
}
