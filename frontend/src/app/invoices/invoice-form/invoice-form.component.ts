import { Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { firstValueFrom } from 'rxjs';
import {
  InvoiceService, InvoiceDetailResponse, CreateInvoiceRequest, InvoiceLineRequest,
} from '../../shared/services/invoice.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { HeaderStepComponent } from './steps/header-step.component';
import { BuyerStepComponent } from './steps/buyer-step.component';
import { LinesStepComponent } from './steps/lines-step.component';
import { ReviewStepComponent } from './steps/review-step.component';

@Component({
  selector: 'app-invoice-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatButtonModule,
    MatIconModule, MatStepperModule,
    HeaderStepComponent, BuyerStepComponent, LinesStepComponent, ReviewStepComponent,
  ],
  templateUrl: './invoice-form.component.html',
  styles: `
    .form-actions { display: flex; gap: 12px; margin-top: 16px; justify-content: flex-end; }
  `,
})
export class InvoiceFormComponent implements OnChanges {
  private fb = inject(FormBuilder);
  private invoiceService = inject(InvoiceService);
  private toast = inject(ToastNotificationService);

  @Input() invoice: InvoiceDetailResponse | null = null;
  @Input() companyId: number | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  @ViewChild('stepper') stepper!: MatStepper;
  @ViewChild(ReviewStepComponent) reviewStep?: ReviewStepComponent;

  form: FormGroup;
  submitting = false;
  isEdit = false;
  serverValidationPassed = false;

