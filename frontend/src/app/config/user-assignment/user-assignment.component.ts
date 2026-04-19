import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { AdminService, AssignUserResponse } from '../../shared/services/admin.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-user-assignment',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
  ],
  templateUrl: './user-assignment.component.html',
  styles: `
    .form-grid { max-width: 500px; }
    .form-actions { display: flex; gap: 12px; margin-top: 16px; }
    .nav-actions { margin-top: 24px; display: flex; gap: 12px; }
    .result-card { margin-top: 16px; max-width: 500px; }
  `,
})
export class UserAssignmentComponent implements OnInit {
  companyId!: number;
  form: FormGroup;
  submitting = false;
  lastResult: AssignUserResponse | null = null;

  roles = ['COMPANY_ADMIN', 'ACCOUNTANT', 'VIEWER'];

  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private adminService = inject(AdminService);
  private toast = inject(ToastNotificationService);

  constructor() {
    this.form = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      name: [''],
      role: ['COMPANY_ADMIN', Validators.required],
    });
  }

  ngOnInit(): void {
    this.companyId = Number(this.route.snapshot.paramMap.get('companyId'));
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    this.lastResult = null;
    this.adminService.assignUser(this.companyId, this.form.value).subscribe({
      next: (result) => {
        this.lastResult = result;
        this.toast.success(result.userCreated
            ? `New user created and assigned as ${result.role}`
            : `User assigned as ${result.role}`);
        this.form.reset({ role: 'COMPANY_ADMIN' });
        this.submitting = false;
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to assign user');
        this.submitting = false;
      },
    });
  }

  goBack(): void {
    this.router.navigate(['/config/company-create', this.companyId, 'branches']);
  }

  goToDashboard(): void {
    this.router.navigate(['/dashboard']);
  }
}
