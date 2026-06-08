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
import { ZatcaSimplifiedService, ConflictBody } from './services/zatca-simplified.service';
import { SessionContextService } from '../shared/services/session-context.service';
import { LineItemsEditorComponent } from '../documents/shared/line-items-editor.component';
import { ConflictResolutionDialogComponent } from '../documents/shared/conflict-resolution.dialog';
import { toSignal } from '@angular/core/rxjs-interop';
import { map, take } from 'rxjs/operators';

function jsonOrNullValidator(control: AbstractControl): null | { invalidJson: true } {
  const v = control.value;
  if (v == null || v === '') return null;
  if (typeof v === 'object') return null;
  if (typeof v === 'string') {
    try { JSON.parse(v); return null; } catch { return { invalidJson: true }; }
  }
  return { invalidJson: true };
}

function simplifiedTxTypePrefixValidator(control: AbstractControl): null | { invalidSimplifiedTxType: true } {
  const v = control.value;
  if (!v || typeof v !== 'string' || v === '') return null;
  if (!v.startsWith('02')) return { invalidSimplifiedTxType: true };
  return null;
}

function parseJsonField(v: unknown): Record<string, unknown> | null {
  if (v == null || v === '') return null;
  if (typeof v === 'string') {
    try { return JSON.parse(v) as Record<string, unknown>; } catch { return {}; }
  }
  return v as Record<string, unknown>;
}

@Component({
  selector: 'app-zatca-simplified-form',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule,
            MatButtonModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatCardModule, MatDialogModule,
            LineItemsEditorComponent],
  template: `
    <div class="form-container">
      <h2>{{ isEdit() ? 'Edit Simplified Document' : 'New Simplified Document' }}</h2>
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
            <mat-form-field><mat-label>Invoice Number</mat-label>
              <input matInput formControlName="invoiceNumber" required></mat-form-field>
            <mat-form-field><mat-label>Invoice Type Code</mat-label>
              <mat-select formControlName="invoiceTypeCode" required>
                <mat-option value="388">Standard (388)</mat-option>
                <mat-option value="381">Credit Note (381)</mat-option>
                <mat-option value="383">Debit Note (383)</mat-option>
              </mat-select></mat-form-field>
            <mat-form-field><mat-label>Transaction Type Code</mat-label>
              <input matInput formControlName="transactionTypeCode" placeholder="0200000" required>
              <mat-error *ngIf="form.get('transactionTypeCode')?.hasError('invalidSimplifiedTxType')">
                Must start with 02
              </mat-error>
            </mat-form-field>
            <mat-form-field><mat-label>Issue Date</mat-label>
              <input matInput formControlName="issueDate" type="date" required></mat-form-field>
            <mat-form-field><mat-label>Issue Time</mat-label>
              <input matInput formControlName="issueTime" type="time" required></mat-form-field>
            <mat-form-field><mat-label>Currency</mat-label>
              <input matInput formControlName="currency" required></mat-form-field>
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-card-header><mat-card-title>Seller / Buyer</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="party-row">
              <mat-form-field class="party-field">
                <mat-label>Seller Data (JSON)</mat-label>
                <textarea matInput formControlName="sellerData" rows="6"
                          placeholder='{"taxRegistrationNumber":"3...","partyName":"Company"}'></textarea>
              </mat-form-field>
              <mat-form-field class="party-field">
                <mat-label>Buyer Data (JSON) — optional</mat-label>
                <textarea matInput formControlName="buyerData" rows="6"
                          placeholder='{"taxRegistrationNumber":"3...","partyName":"Buyer"}'></textarea>
              </mat-form-field>
            </div>
          </mat-card-content>
        </mat-card>

        <app-line-items-editor [linesArray]="linesArray" (validityChange)="onLinesValidity($event)"></app-line-items-editor>

        <button mat-raised-button color="primary" type="submit"
                [disabled]="form.invalid || !linesValid">Save</button>
        <div *ngIf="error" class="error-message">{{ error }}</div>
      </form>
    </div>
  `,
  styles: [`.form-container { padding: 16px; } mat-form-field { margin-right: 16px; width: 200px; }
    .party-row { display: flex; gap: 16px; flex-wrap: wrap; }
    .party-field { width: 320px; } textarea { font-family: monospace; font-size: 12px; }
    .error-message { color: #d32f2f; margin-top: 12px; }`]
})
export class ZatcaSimplifiedFormComponent {
  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private service = inject(ZatcaSimplifiedService);
  private sessionCtx = inject(SessionContextService);
  private dialog = inject(MatDialog);
  private context = toSignal(this.sessionCtx.context$);
  companies = toSignal(this.sessionCtx.companies$, { initialValue: [] });

