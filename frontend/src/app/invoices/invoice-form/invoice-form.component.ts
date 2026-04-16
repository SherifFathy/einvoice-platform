import { Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDividerModule } from '@angular/material/divider';
import {
  InvoiceService, InvoiceDetailResponse, CreateInvoiceRequest, InvoiceLineRequest,
} from '../../shared/services/invoice.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-invoice-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatSelectModule, MatIconModule,
    MatDatepickerModule, MatNativeDateModule, MatDividerModule,
  ],
  templateUrl: './invoice-form.component.html',
  styles: `
    .form-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 16px; }
    .full-width { grid-column: 1 / -1; }
    .two-col { grid-column: span 2; }
    .form-actions { display: flex; gap: 12px; margin-top: 16px; justify-content: flex-end; }
    .line-row { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr 1fr 1fr 1fr auto; gap: 8px; align-items: center; margin-bottom: 8px; }
    .line-header { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr 1fr 1fr 1fr auto; gap: 8px; margin-bottom: 4px; font-weight: bold; }
    .totals-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 8px; max-width: 400px; margin-left: auto; margin-top: 16px; }
    .totals-grid .label { text-align: right; font-weight: 500; }
    .totals-grid .value { text-align: right; }
    .vat-section { margin-top: 16px; }
    h4 { margin: 12px 0 8px 0; }
  `,
})
export class InvoiceFormComponent implements OnChanges {
  private fb = inject(FormBuilder);
  private invoiceService = inject(InvoiceService);
  private toast = inject(ToastNotificationService);

  @Input() invoice: InvoiceDetailResponse | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  form: FormGroup;
  submitting = false;
  isEdit = false;

  calculatedTotals = {
    totalLineNet: 0,
    totalAllowances: 0,
    totalWithoutVat: 0,
    totalVat: 0,
    totalWithVat: 0,
    amountDue: 0,
  };

  vatBreakdown: { category: string; rate: number; taxable: number; tax: number }[] = [];

