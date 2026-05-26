import { Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { CustomerService, CustomerResponse } from '../../shared/services/customer.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-customer-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatSelectModule, MatIconModule,
  ],
  templateUrl: './customer-form.component.html',
  styles: `
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .full-width { grid-column: 1 / -1; }
    .form-actions { display: flex; gap: 12px; margin-top: 16px; justify-content: flex-end; }
  `,
})
export class CustomerFormComponent implements OnChanges {
  private fb = inject(FormBuilder);
  private customerService = inject(CustomerService);
  private toast = inject(ToastNotificationService);

  @Input() customer: CustomerResponse | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  form: FormGroup;
  submitting = false;
  isEdit = false;

  constructor() {
    this.form = this.fb.group({
      nameEn: ['', Validators.required],
      nameAr: [''],
      vatNumber: [''],
      customerType: ['B2B', Validators.required],
      idType: [''],
      idValue: [''],
      street: [''],
      buildingNumber: [''],
      city: [''],
      district: [''],
      postalCode: [''],
      countryCode: ['SA'],
      contactEmail: ['', Validators.email],
      contactPhone: [''],
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['customer'] && this.customer) {
      this.isEdit = true;
      this.form.patchValue({
        nameEn: this.customer.nameEn,
        nameAr: this.customer.nameAr || '',
        vatNumber: this.customer.vatNumber || '',
        customerType: this.customer.customerType,
        idType: this.customer.idType || '',
        idValue: this.customer.idValue || '',
        street: this.customer.street || '',
        buildingNumber: this.customer.buildingNumber || '',
        city: this.customer.city || '',
        district: this.customer.district || '',
        postalCode: this.customer.postalCode || '',
        countryCode: this.customer.countryCode || 'SA',
        contactEmail: this.customer.contactEmail || '',
        contactPhone: this.customer.contactPhone || '',
      });
    } else if (changes['customer'] && !this.customer) {
      this.isEdit = false;
      this.form.reset({ customerType: 'B2B', countryCode: 'SA' });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    const request = this.form.value;
    const obs = this.isEdit && this.customer
        ? this.customerService.update(this.customer.id, request)
        : this.customerService.create(request);
    obs.subscribe({
      next: () => {
        this.toast.success(this.isEdit ? 'Customer updated' : 'Customer created');
        this.saved.emit();
        this.form.reset({ customerType: 'B2B', countryCode: 'SA' });
        this.isEdit = false;
        this.submitting = false;
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to save customer');
        this.submitting = false;
      },
    });
  }

  onCancel(): void {
    this.cancelled.emit();
    this.form.reset({ customerType: 'B2B', countryCode: 'SA' });
    this.isEdit = false;
  }
}