  isEdit = toSignal(
    this.route.paramMap.pipe(map(p => p.has('id'))),
    { initialValue: false }
  );

  linesValid = false;
  currentVersion: number | null = null;
  error: string | null = null;
  private editCompanyId = '';

  form: FormGroup = this.fb.group({
    invoiceNumber: ['', Validators.required],
    invoiceTypeCode: ['388', Validators.required],
    transactionTypeCode: ['0200000', [Validators.required, simplifiedTxTypePrefixValidator]],
    issueDate: [new Date().toISOString().substring(0, 10), Validators.required],
    issueTime: ['12:00:00', Validators.required],
    currency: ['SAR', Validators.required],
    taxCurrency: ['SAR'],
    prepaidAmount: [0],
    sellerData: [{ value: {}, disabled: false }, jsonOrNullValidator],
    buyerData: [{ value: null, disabled: false }, jsonOrNullValidator],
    originalInvoiceId: [null],
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
              taxRegistrationNumber: '',
              partyName: companies[0].companyNameEn,
            }
          });
        }
      });
    }
  }

  private loadForEdit(id: string): void {
    this.service.getById(id).pipe(take(1)).subscribe({
      next: resp => {
        const doc = resp.body;
        if (!doc) return;
        this.editCompanyId = doc.companyId;
        this.currentVersion = doc.version;
        this.form.patchValue({
          invoiceNumber: doc.invoiceNumber,
          invoiceTypeCode: doc.invoiceTypeCode,
          transactionTypeCode: doc.transactionTypeCode,
          issueDate: doc.issueDate,
          issueTime: doc.issueTime,
          currency: doc.currency,
          taxCurrency: doc.taxCurrency,
          prepaidAmount: doc.prepaidAmount,
          sellerData: doc.sellerData,
          buyerData: doc.buyerData,
          originalInvoiceId: doc.originalInvoiceId,
        });
      },
      error: err => this.error = err?.message || 'Failed to load document',
    });
  }

  get linesArray(): FormArray {
    return this.form.get('lines') as FormArray;
  }

  onLinesValidity(valid: boolean): void {
    this.linesValid = valid;
  }

  onSubmit(): void {
    if (this.form.invalid || !this.linesValid) return;
    this.error = null;
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
          next: resp => {
            this.currentVersion = resp.body?.version ?? this.currentVersion;
            this.router.navigate(['/simplified', id]);
          },
          error: (err: ConflictBody | any) => {
            if (err?.code === 'OPTIMISTIC_LOCK_CONFLICT') {
              this.handleConflict(err as ConflictBody, companyId, id);
            } else {
              this.error = err?.error?.message || err?.message || 'Update failed';
            }
          },
        });
    } else {
      const companyId = raw.owningCompanyId;
      this.service.create(companyId, payload).subscribe({
        next: resp => {
          const newId = resp.body?.id;
          if (newId) {
            this.router.navigate(['/simplified', newId]);
          }
        },
        error: err => this.error = err?.error?.message || err?.message || 'Create failed',
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
        const payload = {
          ...this.form.value,
          sellerData: parseJsonField(this.form.value.sellerData),
          buyerData: parseJsonField(this.form.value.buyerData),
        };
        this.service.update(companyId, id, payload,
            String(conflict.actualVersion))
          .subscribe({
            next: resp => {
              this.currentVersion = resp.body?.version ?? this.currentVersion;
              this.router.navigate(['/simplified', id]);
            },
            error: err => this.error = err?.error?.message || err?.message || 'Overwrite failed',
          });
      }
    });
  }
}
