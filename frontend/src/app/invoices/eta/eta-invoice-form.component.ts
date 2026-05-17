import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, FormArray, Validators, AbstractControl } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { EtaInvoiceService, ConflictBody } from './services/eta-invoice.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { LineItemsEditorComponent } from '../shared/line-items-editor.component';
import { ConflictResolutionDialogComponent } from '../shared/conflict-resolution.dialog';
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

function parseJsonField(v: unknown): Record<string, unknown> | null {
  if (v == null || v === '') return null;
  if (typeof v === 'string') {
    try { return JSON.parse(v) as Record<string, unknown>; } catch { return {}; }
  }
  return v as Record<string, unknown>;
}

@Component({
  selector: 'app-eta-invoice-form',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule,
            MatButtonModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatCardModule, MatDialogModule,
            LineItemsEditorComponent],
  template: `
    <div class="form-container">
      <h2>{{ isEdit() ? 'Edit Invoice' : 'New Invoice' }}</h2>
      <form [formGroup]="form" (ngSubmit)="onSubmit()">
        <mat-card>
          <mat-card-content>
            <mat-form-field><mat-label>Invoice Number</mat-label>
              <input matInput formControlName="invoiceNumber" required></mat-form-field>
            <mat-form-field><mat-label>Document Type</mat-label>
              <mat-select formControlName="documentType" required>
                <mat-option value="i">Standard (i)</mat-option>
                <mat-option value="c">Credit Note (c)</mat-option>
                <mat-option value="d">Debit Note (d)</mat-option>
                <mat-option value="ei">Export Invoice (ei)</mat-option>
                <mat-option value="ec">Export Credit (ec)</mat-option>
                <mat-option value="ed">Export Debit (ed)</mat-option>
              </mat-select></mat-form-field>
            <mat-form-field><mat-label>Issue Date</mat-label>
              <input matInput formControlName="issueDatetime" type="datetime-local" required></mat-form-field>
            <mat-form-field><mat-label>Currency</mat-label>
              <input matInput formControlName="currency" required></mat-form-field>
            <mat-form-field><mat-label>Taxpayer Activity Code</mat-label>
              <input matInput formControlName="taxpayerActivityCode"></mat-form-field>
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-card-header><mat-card-title>Seller / Buyer</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="party-row">
              <mat-form-field class="party-field">
                <mat-label>Seller Data (JSON)</mat-label>
                <textarea matInput formControlName="sellerData" rows="6"
                          placeholder='{"type":"B","name":"Company Name","id":"123456"}'></textarea>
              </mat-form-field>
              <mat-form-field class="party-field">
                <mat-label>Buyer Data (JSON)</mat-label>
                <textarea matInput formControlName="buyerData" rows="6"
                          placeholder='{"type":"P","name":"Buyer Name"}'></textarea>
              </mat-form-field>
            </div>
            <mat-form-field class="party-field">
              <mat-label>Delivery Data (JSON) — required for export types</mat-label>
              <textarea matInput formControlName="deliveryData" rows="3"
                        placeholder='{"country":"EG","governate":"Cairo"}'></textarea>
            </mat-form-field>
            <mat-form-field class="party-field">
              <mat-label>Payment Data (JSON)</mat-label>
              <textarea matInput formControlName="paymentData" rows="3"
                        placeholder='{"bankName":"Bank","accountNo":"1234"}'></textarea>
            </mat-form-field>
          </mat-card-content>
        </mat-card>

        <app-line-items-editor [linesArray]="linesArray" (validityChange)="onLinesValidity($event)"></app-line-items-editor>

        <button mat-raised-button color="primary" type="submit"
                [disabled]="form.invalid || !linesValid">Save</button>
      </form>
    </div>
  `,
  styles: [`.form-container { padding: 16px; } mat-form-field { margin-right: 16px; width: 200px; }
    .party-row { display: flex; gap: 16px; flex-wrap: wrap; }
    .party-field { width: 320px; } textarea { font-family: monospace; font-size: 12px; }`]
})
export class EtaInvoiceFormComponent {
  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private service = inject(EtaInvoiceService);
  private sessionCtx = inject(SessionContextService);
  private dialog = inject(MatDialog);
  private context = toSignal(this.sessionCtx.context$);

  isEdit = toSignal(
    this.route.paramMap.pipe(map(p => p.has('id'))),
    { initialValue: false }
  );

  linesValid = false;
  currentVersion: number | null = null;

  form: FormGroup = this.fb.group({
    invoiceNumber: ['', Validators.required],
    documentType: ['i', Validators.required],
    documentTypeVersion: ['1.0'],
    issueDatetime: [new Date().toISOString(), Validators.required],
    currency: ['EGP', Validators.required],
    taxpayerActivityCode: [''],
    sellerData: [{ value: {}, disabled: false }, jsonOrNullValidator],
    buyerData: [{ value: {}, disabled: false }, jsonOrNullValidator],
    deliveryData: [null, jsonOrNullValidator],
    paymentData: [null, jsonOrNullValidator],
    lines: this.fb.array([]),
  });

  constructor() {
    this.sessionCtx.context$.pipe(take(1)).subscribe(ctx => {
      if (!ctx) return;
      const active = ctx.companies?.find(c =>
          c.companyId === ctx.activeCompanyId);
      if (active) {
        this.form.patchValue({
          sellerData: {
            type: 'B',
            name: active.companyNameEn,
            nameAr: active.companyNameAr,
          }
        });
      }
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
    const companyId = this.context()?.activeCompanyId ?? '';
    const raw = this.form.value;
    const payload = {
      ...raw,
      sellerData: parseJsonField(raw.sellerData),
      buyerData: parseJsonField(raw.buyerData),
      deliveryData: parseJsonField(raw.deliveryData),
      paymentData: parseJsonField(raw.paymentData),
    };
    if (this.isEdit()) {
      const id = this.route.snapshot.paramMap.get('id')!;
      this.service.update(companyId, id, payload,
          String(this.currentVersion ?? 0))
        .subscribe({
          next: resp => this.currentVersion = resp.body?.version ?? this.currentVersion,
          error: (err: ConflictBody) => this.handleConflict(err, companyId, id),
        });
    } else {
      this.service.create(companyId, payload).subscribe({
        next: resp => this.currentVersion = resp.body?.version ?? null,
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
