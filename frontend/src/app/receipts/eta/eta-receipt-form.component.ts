import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, FormArray, Validators, AbstractControl } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { EtaReceiptService, ConflictBody } from './services/eta-receipt.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { LineItemsEditorComponent } from '../../documents/shared/line-items-editor.component';
import { ConflictResolutionDialogComponent } from '../../documents/shared/conflict-resolution.dialog';
import { toSignal } from '@angular/core/rxjs-interop';
import { map, take } from 'rxjs/operators';

const RECEIPT_DOC_TYPES: { value: string; label: string }[] = [
  { value: 'r', label: 'Standard Receipt (r)' },
  { value: 'rr', label: 'Return (rr)' },
  { value: 'rrwr', label: 'Return with Replace (rrwr)' },
  { value: 'cr', label: 'Cancellation (cr)' },
  { value: 'crr', label: 'Cancellation Refund (crr)' },
  { value: 'gs', label: 'General Sale (gs)' },
  { value: 'gsr', label: 'General Sale Return (gsr)' },
  { value: 'rt', label: 'Refund (rt)' },
  { value: 'rtr', label: 'Refund with Replace (rtr)' },
  { value: 'tr', label: 'Transfer (tr)' },
  { value: 'trr', label: 'Transfer Return (trr)' },
  { value: 'bk', label: 'Bank Deposit (bk)' },
  { value: 'bkr', label: 'Bank Deposit Return (bkr)' },
  { value: 'ed', label: 'Electronic Debit (ed)' },
  { value: 'edr', label: 'Electronic Debit Return (edr)' },
  { value: 'pr', label: 'Payment (pr)' },
  { value: 'prr', label: 'Payment Return (prr)' },
  { value: 'sh', label: 'Shift (sh)' },
  { value: 'shr', label: 'Shift Return (shr)' },
  { value: 'en', label: 'Entry (en)' },
  { value: 'enr', label: 'Entry Return (enr)' },
  { value: 'ut', label: 'Utility (ut)' },
  { value: 'utr', label: 'Utility Return (utr)' },
];

const REQUIRES_ORIGINAL_TYPES = new Set([
  'rr', 'rrwr', 'cr', 'crr', 'rt', 'rtr', 'tr', 'trr',
  'bk', 'bkr', 'ed', 'edr', 'pr', 'prr', 'sh', 'shr',
  'en', 'enr', 'ut', 'utr',
]);

function jsonOrNullValidator(control: AbstractControl): null | { invalidJson: true } {
  const v = control.value;
  if (v == null || v === '') return null;
  if (typeof v === 'object') return null;
  if (typeof v === 'string') {
    try { JSON.parse(v); return null; } catch { return { invalidJson: true }; }
  }
  return { invalidJson: true };
}

function parseJsonField(v: unknown): Record<string, unknown> | null {
  if (v == null || v === '') return null;
  if (typeof v === 'string') {
    try { return JSON.parse(v) as Record<string, unknown>; } catch { return {}; }
  }
  return v as Record<string, unknown>;
}

