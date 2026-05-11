import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Subject, takeUntil } from 'rxjs';
import { ZatcaCustomerService } from '../services/zatca-customer.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

function vatNumberValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (!value) return null;
  if (!/^3[0-9]{13}3$/.test(value)) {
    return { vatFormat: 'VAT number must be 15 digits starting and ending with 3' };
  }
  return null;
}

@Component({
  selector: 'app-zatca-customer-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatSelectModule, MatIconModule,
    MatSlideToggleModule, HasPermissionDirective,
  ],
  templateUrl: './zatca-customer-form.component.html',
  styleUrls: ['./zatca-customer-form.component.scss'],
})
export class ZatcaCustomerFormComponent implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private customerService = inject(ZatcaCustomerService);
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
  ];

  constructor() {
    this.form = this.fb.group({
      nameEn: ['', Validators.required],
      nameAr: [''],
      vatNumber: [''],
      customerType: ['', Validators.required],
      isActive: [true],
      addressData: this.fb.group({
        streetName: ['', Validators.required],
        buildingNumber: ['', Validators.required],
        city: ['', Validators.required],
        postalCode: ['', Validators.required],
        districtName: ['', Validators.required],
        country: ['', Validators.required],
      }),
    });
  }

  ngOnInit(): void {
    this.sessionCtx.context$.pipe(takeUntil(this.destroy$)).subscribe((ctx) => {
      if (ctx && ctx.companies.length > 0 && !this.selectedCompanyId) {
        this.selectedCompanyId = ctx.companies[0].companyId;
      }
    });

    this.form.get('customerType')!.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe((type) => {
        const vat = this.form.get('vatNumber')!;
        if (type === 'B') {
          vat.setValidators([Validators.required, vatNumberValidator]);
        } else {
          vat.clearValidators();
        }
        vat.updateValueAndValidity({ emitEvent: false });
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
          vatNumber: customer.vatNumber || '',
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
        this.router.navigate(['/customers/zatca']);
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
