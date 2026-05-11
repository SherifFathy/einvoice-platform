import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Subject, takeUntil } from 'rxjs';
import { EtaCustomerService } from '../services/eta-customer.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-eta-customer-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatSelectModule, MatIconModule,
    MatSlideToggleModule, HasPermissionDirective,
  ],
  templateUrl: './eta-customer-form.component.html',
  styleUrls: ['./eta-customer-form.component.scss'],
})
export class EtaCustomerFormComponent implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private customerService = inject(EtaCustomerService);
  private sessionCtx = inject(SessionContextService);
  private toast = inject(ToastNotificationService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroy$ = new Subject<void>();

  form: FormGroup;
  submitting = false;
  isEdit = false;
  customerId: string | null = null;
  selectedCompanyId = '';

  customerTypes = [
    { value: 'B', label: 'Business (B)' },
    { value: 'P', label: 'Person (P)' },
    { value: 'F', label: 'Foreign (F)' },
  ];

  constructor() {
    this.form = this.fb.group({
      nameEn: ['', Validators.required],
      nameAr: [''],
      taxNumber: [''],
      customerType: ['', Validators.required],
      isActive: [true],
      addressData: this.fb.group({
        country: ['', Validators.required],
        governorate: ['', Validators.required],
        regionCity: ['', Validators.required],
        street: ['', Validators.required],
        buildingNumber: ['', Validators.required],
      }),
    });
  }

  ngOnInit(): void {
    this.sessionCtx.context$.pipe(takeUntil(this.destroy$)).subscribe((ctx) => {
      if (ctx && ctx.companies.length > 0 && !this.selectedCompanyId) {
        this.selectedCompanyId = ctx.companies[0].companyId;
      }
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.customerId = id;
      this.loadCustomer();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadCustomer(): void {
    if (!this.customerId || !this.selectedCompanyId) return;
    this.customerService.get(this.selectedCompanyId, this.customerId).subscribe({
      next: (customer) => {
        this.form.patchValue({
          nameEn: customer.nameEn,
          nameAr: customer.nameAr || '',
          taxNumber: customer.taxNumber || '',
          customerType: customer.customerType,
          isActive: customer.isActive,
          addressData: customer.addressData ?? {},
        });
      },
      error: (err) => this.toast.error(err.error?.error || 'Failed to load customer'),
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    const payload = this.form.value;

    const obs = this.isEdit && this.customerId
      ? this.customerService.update(this.selectedCompanyId, this.customerId, payload)
      : this.customerService.create(this.selectedCompanyId, payload);

    obs.subscribe({
      next: () => {
        this.toast.success(this.isEdit ? 'Customer updated' : 'Customer created');
        this.submitting = false;
        this.router.navigate(['/customers/eta']);
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to save customer');
        this.submitting = false;
      },
    });
  }

  onCancel(): void {
    history.back();
  }
}