  constructor() {
    this.form = this.fb.group({
      type: ['TAX_INVOICE', Validators.required],
      issueDate: [this.todayAsString(), Validators.required],
      supplyDate: [null],
      supplyEndDate: [null],
      currency: ['SAR'],
      buyerId: [null],
      branchId: [null, Validators.required],
      authority: ['ZATCA', Validators.required],
      paymentMeansCode: ['10'],
      paymentTerms: [''],
      prepaidAmount: [0],
      totalAllowances: [0],
      originalInvoiceId: [null],
      notes: [''],
      lines: this.fb.array([]),
    });

    this.addLine();
    this.recalcTotals();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['invoice'] && this.invoice) {
      this.isEdit = true;
      this.form.patchValue({
        type: this.invoice.type,
        issueDate: this.invoice.issueDate,
        supplyDate: this.invoice.supplyDate,
        supplyEndDate: this.invoice.supplyEndDate,
        currency: this.invoice.currency,
        buyerId: this.invoice.buyerId,
        branchId: this.invoice.branchId,
        authority: this.invoice.authority,
        paymentMeansCode: this.invoice.paymentMeansCode,
        paymentTerms: this.invoice.paymentTerms,
        prepaidAmount: this.invoice.prepaidAmount,
        totalAllowances: this.invoice.totalAllowances,
        originalInvoiceId: this.invoice.originalInvoiceId,
        notes: this.invoice.notes,
      });

      const linesArray = this.form.get('lines') as FormArray;
      linesArray.clear();
      for (const line of this.invoice.lines) {
        linesArray.push(this.fb.group({
          itemId: [line.itemId],
          descriptionEn: [line.descriptionEn, Validators.required],
          quantity: [line.quantity, [Validators.required, Validators.min(0.01)]],
          unit: [line.unit, Validators.required],
          unitPrice: [line.unitPrice, [Validators.required, Validators.min(0.01)]],
          discountAmount: [line.discountAmount || 0],
          vatCategory: [line.vatCategory, Validators.required],
          vatRate: [line.vatRate, Validators.required],
          sortOrder: [line.sortOrder],
        }));
      }
      this.recalcTotals();
    } else if (changes['invoice'] && !this.invoice) {
      this.isEdit = false;
      this.form.reset({
        type: 'TAX_INVOICE',
        issueDate: this.todayAsString(),
        currency: 'SAR',
        authority: 'ZATCA',
        paymentMeansCode: '10',
        prepaidAmount: 0,
        totalAllowances: 0,
      });
      const linesArray = this.form.get('lines') as FormArray;
      linesArray.clear();
      this.addLine();
      this.recalcTotals();
    }
  }

  get linesArray(): FormArray {
    return this.form.get('lines') as FormArray;
  }

  addLine(): void {
    const sortOrder = this.linesArray.length + 1;
    this.linesArray.push(this.fb.group({
      itemId: [null],
      descriptionEn: ['', Validators.required],
      quantity: [1, [Validators.required, Validators.min(0.01)]],
      unit: ['EA', Validators.required],
      unitPrice: [0, [Validators.required, Validators.min(0.01)]],
      discountAmount: [0],
      vatCategory: ['S', Validators.required],
      vatRate: [15, Validators.required],
      sortOrder: [sortOrder],
    }));
  }

  removeLine(index: number): void {
    if (this.linesArray.length > 1) {
      this.linesArray.removeAt(index);
      this.recalcTotals();
    }
  }

  recalcTotals(): void {
    const lines = this.linesArray.value as any[];
    const totalAllowances = parseFloat(this.form.get('totalAllowances')?.value) || 0;
    const prepaidAmount = parseFloat(this.form.get('prepaidAmount')?.value) || 0;

    let totalLineNet = 0;
    const vatMap = new Map<string, { taxable: number; tax: number; rate: number }>();

    for (const line of lines) {
      const gross = (parseFloat(line.unitPrice) || 0) * (parseFloat(line.quantity) || 0);
      const discount = parseFloat(line.discountAmount) || 0;
      const lineNet = gross - discount;
      totalLineNet += lineNet;

      const vatRate = parseFloat(line.vatRate) || 0;
      const lineVat = lineNet * (vatRate / 100);
      const key = `${line.vatCategory}@${vatRate}`;
      const existing = vatMap.get(key);
      if (existing) {
        existing.taxable += lineNet;
        existing.tax += lineVat;
      } else {
        vatMap.set(key, { taxable: lineNet, tax: lineVat, rate: vatRate });
      }
    }

    const totalWithoutVat = totalLineNet - totalAllowances;
    let totalVat = 0;
    this.vatBreakdown = [];
    vatMap.forEach((val, key) => {
      totalVat += val.tax;
      this.vatBreakdown.push({
        category: key.split('@')[0],
        rate: val.rate,
        taxable: Math.round(val.taxable * 100) / 100,
        tax: Math.round(val.tax * 100) / 100,
      });
    });

    const totalWithVat = totalWithoutVat + totalVat;
    const amountDue = totalWithVat - prepaidAmount;

    this.calculatedTotals = {
      totalLineNet: Math.round(totalLineNet * 100) / 100,
      totalAllowances: Math.round(totalAllowances * 100) / 100,
      totalWithoutVat: Math.round(totalWithoutVat * 100) / 100,
      totalVat: Math.round(totalVat * 100) / 100,
      totalWithVat: Math.round(totalWithVat * 100) / 100,
      amountDue: Math.round(amountDue * 100) / 100,
    };
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;

    const formVal = this.form.value;
    const lines: InvoiceLineRequest[] = formVal.lines.map((l: any, i: number) => ({
      itemId: l.itemId || null,
      descriptionEn: l.descriptionEn,
      quantity: parseFloat(l.quantity),
      unit: l.unit,
      unitPrice: parseFloat(l.unitPrice),
      discountAmount: parseFloat(l.discountAmount) || 0,
      vatCategory: l.vatCategory,
      vatRate: parseFloat(l.vatRate),
      sortOrder: i + 1,
    }));

    const request: CreateInvoiceRequest = {
      type: formVal.type,
      issueDate: formVal.issueDate,
      supplyDate: formVal.supplyDate || null,
      supplyEndDate: formVal.supplyEndDate || null,
      currency: formVal.currency || 'SAR',
      buyerId: formVal.buyerId || null,
      branchId: formVal.branchId,
      authority: formVal.authority,
      paymentMeansCode: formVal.paymentMeansCode || null,
      paymentTerms: formVal.paymentTerms || null,
      prepaidAmount: parseFloat(formVal.prepaidAmount) || 0,
      totalAllowances: parseFloat(formVal.totalAllowances) || 0,
      originalInvoiceId: formVal.originalInvoiceId || null,
      notes: formVal.notes || null,
      lines,
    };

    const obs = this.isEdit && this.invoice
        ? this.invoiceService.update(this.invoice.id, request)
        : this.invoiceService.create(request);

    obs.subscribe({
      next: () => {
        this.toast.success(this.isEdit ? 'Invoice updated' : 'Invoice created');
        this.saved.emit();
        this.submitting = false;
      },
      error: (err) => {
        const errors = err.error?.errors;
        if (errors && Array.isArray(errors)) {
          this.toast.error(errors.map((e: any) => `${e.field}: ${e.message}`).join('; '));
        } else {
          this.toast.error(err.error?.error || 'Failed to save invoice');
        }
        this.submitting = false;
      },
    });
  }

  onCancel(): void {
    this.cancelled.emit();
  }

  private todayAsString(): string {
    return new Date().toISOString().split('T')[0];
  }
}
