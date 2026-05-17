import { Component, inject, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormBuilder, Validators, FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { CustomerService, CustomerResponse } from '../../../shared/services/customer.service';
import { InvoiceService, InvoiceListResponse } from '../../../shared/services/invoice.service';
import { ToastNotificationService } from '../../../shared/services/toast.service';

@Component({
  selector: 'app-buyer-step',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatSelectModule, MatIconModule, MatDialogModule, MatListModule,
  ],
  templateUrl: './buyer-step.component.html',
  styles: `
    .buyer-section { margin-bottom: 16px; }
    .buyer-search { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 16px; }
    .buyer-search mat-form-field { flex: 1; }
    .selected-buyer { padding: 12px; border: 1px solid #e0e0e0; border-radius: 8px; margin-bottom: 16px; }
    .selected-buyer .name { font-weight: 500; font-size: 1.1em; }
    .selected-buyer .detail { color: #666; margin-top: 4px; }
    .reference-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 16px; }
    .reference-hint { font-size: 0.85em; color: #666; margin-top: 4px; }
    .inline-create { padding: 16px; border: 1px dashed #ccc; border-radius: 8px; margin-top: 16px; }
    .inline-create h4 { margin: 0 0 12px 0; }
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  `,
})
export class BuyerStepComponent implements OnChanges {
  @Input() form!: FormGroup;
  @Input() authority: string = 'ZATCA';
  @Input() invoiceType: string = 'TAX_INVOICE';
  @Input() subtypeFlags: Record<string, boolean> | null = null;

  private customerService = inject(CustomerService);
  private invoiceService = inject(InvoiceService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);
  private fb = inject(FormBuilder);

  customers: CustomerResponse[] = [];
  selectedCustomer: CustomerResponse | null = null;
  searchTerm = '';
  invoices: InvoiceListResponse[] = [];
  invoiceSearchTerm = '';
  isB2b = false;
  showInlineCreate = false;

  inlineForm: FormGroup = this.fb.group({
    nameEn: ['', Validators.required],
    nameAr: [''],
    vatNumber: [''],
    customerType: ['B2C', Validators.required],
    countryCode: ['SA'],
    idType: ['CR'],
    idValue: [''],
  });

  get isCreditOrDebitNote(): boolean {
    return this.invoiceType === 'CREDIT_NOTE' || this.invoiceType === 'DEBIT_NOTE';
  }

  get needsBuyer(): boolean {
    if (this.authority === 'ZATCA') {
      return this.invoiceType === 'TAX_INVOICE'
        || this.invoiceType === 'CREDIT_NOTE'
        || this.invoiceType === 'DEBIT_NOTE';
    }
    return true;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['invoiceType'] || changes['subtypeFlags']) {
      this.updateB2bStatus();
    }
    if (changes['authority']) {
      this.inlineForm.patchValue({
        countryCode: this.authority === 'ZATCA' ? 'SA' : 'EG',
      });
    }
  }

  searchCustomers(): void {
    if (!this.searchTerm || this.searchTerm.length < 2) return;
    this.customerService.list(0, 20, this.searchTerm).subscribe({
      next: (res) => this.customers = res.content,
    });
  }

  selectCustomer(customer: CustomerResponse): void {
    this.selectedCustomer = customer;
    this.form.patchValue({ buyerId: customer.id });
    this.isB2b = customer.customerType === 'B2B';
    this.customers = [];
    this.searchTerm = '';
  }

  clearCustomer(): void {
    this.selectedCustomer = null;
    this.form.patchValue({ buyerId: null });
    this.isB2b = false;
  }

  toggleInlineCreate(): void {
    this.showInlineCreate = !this.showInlineCreate;
  }

  createCustomerInline(): void {
    if (this.inlineForm.invalid) return;
    this.customerService.create(this.inlineForm.value).subscribe({
      next: (customer) => {
        this.toast.success('Customer created');
        this.selectCustomer(customer);
        this.showInlineCreate = false;
        this.inlineForm.reset({ customerType: 'B2C', countryCode: this.authority === 'ZATCA' ? 'SA' : 'EG' });
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to create customer');
      },
    });
  }

  getFieldError(controlName: string): string | null {
    const control = this.form?.get(controlName);
    if (!control || !control.errors || !control.touched) return null;
    if (control.errors['required']) return 'This field is required';
    return null;
  }

  private updateB2bStatus(): void {
    if (this.selectedCustomer) {
      this.isB2b = this.selectedCustomer.customerType === 'B2B';
    } else if (this.subtypeFlags) {
      this.isB2b = !this.subtypeFlags['summary'];
    }
  }

  searchInvoices(): void {
    if (!this.invoiceSearchTerm || this.invoiceSearchTerm.length < 2) return;
    this.invoiceService.list(0, 10, undefined, undefined, undefined, undefined, this.invoiceSearchTerm).subscribe({
      next: (res) => this.invoices = res.content,
    });
  }

  selectInvoice(invoice: InvoiceListResponse): void {
    this.form.patchValue({ originalInvoiceId: invoice.id });
    this.invoices = [];
    this.invoiceSearchTerm = '';
  }

  clearInvoice(): void {
    this.form.patchValue({ originalInvoiceId: null });
    this.invoiceSearchTerm = '';
  }
}