  constructor() {
    this.form = this.fb.group({
      authority: ['ZATCA', Validators.required],
      type: ['TAX_INVOICE', Validators.required],
      subtypeFlags: this.fb.group({
        thirdParty: [false],
        nominal: [false],
        export: [false],
        summary: [false],
        selfBilled: [false],
      }),
      issueDate: [this.todayAsString(), Validators.required],
      supplyDate: [null],
      supplyEndDate: [null],
      currency: ['SAR'],
      branchId: [null, Validators.required],
      paymentMeansCode: ['10'],
      paymentTerms: [''],
      buyerId: [null],
      originalInvoiceId: [null],
      externalInvoiceReference: [null],
      notes: [''],
      totalAllowances: [0],
      prepaidAmount: [0],
      lines: this.fb.array([]),
    });

    this.addLine();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['invoice'] && this.invoice) {
      this.isEdit = true;
      const subtypeFlags = this.invoice.subtypeFlags || {};
      this.form.patchValue({
        authority: this.invoice.authority,
        type: this.invoice.type,
        issueDate: this.invoice.issueDate,
        supplyDate: this.invoice.supplyDate,
        supplyEndDate: this.invoice.supplyEndDate,
        currency: this.invoice.currency,
        branchId: this.invoice.branchId,
        paymentMeansCode: this.invoice.paymentMeansCode,
        paymentTerms: this.invoice.paymentTerms,
        buyerId: this.invoice.buyerId,
        originalInvoiceId: this.invoice.originalInvoiceId,
        externalInvoiceReference: this.invoice.externalInvoiceReference,
        notes: this.invoice.notes,
        totalAllowances: this.invoice.totalAllowances,
        prepaidAmount: this.invoice.prepaidAmount,
      });

      const sfGroup = this.form.get('subtypeFlags') as FormGroup;
      sfGroup.patchValue({
        thirdParty: subtypeFlags['thirdParty'] || false,
        nominal: subtypeFlags['nominal'] || false,
        export: subtypeFlags['export'] || false,
        summary: subtypeFlags['summary'] || false,
        selfBilled: subtypeFlags['selfBilled'] || false,
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
    } else if (changes['invoice'] && !this.invoice) {
      this.isEdit = false;
      this.form.reset({
        authority: 'ZATCA',
        type: 'TAX_INVOICE',
        issueDate: this.todayAsString(),
        currency: 'SAR',
        paymentMeansCode: '10',
        totalAllowances: 0,
        prepaidAmount: 0,
      });
      const sfGroup = this.form.get('subtypeFlags') as FormGroup;
      sfGroup.reset({
        thirdParty: false, nominal: false, export: false, summary: false, selfBilled: false,
      });
      const linesArray = this.form.get('lines') as FormArray;
      linesArray.clear();
      this.addLine();
    }
  }

  get linesArray(): FormArray {
    return this.form.get('lines') as FormArray;
  }

  addLine(): void {
    const authority = this.form.get('authority')?.value || 'ZATCA';
    const sortOrder = this.linesArray.length + 1;
    this.linesArray.push(this.fb.group({
      itemId: [null],
      descriptionEn: ['', Validators.required],
      quantity: [1, [Validators.required, Validators.min(0.01)]],
      unit: ['EA', Validators.required],
      unitPrice: [0, [Validators.required, Validators.min(0.01)]],
      discountAmount: [0],
      vatCategory: ['S', Validators.required],
      vatRate: [authority === 'ZATCA' ? 15 : 14, Validators.required],
      sortOrder: [sortOrder],
    }));
  }

  onAuthorityChanged(authority: string): void {
    const defaultVatRate = authority === 'ZATCA' ? 15 : 14;
    for (const control of this.linesArray.controls) {
      const fg = control as FormGroup;
      fg.patchValue({ vatRate: defaultVatRate });
    }
  }

  private get needsBuyer(): boolean {
    const authority = this.form.get('authority')?.value;
    const type = this.form.get('type')?.value;
    if (authority === 'ZATCA') {
      return type === 'TAX_INVOICE' || type === 'CREDIT_NOTE' || type === 'DEBIT_NOTE';
    }
    return true;
  }

  isStepValid(stepIndex: number): boolean {
    switch (stepIndex) {
      case 0:
        return !!this.form.get('authority')?.valid
          && !!this.form.get('type')?.valid
          && !!this.form.get('branchId')?.valid
          && !!this.form.get('issueDate')?.valid;
      case 1:
        if (!this.needsBuyer) return true;
        return !!this.form.get('buyerId')?.value;
      case 2:
        return this.linesArray.length > 0 && this.linesArray.valid;
      case 3:
        return this.form.valid;
      default:
        return true;
    }
  }

  canSubmit(): boolean {
    if (!this.form.valid || this.linesArray.length === 0 || this.submitting) {
      return false;
    }
    if (this.reviewStep && this.reviewStep.hasServerError) {
      return false;
    }
    return true;
  }

  onValidationComplete(passed: boolean): void {
    this.serverValidationPassed = passed;
  }

  private buildRequest(): CreateInvoiceRequest {
    const formVal = this.form.value;
    const lines: InvoiceLineRequest[] = formVal.lines.map((l: Record<string, unknown>, i: number) => ({
      itemId: l['itemId'] || null,
      descriptionEn: l['descriptionEn'],
      quantity: parseFloat(l['quantity'] as string),
      unit: l['unit'],
      unitPrice: parseFloat(l['unitPrice'] as string),
      discountAmount: parseFloat(l['discountAmount'] as string) || 0,
      vatCategory: l['vatCategory'] as string,
      vatRate: parseFloat(l['vatRate'] as string),
      sortOrder: i + 1,
    }));

    const activeFlags: Record<string, boolean> = {};
    const sf = formVal.subtypeFlags;
    if (sf) {
      for (const [key, value] of Object.entries(sf)) {
        if (value === true) activeFlags[key] = true;
      }
    }

    return {
      type: formVal.type,
      subtypeFlags: Object.keys(activeFlags).length > 0 ? activeFlags : undefined,
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
      externalInvoiceReference: formVal.externalInvoiceReference || null,
      notes: formVal.notes || null,
      lines,
    };
  }

  onSubmit(): void {
    if (!this.canSubmit()) return;
    this.submitting = true;

    const request = this.buildRequest();
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
          this.toast.error(errors.map((e: { field: string; message: string }) => `${e.field}: ${e.message}`).join('; '));
        } else {
          this.toast.error(err.error?.error || 'Failed to save invoice');
        }
        this.submitting = false;
      },
    });
  }

  async onStepperSelectionChange(event: { selectedIndex: number }): Promise<void> {
    if (event.selectedIndex !== 3) return;
    if (this.isEdit || !this.reviewStep) return;
    if (!this.form.valid || this.linesArray.length === 0) return;

    try {
      const saved = await firstValueFrom(this.invoiceService.create(this.buildRequest()));
      this.invoice = saved;
      this.isEdit = true;
    } catch {
      this.toast.error('Could not save draft for validation');
    }
  }

  onCancel(): void {
    this.cancelled.emit();
  }

  private todayAsString(): string {
    return new Date().toISOString().split('T')[0];
  }
}
