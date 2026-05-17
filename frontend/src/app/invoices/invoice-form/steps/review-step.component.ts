import { Component, EventEmitter, inject, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormArray } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import {
  CalculationService,
  LineCalculation,
  LineInput,
  VatBreakdownEntry,
  DocumentTotals,
} from '../services/calculation.service';
import {
  InvoiceService,
  ValidationItem,
} from '../../../shared/services/invoice.service';
import { ValidationResultComponent } from '../../invoice-detail/validation-result.component';

@Component({
  selector: 'app-review-step',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatButtonModule,
    MatCardModule, MatDividerModule, MatIconModule, MatProgressBarModule,
    ValidationResultComponent,
  ],
  templateUrl: './review-step.component.html',
  styles: `
    .review-section { margin-bottom: 16px; }
    .review-section h4 { margin: 0 0 8px 0; color: #333; }
    .meta-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; }
    .meta-item .label { font-weight: 500; color: #666; font-size: 0.85em; }
    .meta-item .value { margin-top: 2px; }
    .flag-tags { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
    .flag-tag { background: #e3f2fd; color: #1565c0; padding: 2px 8px; border-radius: 12px; font-size: 0.8em; }
    table { width: 100%; border-collapse: collapse; margin-top: 8px; }
    th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #e0e0e0; }
    th { background: #f5f5f5; font-weight: 500; font-size: 0.9em; }
    .amount { text-align: right; }
    .totals-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 8px; max-width: 400px; margin-left: auto; margin-top: 16px; }
    .totals-grid .label { text-align: right; font-weight: 500; }
    .totals-grid .value { text-align: right; }
    .totals-grid .grand { font-weight: bold; font-size: 1.1em; border-top: 2px solid #333; padding-top: 8px; margin-top: 4px; }
    .vat-section { margin-top: 16px; }
    .vat-section table { width: auto; margin-left: auto; }
    .validation-warnings { margin-top: 16px; padding: 12px; border-radius: 8px; }
    .warning-item { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
    .warning-item mat-icon { font-size: 18px; }
    .has-warnings { background: #fff3e0; }
    .no-warnings { background: #e8f5e9; }
    .warning-icon { color: #f57c00; }
    .success-icon { color: #388e3c; }
    .buyer-card { padding: 12px; border: 1px solid #e0e0e0; border-radius: 8px; }
    .validate-action { margin-top: 12px; }
    .validation-errors-block { margin-top: 16px; }
  `,
})
export class ReviewStepComponent {
  @Input() form!: FormGroup;
  @Input() invoiceId: string | null = null;
  @Output() validationComplete = new EventEmitter<boolean>();

  private calculationService = inject(CalculationService);
  private invoiceService = inject(InvoiceService);

  serverErrors: ValidationItem[] = [];
  serverWarnings: ValidationItem[] = [];
  serverValidationDone = false;
  validating = false;
  hasServerError = false;

  get canValidate(): boolean {
    return !!this.invoiceId && !this.validating;
  }

  get blockSubmit(): boolean {
    return this.hasServerError;
  }

  onValidate(): void {
    if (!this.invoiceId || this.validating) return;
    this.validating = true;
    this.serverErrors = [];
    this.serverWarnings = [];
    this.hasServerError = false;

    this.invoiceService.validate(this.invoiceId).subscribe({
      next: (result) => {
        this.serverErrors = result.errors || [];
        this.serverWarnings = result.warnings || [];
        this.hasServerError = this.serverErrors.length > 0;
        this.serverValidationDone = true;
        this.validating = false;
        this.validationComplete.emit(!this.hasServerError);
      },
      error: () => {
        this.validating = false;
        this.validationComplete.emit(false);
      },
    });
  }

  private toLineInput(l: Record<string, unknown>): LineInput {
    return {
      unitPrice: parseFloat(l['unitPrice'] as string) || 0,
      quantity: parseFloat(l['quantity'] as string) || 0,
      discountAmount: parseFloat(l['discountAmount'] as string) || 0,
      vatCategory: l['vatCategory'] as string,
      vatRate: parseFloat(l['vatRate'] as string) || 0,
    };
  }

  get headerValues(): Record<string, unknown> {
    return this.form?.value || {};
  }

  get subtypeFlagsList(): string[] {
    const flags = this.form?.get('subtypeFlags')?.value;
    if (!flags) return [];
    return Object.entries(flags)
      .filter(([, v]) => v === true)
      .map(([k]) => k);
  }

  get lines(): Record<string, unknown>[] {
    return (this.form?.get('lines') as FormArray)?.value || [];
  }

  get lineCalculations(): LineCalculation[] {
    return this.lines.map((l: Record<string, unknown>) =>
      this.calculationService.calculateLine(this.toLineInput(l)),
    );
  }

  get vatBreakdown(): VatBreakdownEntry[] {
    const totalAllowances = parseFloat(this.form?.get('totalAllowances')?.value) || 0;
    return this.calculationService.buildVatBreakdown(
      this.lines.map((l: Record<string, unknown>) => this.toLineInput(l)),
      totalAllowances,
    );
  }

  get totals(): DocumentTotals {
    const totalAllowances = parseFloat(this.form?.get('totalAllowances')?.value) || 0;
    const prepaidAmount = parseFloat(this.form?.get('prepaidAmount')?.value) || 0;
    return this.calculationService.calculateTotals(
      this.lines.map((l: Record<string, unknown>) => this.toLineInput(l)),
      totalAllowances,
      prepaidAmount,
    );
  }

  get validationWarnings(): string[] {
    const warnings: string[] = [];
    const v = this.headerValues as Record<string, unknown>;

    if (!v['buyerId'] && (v['type'] === 'TAX_INVOICE' || v['type'] === 'INVOICE')) {
      warnings.push('No buyer selected — required for B2B tax invoices');
    }

    if ((v['type'] === 'CREDIT_NOTE' || v['type'] === 'DEBIT_NOTE') &&
        !v['originalInvoiceId'] && !v['externalInvoiceReference']) {
      warnings.push('Credit/debit note should reference an original invoice');
    }

    if (v['authority'] === 'ZATCA' && v['supplyDate'] && v['issueDate'] &&
        (v['supplyDate'] as string) > (v['issueDate'] as string)) {
      warnings.push('Supply date is after issue date');
    }

    const linesArr = this.form?.get('lines') as FormArray;
    if (linesArr && linesArr.length === 0) {
      warnings.push('Invoice must have at least one line item');
    }

    for (let i = 0; i < this.lines.length; i++) {
      const l = this.lines[i];
      if (!l['descriptionEn']) {
        warnings.push(`Line ${i + 1}: Missing description`);
      }
    }

    return warnings;
  }

  get hasValidationWarnings(): boolean {
    return this.validationWarnings.length > 0;
  }

  get hasFormErrors(): boolean {
    return this.form?.invalid || false;
  }
}
