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
import { AdminService, BranchResponse, CompanyResponse } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { BranchFormComponent, BranchFormDialogData } from './branch-form.component';

@Component({
  selector: 'app-branch-list',
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
  templateUrl: './branch-list.component.html',
  styles: `
    table { width: 100%; }
    .active-badge { color: #2e7d32; font-weight: 600; }
    .inactive-badge { color: #999; }
    .actions { display: flex; gap: 4px; }
    .toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
    .company-selector { min-width: 280px; }
  `,
})
export class BranchListComponent implements OnInit {
  companies: CompanyResponse[] = [];
  branches: BranchResponse[] = [];
  selectedCompanyId: string | null = null;
  displayedColumns = ['nameEn', 'nameAr', 'branchCode', 'city', 'status', 'actions'];
  loading = false;

  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);

  ngOnInit(): void {
    this.adminService.listCompanies(true).subscribe({
      next: (companies) => {
        this.companies = companies;
        if (companies.length > 0) {
          this.selectedCompanyId = companies[0].id;
          this.loadBranches();
        }
      },
      error: () => this.toast.error('Failed to load companies'),
    });
  }

  onCompanyChange(): void {
    this.loadBranches();
  }

  loadBranches(): void {
    if (!this.selectedCompanyId) return;
    this.loading = true;
    this.adminService.listBranches(this.selectedCompanyId).subscribe({
      next: (branches) => { this.branches = branches; this.loading = false; },
      error: () => { this.toast.error('Failed to load branches'); this.loading = false; },
    });
  }

  openCreateDialog(): void {
    if (!this.selectedCompanyId) return;
    const dialogRef = this.dialog.open(BranchFormComponent, {
      width: '560px',
      disableClose: true,
      data: {
        mode: 'create' as const,
        companyId: this.selectedCompanyId,
      } as BranchFormDialogData,
    });
    dialogRef.afterClosed().subscribe((result: boolean) => {
      if (result) this.loadBranches();
    });
  }

  openEditDialog(branch: BranchResponse): void {
    if (!this.selectedCompanyId) return;
    const dialogRef = this.dialog.open(BranchFormComponent, {
      width: '560px',
      disableClose: true,
      data: {
        mode: 'edit' as const,
        companyId: this.selectedCompanyId,
        branch,
      } as BranchFormDialogData,
    });
    dialogRef.afterClosed().subscribe((result: boolean) => {
      if (result) this.loadBranches();
    });
  }
}
