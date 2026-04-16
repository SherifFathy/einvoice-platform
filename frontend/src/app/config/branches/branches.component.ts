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
import { CompanyConfigService } from '../../shared/services/company-config.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

export interface BranchResponse {
  id: number;
  companyId: number;
  nameAr: string;
  nameEn: string;
  branchCode: string;
  isActive: boolean;
  createdAt: string;
}

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
  `,
})
export class BranchesComponent implements OnInit {
  companyId!: number;
  branches: BranchResponse[] = [];
  displayedColumns = ['nameAr', 'nameEn', 'branchCode', 'isActive', 'actions'];
  form: FormGroup;
  submitting = false;
  loading = false;
  editingBranchId: number | null = null;

  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private configService = inject(CompanyConfigService);
  private toast = inject(ToastNotificationService);

  constructor() {
    this.form = this.fb.group({
      nameAr: ['', Validators.required],
      nameEn: ['', Validators.required],
      branchCode: ['', Validators.required],
    });
  }

  ngOnInit(): void {
    this.companyId = Number(this.route.snapshot.paramMap.get('companyId'));
    this.loadBranches();
  }

  loadBranches(): void {
    this.loading = true;
    this.configService.listBranches(this.companyId).subscribe({
      next: (branches) => { this.branches = branches; this.loading = false; },
      error: () => { this.toast.error('Failed to load branches'); this.loading = false; },
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;

    if (this.editingBranchId) {
      this.configService.updateBranch(this.companyId, this.editingBranchId, this.form.value).subscribe({
        next: () => {
          this.toast.success('Branch updated');
          this.cancelEdit();
          this.loadBranches();
        },
        error: () => { this.toast.error('Failed to update branch'); this.submitting = false; },
      });
    } else {
      this.configService.createBranch(this.companyId, this.form.value).subscribe({
        next: () => {
          this.toast.success('Branch created');
          this.form.reset();
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
    });
  }

  cancelEdit(): void {
    this.editingBranchId = null;
    this.form.reset();
    this.submitting = false;
  }

  deleteBranch(branch: BranchResponse): void {
    this.configService.deactivateBranch(this.companyId, branch.id).subscribe({
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

  goToAssignUser(): void {
    this.router.navigate(['/config/company-create', this.companyId, 'assign-user']);
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }
}