@Component({
  selector: 'app-eta-receipt-form',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule,
            MatButtonModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatCardModule, MatDialogModule,
            LineItemsEditorComponent],
  template: `
    <div class="form-container">
      <h2>{{ isEdit() ? 'Edit Receipt' : 'New Receipt' }}</h2>
      <form [formGroup]="form" (ngSubmit)="onSubmit()">
        <mat-form-field *ngIf="!isEdit()">
          <mat-label>Company</mat-label>
          <mat-select formControlName="owningCompanyId" required>
            @for (c of companies(); track c.companyId) {
              <mat-option [value]="c.companyId">{{ c.companyNameEn }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-card>
          <mat-card-content>
            <mat-form-field><mat-label>Receipt Number</mat-label>
              <input matInput formControlName="receiptNumber" required></mat-form-field>
            <mat-form-field><mat-label>Document Type</mat-label>
              <mat-select formControlName="documentType" required>
                @for (dt of docTypes; track dt.value) {
                  <mat-option [value]="dt.value">{{ dt.label }}</mat-option>
                }
              </mat-select></mat-form-field>
            <mat-form-field><mat-label>Issue Date</mat-label>
              <input matInput formControlName="issueDatetime" type="datetime-local" required></mat-form-field>
            <mat-form-field><mat-label>Currency</mat-label>
              <input matInput formControlName="currency" required></mat-form-field>
            <mat-form-field><mat-label>POS Serial</mat-label>
              <input matInput formControlName="posSerial"></mat-form-field>
            <mat-form-field><mat-label>Payment Method</mat-label>
              <input matInput formControlName="paymentMethod"></mat-form-field>
          </mat-card-content>
        </mat-card>

        <mat-card *ngIf="showOriginalPicker()">
          <mat-card-header><mat-card-title>Original Receipt</mat-card-title></mat-card-header>
          <mat-card-content>
            <mat-form-field class="full-width">
              <mat-label>Original Receipt ID (UUID)</mat-label>
              <input matInput formControlName="originalReceiptId" required
                     placeholder="00000000-0000-0000-0000-000000000000">
            </mat-form-field>
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-card-header><mat-card-title>Seller</mat-card-title></mat-card-header>
          <mat-card-content>
            <mat-form-field class="full-width">
              <mat-label>Seller Data (JSON)</mat-label>
              <textarea matInput formControlName="sellerData" rows="6"
                        placeholder='{"type":"B","name":"Company Name","id":"123456"}'></textarea>
            </mat-form-field>
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-card-header><mat-card-title>Buyer (optional)</mat-card-title></mat-card-header>
          <mat-card-content>
            <mat-form-field class="full-width">
              <mat-label>Buyer Data (JSON)</mat-label>
              <textarea matInput formControlName="buyerData" rows="6"
                        placeholder='{"type":"P","name":"Buyer Name"}'></textarea>
            </mat-form-field>
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-card-header><mat-card-title>Totals</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="totals-row">
              <mat-form-field>
                <mat-label>Total Sales Amount</mat-label>
                <input matInput formControlName="totalSalesAmount" type="number" required>
              </mat-form-field>
              <mat-form-field>
                <mat-label>Total Commercial Discount</mat-label>
                 <input matInput formControlName="totalCommercialDiscount" type="number">
              </mat-form-field>
              <mat-form-field>
                <mat-label>Extra Discount Amount</mat-label>
                <input matInput formControlName="extraDiscountAmount" type="number">
              </mat-form-field>
              <mat-form-field>
                <mat-label>Total Items Discount</mat-label>
                <input matInput formControlName="totalItemsDiscountAmount" type="number">
              </mat-form-field>
              <mat-form-field>
                <mat-label>Net Amount</mat-label>
                <input matInput formControlName="netAmount" type="number" required>
              </mat-form-field>
              <mat-form-field>
                <mat-label>Total Amount</mat-label>
                <input matInput formControlName="totalAmount" type="number" required>
              </mat-form-field>
            </div>
          </mat-card-content>
        </mat-card>

        <app-line-items-editor [linesArray]="linesArray" (validityChange)="onLinesValidity($event)"></app-line-items-editor>

        <button mat-raised-button color="primary" type="submit"
                [disabled]="form.invalid || !linesValid">Save</button>
      </form>
    </div>
  `,
  styles: [`.form-container { padding: 16px; } mat-form-field { margin-right: 16px; width: 200px; }
    .full-width { width: 100%; box-sizing: border-box; }
    .totals-row { display: flex; gap: 16px; flex-wrap: wrap; }
    textarea { font-family: monospace; font-size: 12px; }`]
})
export class EtaReceiptFormComponent {
  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private service = inject(EtaReceiptService);
  private sessionCtx = inject(SessionContextService);
  private dialog = inject(MatDialog);
  private context = toSignal(this.sessionCtx.context$);
  companies = toSignal(this.sessionCtx.companies$, { initialValue: [] });

  docTypes = RECEIPT_DOC_TYPES;

  isEdit = toSignal(
    this.route.paramMap.pipe(map(p => p.has('id'))),
    { initialValue: false }
  );

  linesValid = false;
  currentVersion: number | null = null;
  private editCompanyId = '';

