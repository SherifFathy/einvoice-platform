import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { AdminService, BranchResponse } from '../../shared/services/admin.service';
import { CompanyConfigService } from '../../shared/services/company-config.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-branches',
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
  ],
  templateUrl: './branches.component.html',
  styles: `
    .branch-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
    .form-actions { display: flex; gap: 12px; margin-top: 16px; }
    table { width: 100%; }
    .nav-actions { margin-top: 24px; display: flex; gap: 12px; }
    .address-section { margin-top: 16px; }
    .address-section h4 { margin-bottom: 8px; color: #666; }
  `,
})
export class BranchesComponent implements OnInit {
  companyId!: number;
  branches: BranchResponse[] = [];
  displayedColumns = ['nameEn', 'branchCode', 'city', 'isActive', 'actions'];
  form: FormGroup;
  submitting = false;
  loading = false;
  editingBranchId: number | null = null;
  isSuperAdmin = false;

  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private adminService = inject(AdminService);
  private configService = inject(CompanyConfigService);
  private toast = inject(ToastNotificationService);
  private authService = inject(AuthService);

  constructor() {
    this.form = this.fb.group({
      nameAr: ['', Validators.required],
      nameEn: ['', Validators.required],
      branchCode: ['', Validators.required],
      street: [''],
      buildingNumber: [''],
      additionalNumber: [''],
      city: [''],
      district: [''],
      postalCode: [''],
      countryCode: ['SA'],
      additionalStreet: [''],
    });
  }

  ngOnInit(): void {
    this.companyId = Number(this.route.snapshot.paramMap.get('companyId'));
    this.isSuperAdmin = this.authService.getCurrentUser()?.isSuperUser ?? false;
    this.loadBranches();
  }

  loadBranches(): void {
    this.loading = true;
    const service = this.isSuperAdmin ? this.adminService : this.configService;
    service.listBranches(this.companyId).subscribe({
      next: (branches) => { this.branches = branches; this.loading = false; },
      error: () => { this.toast.error('Failed to load branches'); this.loading = false; },
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;

    if (this.editingBranchId) {
      const update$ = this.isSuperAdmin
        ? this.adminService.updateBranch(this.companyId, this.editingBranchId, this.form.value)
        : this.configService.updateBranch(this.companyId, this.editingBranchId, this.form.value);
      update$.subscribe({
        next: () => {
          this.toast.success('Branch updated');
          this.cancelEdit();
          this.loadBranches();
        },
        error: () => { this.toast.error('Failed to update branch'); this.submitting = false; },
      });
    } else {
      const create$ = this.isSuperAdmin
        ? this.adminService.createBranch(this.companyId, this.form.value)
        : this.configService.createBranch(this.companyId, this.form.value);
      create$.subscribe({
        next: () => {
          this.toast.success('Branch created');
          this.form.reset({ countryCode: 'SA' });
          this.submitting = false;
          this.loadBranches();
        },
        error: () => { this.toast.error('Failed to create branch'); this.submitting = false; },
      });
    }
  }

  startEdit(branch: BranchResponse): void {
    this.editingBranchId = branch.id;
    this.form.patchValue({
      nameAr: branch.nameAr,
      nameEn: branch.nameEn,
      branchCode: branch.branchCode,
      street: branch.street,
      buildingNumber: branch.buildingNumber,
      additionalNumber: branch.additionalNumber,
      city: branch.city,
      district: branch.district,
      postalCode: branch.postalCode,
      countryCode: branch.countryCode || 'SA',
      additionalStreet: branch.additionalStreet,
    });
  }

  cancelEdit(): void {
    this.editingBranchId = null;
    this.form.reset({ countryCode: 'SA' });
    this.submitting = false;
  }

  deleteBranch(branch: BranchResponse): void {
    const delete$ = this.isSuperAdmin
      ? this.adminService.deleteBranch(this.companyId, branch.id)
      : this.configService.deactivateBranch(this.companyId, branch.id);
    delete$.subscribe({
      next: () => {
        this.toast.success('Branch deactivated');
        this.loadBranches();
      },
      error: () => this.toast.error('Failed to deactivate branch'),
    });
  }

  goToAuthorityConfig(branchId: number): void {
    this.router.navigate(['/config/company-create', this.companyId, 'branches', branchId, 'authority-config']);
  }

  goBack(): void {
    if (this.isSuperAdmin) {
      this.router.navigate(['/config/company-create']);
    } else {
      this.router.navigate(['/config']);
    }
  }
}
