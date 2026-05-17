import { Component, inject, Input, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormArray, FormBuilder, Validators, FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subscription } from 'rxjs';
import { ItemService, ItemResponse } from '../../../shared/services/item.service';
import {
  CalculationService,
  LineInput,
  LineCalculation,
} from '../services/calculation.service';

@Component({
  selector: 'app-lines-step',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatSelectModule, MatIconModule, MatTableModule, MatTooltipModule,
  ],
  templateUrl: './lines-step.component.html',
  styles: `
    .line-row { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr 1fr 1fr 1fr auto auto auto; gap: 8px; align-items: start; margin-bottom: 8px; }
    .line-header { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr 1fr 1fr 1fr auto auto auto; gap: 8px; margin-bottom: 4px; font-weight: bold; font-size: 0.85em; color: #666; padding: 0 4px; }
    .calc-display { font-size: 0.85em; color: #666; text-align: right; margin-top: 4px; }
    .item-search-row { display: flex; gap: 8px; margin-bottom: 16px; align-items: center; }
    .item-search-row mat-form-field { flex: 1; }
    .add-line-actions { display: flex; gap: 12px; margin-top: 8px; margin-bottom: 16px; }
    .totals-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 8px; max-width: 400px; margin-left: auto; margin-top: 16px; }
    .totals-grid .label { text-align: right; font-weight: 500; }
    .totals-grid .value { text-align: right; }
    .totals-grid .grand { font-weight: bold; font-size: 1.1em; border-top: 2px solid #333; padding-top: 8px; margin-top: 4px; }
    .vat-section { margin-top: 16px; }
    .vat-section table { width: auto; margin-left: auto; }
    th, td { padding: 4px 12px; text-align: left; }
    td.amount { text-align: right; }
    .discounts-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; max-width: 600px; margin-left: auto; margin-bottom: 16px; }
    .field-error mat-form-field { border: 1px solid #f44336; border-radius: 4px; }
  `,
})
export class LinesStepComponent implements OnInit, OnDestroy {
  @Input() form!: FormGroup;
  @Input() authority: string = 'ZATCA';

  private fb = inject(FormBuilder);
  private itemService = inject(ItemService);
  private calculationService = inject(CalculationService);
  private valueSub: Subscription | null = null;

  itemSearchTerm = '';
  searchedItems: ItemResponse[] = [];
  lineCalculations: LineCalculation[] = [];
  vatBreakdown: { category: string; rate: number; taxable: number; tax: number }[] = [];
  totals = { totalLineNet: 0, totalAllowances: 0, totalWithoutVat: 0, totalVat: 0, totalWithVat: 0, amountDue: 0 };

  get linesArray(): FormArray {
    return this.form?.get('lines') as FormArray;
  }

  ngOnInit(): void {
    this.recalcTotals();
    this.valueSub = this.form.valueChanges.subscribe(() => this.recalcTotals());
  }

  ngOnDestroy(): void {
    this.valueSub?.unsubscribe();
  }

  searchItems(): void {
    if (!this.itemSearchTerm || this.itemSearchTerm.length < 2) return;
    this.itemService.list(0, 20, this.itemSearchTerm, this.authority).subscribe({
      next: (res) => this.searchedItems = res.content,
    });
  }

  addItemToLine(item: ItemResponse, lineIndex: number): void {
    const lineGroup = this.linesArray.at(lineIndex) as FormGroup;
    lineGroup.patchValue({
      itemId: item.id,
      descriptionEn: item.nameEn,
      unit: item.unitOfMeasure,
      unitPrice: item.unitPrice,
      vatCategory: item.vatCategory,
      vatRate: item.vatRate,
    });
    this.searchedItems = [];
    this.itemSearchTerm = '';
    this.recalcTotals();
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
      vatRate: [this.authority === 'ZATCA' ? 15 : 14, Validators.required],
      sortOrder: [sortOrder],
    }));
    this.recalcTotals();
  }

  removeLine(index: number): void {
    if (this.linesArray.length > 1) {
      this.linesArray.removeAt(index);
      this.reindexSortOrder();
      this.recalcTotals();
    }
  }

  moveLineUp(index: number): void {
    if (index <= 0) return;
    const control = this.linesArray.at(index);
    this.linesArray.removeAt(index);
    this.linesArray.insert(index - 1, control);
    this.reindexSortOrder();
  }

  moveLineDown(index: number): void {
    if (index >= this.linesArray.length - 1) return;
    const control = this.linesArray.at(index);
    this.linesArray.removeAt(index);
    this.linesArray.insert(index + 1, control);
    this.reindexSortOrder();
  }

  private reindexSortOrder(): void {
    for (let i = 0; i < this.linesArray.length; i++) {
      const fg = this.linesArray.at(i) as FormGroup;
      fg.patchValue({ sortOrder: i + 1 });
    }
  }

  recalcTotals(): void {
    const lines = this.linesArray?.value as Record<string, unknown>[] || [];
    const totalAllowances = parseFloat(this.form?.get('totalAllowances')?.value) || 0;
    const prepaidAmount = parseFloat(this.form?.get('prepaidAmount')?.value) || 0;

    const lineInputs = lines.map((l: Record<string, unknown>) => this.toLineInput(l));
    this.lineCalculations = lineInputs.map(li => this.calculationService.calculateLine(li));
    this.vatBreakdown = this.calculationService.buildVatBreakdown(lineInputs, totalAllowances);
    this.totals = this.calculationService.calculateTotals(lineInputs, totalAllowances, prepaidAmount);
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

  getFieldError(lineIndex: number, controlName: string): string | null {
    const lineGroup = this.linesArray?.at(lineIndex) as FormGroup;
    if (!lineGroup) return null;
    const control = lineGroup.get(controlName);
    if (!control || !control.errors || !control.touched) return null;
    if (control.errors['required']) return 'Required';
    if (control.errors['min']) return `Min ${control.errors['min'].min}`;
    return null;
  }
}
