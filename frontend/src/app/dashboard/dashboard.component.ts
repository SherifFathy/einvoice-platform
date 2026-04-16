import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { AdminService, CompanyResponse } from '../shared/services/admin.service';
import { AuthService } from '../shared/services/auth.service';
import { ToastNotificationService } from '../shared/services/toast.service';

@Component({
  selector: 'app-dashboard',
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatPaginatorModule,
  ],
  templateUrl: './dashboard.component.html',
  styles: `
    .dashboard-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .company-actions { display: flex; gap: 8px; }
    .status-active { color: #4caf50; font-weight: 600; }
    .status-inactive { color: #f44336; font-weight: 600; }
    mat-card { margin-bottom: 16px; }
    table { width: 100%; }
  `,
})
export class DashboardComponent implements OnInit {
  companies: CompanyResponse[] = [];
  totalElements = 0;
  pageSize = 20;
  pageIndex = 0;
  loading = false;
  isSuperAdmin = false;

  displayedColumns = ['nameEn', 'vatNumber', 'city', 'isActive', 'createdAt', 'actions'];

  private adminService = inject(AdminService);
  private authService = inject(AuthService);
  private toast = inject(ToastNotificationService);

  ngOnInit(): void {
    this.isSuperAdmin = this.authService.getCurrentRole() === 'SUPER_ADMIN';
    if (this.isSuperAdmin) {
      this.loadCompanies();
    }
  }

  loadCompanies(): void {
    this.loading = true;
    this.adminService.listCompanies(this.pageIndex, this.pageSize).subscribe({
      next: (response) => {
        this.companies = response.content;
        this.totalElements = response.totalElements;
        this.loading = false;
      },
      error: () => {
        this.toast.error('Failed to load companies');
        this.loading = false;
      },
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadCompanies();
  }

  toggleCompanyStatus(company: CompanyResponse): void {
    const action = company.isActive
        ? this.adminService.deactivateCompany(company.id)
        : this.adminService.activateCompany(company.id);
    action.subscribe({
      next: () => {
        this.toast.success(`Company ${company.isActive ? 'deactivated' : 'activated'}`);
        this.loadCompanies();
      },
      error: () => this.toast.error('Failed to update company status'),
    });
  }
}
