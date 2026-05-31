import { Component, inject, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, FormArray } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatExpansionModule } from '@angular/material/expansion';

@Component({
  selector: 'app-line-items-editor',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatFormFieldModule,
            MatInputModule, MatButtonModule, MatIconModule, MatTableModule,
            MatExpansionModule],
  template: `
    <div class="line-items-editor">
      <h3>Line Items</h3>
      <div [formGroup]="wrapper">
        <div formArrayName="lines" *ngIf="linesArray">
        <div *ngFor="let line of linesArray.controls; let i = index" [formGroupName]="i" class="line-row">
          <mat-form-field><mat-label>Item Code</mat-label>
            <input matInput formControlName="itemCode"></mat-form-field>
          <mat-form-field><mat-label>Description</mat-label>
            <input matInput formControlName="description"></mat-form-field>
          <mat-form-field><mat-label>Quantity</mat-label>
            <input matInput formControlName="quantity" type="number"></mat-form-field>
          <mat-form-field><mat-label>Unit Price</mat-label>
            <input matInput formControlName="salesTotal" type="number"></mat-form-field>

          <mat-expansion-panel class="tax-panel">
            <mat-expansion-panel-header>
              <mat-panel-title>Taxes ({{ getTaxArray(i)?.controls?.length || 0 }})</mat-panel-title>
            </mat-expansion-panel-header>
            <div [formArrayName]="'taxes'" *ngIf="getTaxArray(i) as taxArray">
              <div *ngFor="let tax of taxArray.controls; let j = index" [formGroupName]="j" class="tax-row">
                <mat-form-field><mat-label>Type</mat-label>
                  <input matInput formControlName="taxType"></mat-form-field>
                <mat-form-field><mat-label>Rate</mat-label>
                  <input matInput formControlName="taxRate" type="number"></mat-form-field>
                <mat-form-field><mat-label>Amount</mat-label>
                  <input matInput formControlName="taxAmount" type="number"></mat-form-field>
                <button mat-icon-button color="warn" (click)="removeTax(i, j)">
                  <mat-icon>delete</mat-icon>
                </button>
              </div>
              <button mat-stroked-button (click)="addTax(i)">
                <mat-icon>add</mat-icon> Add Tax
              </button>
            </div>
          </mat-expansion-panel>

          <button mat-icon-button color="warn" (click)="removeLine(i)">
            <mat-icon>delete</mat-icon>
          </button>
        </div>
        </div>
      </div>
      <button mat-stroked-button (click)="addLine()">
        <mat-icon>add</mat-icon> Add Line
      </button>
    </div>
  `,
  styles: [`
    .line-items-editor { margin-top: 16px; }
    .line-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; flex-wrap: wrap; }
    .line-row mat-form-field { flex: 1; min-width: 100px; }
    .tax-panel { width: 100%; margin-bottom: 8px; }
    .tax-row { display: flex; gap: 8px; align-items: center; margin-bottom: 4px; }
    .tax-row mat-form-field { flex: 1; min-width: 80px; }
  `]
})
export class LineItemsEditorComponent implements OnChanges {
  @Input() linesArray!: FormArray;
  @Output() validityChange = new EventEmitter<boolean>();

  private fb = inject(FormBuilder);
  wrapper: FormGroup = this.fb.group({});

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['linesArray'] && this.linesArray) {
      this.wrapper = this.fb.group({ lines: this.linesArray });
    }
  }

  ngDoCheck(): void {
    this.validityChange.emit(this.linesArray?.valid ?? false);
  }

  addLine(): void {
    if (this.linesArray) {
      this.linesArray.push(this.fb.group({
        lineNumber: [this.linesArray.length + 1],
        itemCode: [''],
        description: [''],
        itemType: ['GS1'],
        unitType: ['EA'],
        quantity: [0],
        unitValue: [{}],
        salesTotal: [0],
        discountAmount: [0],
        total: [0],
        taxes: this.fb.array([]),
      }));
    }
  }

  removeLine(index: number): void {
    this.linesArray?.removeAt(index);
  }

  getTaxArray(lineIndex: number): FormArray | null {
    const line = this.linesArray?.at(lineIndex) as FormGroup;
    return line?.get('taxes') as FormArray || null;
  }

  addTax(lineIndex: number): void {
    const taxArray = this.getTaxArray(lineIndex);
    if (taxArray) {
      taxArray.push(this.fb.group({
        taxType: ['VAT'],
        subType: [''],
        taxRate: [0],
        taxAmount: [0],
      }));
    }
  }

  removeTax(lineIndex: number, taxIndex: number): void {
    this.getTaxArray(lineIndex)?.removeAt(taxIndex);
  }
}