  form: FormGroup = this.fb.group({
    receiptNumber: ['', Validators.required],
    documentType: ['r', Validators.required],
    documentTypeVersion: ['1.2'],
    issueDatetime: [new Date().toISOString(), Validators.required],
    currency: ['EGP', Validators.required],
    posSerial: [''],
    paymentMethod: [''],
    originalReceiptId: [null],
    sellerData: [{ value: {}, disabled: false }, jsonOrNullValidator],
    buyerData: [null, jsonOrNullValidator],
    totalSalesAmount: [0, Validators.required],
    totalCommercialDiscount: [0],
    extraDiscountAmount: [0],
    totalItemsDiscountAmount: [0],
    netAmount: [0, Validators.required],
    totalAmount: [0, Validators.required],
    owningCompanyId: ['', Validators.required],
    lines: this.fb.array([]),
  });

  constructor() {
    const editId = this.route.snapshot.paramMap.get('id');
    if (editId) {
      this.loadForEdit(editId);
    } else {
      this.sessionCtx.companies$.pipe(take(1)).subscribe(companies => {
        if (companies.length > 0) {
          this.form.patchValue({
            owningCompanyId: companies[0].companyId,
            sellerData: {
              type: 'B',
              name: companies[0].companyNameEn,
              nameAr: companies[0].companyNameAr,
            }
          });
        }
      });
    }
  }

  private loadForEdit(id: string): void {
    this.service.getById(id).pipe(take(1)).subscribe({
      next: resp => {
        const rec = resp.body;
        if (!rec) return;
        this.editCompanyId = rec.companyId;
        this.currentVersion = rec.version;
        this.form.patchValue({
          receiptNumber: rec.receiptNumber,
          documentType: rec.documentType,
          documentTypeVersion: rec.documentTypeVersion,
          issueDatetime: rec.issueDatetime,
          currency: rec.currency,
          posSerial: rec.posSerial,
          paymentMethod: rec.paymentMethod,
          originalReceiptId: rec.originalReceiptId,
          sellerData: rec.sellerData,
          buyerData: rec.buyerData,
          totalSalesAmount: rec.totalSalesAmount,
          totalCommercialDiscount: rec.totalCommercialDiscount,
          extraDiscountAmount: rec.extraDiscountAmount,
          totalItemsDiscountAmount: rec.totalItemsDiscountAmount,
          netAmount: rec.netAmount,
          totalAmount: rec.totalAmount,
        });
      },
    });
  }

  get linesArray(): FormArray {
    return this.form.get('lines') as FormArray;
  }

  showOriginalPicker(): boolean {
    return REQUIRES_ORIGINAL_TYPES.has(this.form.get('documentType')?.value);
  }

  onLinesValidity(valid: boolean): void {
    this.linesValid = valid;
  }

  onSubmit(): void {
    if (this.form.invalid || !this.linesValid) return;
    const raw = this.form.value;
    const payload = {
      ...raw,
      sellerData: parseJsonField(raw.sellerData),
      buyerData: parseJsonField(raw.buyerData),
    };
    delete (payload as any).owningCompanyId;
    if (this.isEdit()) {
      const id = this.route.snapshot.paramMap.get('id')!;
      const companyId = this.editCompanyId;
      this.service.update(companyId, id, payload,
          String(this.currentVersion ?? 0))
        .subscribe({
          next: resp => this.currentVersion = resp.body?.version ?? this.currentVersion,
          error: (err: ConflictBody) => this.handleConflict(err, companyId, id),
        });
    } else {
      const companyId = raw.owningCompanyId;
      this.service.create(companyId, payload).subscribe({
        next: resp => this.router.navigate(['/receipts/eta', resp.body?.id]),
      });
    }
  }

  private handleConflict(conflict: ConflictBody, companyId: string, id: string): void {
    const ref = this.dialog.open(ConflictResolutionDialogComponent, {
      data: { expectedVersion: conflict.expectedVersion, actualVersion: conflict.actualVersion, current: conflict.current, pendingChanges: this.form.value },
      width: '600px',
    });
    ref.afterClosed().subscribe(result => {
      if (result?.action === 'overwrite') {
        this.service.update(companyId, id, this.form.value,
            String(conflict.actualVersion))
          .subscribe({ next: resp => this.currentVersion = resp.body?.version ?? this.currentVersion });
      }
    });
  }
}
