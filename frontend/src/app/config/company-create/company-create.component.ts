import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { AdminService, CompanyResponse } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { PageResponse } from '../../shared/components/data-table/data-table.component';

@Component({
  selector: 'app-company-create',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatTableModule,
    MatIconModule,
    MatPaginatorModule,
  ],
  templateUrl: './company-create.component.html',
  styles: `
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .full-width { grid-column: 1 / -1; }
    .form-actions { display: flex; gap: 12px; margin-top: 16px; justify-content: flex-end; }
    table { width: 100%; }
    .company-list { margin-bottom: 24px; }
    .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
  `,
})
export class CompanyCreateComponent {
  form: FormGroup;
  submitting = false;
  showForm = false;

  companies: CompanyResponse[] = [];
  totalCompanies = 0;
  displayedColumns = ['nameEn', 'nameAr', 'vatNumber', 'isActive', 'actions'];
  page = 0;
  pageSize = 10;

  private fb = inject(FormBuilder);
  private adminService = inject(AdminService);
  private router = inject(Router);
  private toast = inject(ToastNotificationService);

  constructor() {
    this.form = this.fb.group({
      nameAr: ['', Validators.required],
      nameEn: ['', Validators.required],
      vatNumber: ['', [Validators.required, Validators.minLength(10)]],
      crNumber: [''],
    });
    this.loadCompanies();
  }

  loadCompanies(): void {
    this.adminService.listCompanies(this.page, this.pageSize).subscribe({
      next: (res: PageResponse<CompanyResponse>) => {
        this.companies = res.content;
        this.totalCompanies = res.totalElements;
      },
      error: () => {},
    });
  }

  onPageChange(event: { pageIndex: number; pageSize: number }): void {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadCompanies();
  }

  showCreateForm(): void {
    this.showForm = true;
    this.form.reset();
  }

  cancelCreate(): void {
    this.showForm = false;
    this.form.reset();
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    this.adminService.createCompany(this.form.value).subscribe({
      next: (company) => {
        this.toast.success(`Company "${company.nameEn}" created`);
        this.showForm = false;
        this.form.reset();
        this.loadCompanies();
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to create company');
        this.submitting = false;
      },
    });
  }

  editCompany(company: CompanyResponse): void {
    this.router.navigate(['/config/company-create', company.id, 'edit']);
  }

  manageBranches(company: CompanyResponse): void {
    this.router.navigate(['/config/company-create', company.id, 'branches']);
  }
}
