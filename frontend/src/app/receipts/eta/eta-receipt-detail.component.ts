import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormsModule } from '@angular/forms';
import { EtaReceiptService, SubmissionAttemptResponse, EtaReceipt } from './services/eta-receipt.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { DetailGridComponent, DetailGridRow } from '../../documents/shared/detail-grid.component';
import { DocumentDetailShellComponent, DocumentDetailTile } from '../../documents/shared/document-detail-shell.component';
import { isAllowed, receiptTransitions } from '../../invoices/shared/generated/eta-states';
import { CancelEtaDialogComponent } from '../../shared/dialogs/cancel-eta-dialog.component';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { toSignal, toObservable } from '@angular/core/rxjs-interop';
import { BehaviorSubject, of, switchMap } from 'rxjs';

@Component({
  selector: 'app-receipt-number-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule,
            MatFormFieldModule, MatInputModule, FormsModule],
  template: `
    <h2 mat-dialog-title>Enter new receipt number</h2>
    <mat-dialog-content>
      <mat-form-field class="full-width">
        <mat-label>Receipt Number</mat-label>
        <input matInput [(ngModel)]="receiptNumber" required>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="primary" [mat-dialog-close]="receiptNumber"
              [disabled]="!receiptNumber">Create</button>
    </mat-dialog-actions>
  `,
  styles: [`.full-width { width: 100%; }`]
})
export class ReceiptNumberDialogComponent {
  receiptNumber = '';
}

@Component({
  selector: 'app-eta-receipt-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, MatButtonModule, MatIconModule,
            MatCardModule, MatTableModule, HasPermissionDirective,
            DetailGridComponent, DocumentDetailShellComponent],
  template: `
    <app-document-detail-shell
      *ngIf="receipt()?.body as rec; else loading"
      authority="ETA"
      eyebrow="ETA Receipt"
      [documentNumber]="rec.receiptNumber"
      [status]="rec.state"
      [subline]="subline(rec)"
      [backLink]="['/receipts/eta']"
      backLabel="ETA receipts"
      [tiles]="summaryTiles(rec)"
      [lineCount]="rec.lines.length"
      [submissions]="submissions()"
      [artifactTypes]="artifactTypes"
      [companyId]="rec.companyId"
      [docId]="rec.id"
      [getArtifactUrl]="getArtifactUrlFn()">
      <ng-container actions>
        <ng-container *appHasPermission="['RECEIPT', 'EDIT']">
          <button mat-raised-button color="primary"
                  *ngIf="isActionAllowed(rec.state, 'EDIT')"
                  [routerLink]="['/receipts/eta', rec.id, 'edit']">Edit</button>
        </ng-container>
        <ng-container *appHasPermission="['RECEIPT', 'SUBMIT']">
          <button mat-raised-button color="accent"
                  *ngIf="isActionAllowed(rec.state, 'SUBMIT')"
                  (click)="submit()">Submit</button>
        </ng-container>
        <ng-container *appHasPermission="['RECEIPT', 'CREATE']">
          <button mat-raised-button color="warn"
                  *ngIf="rec.state === 'REJECTED'"
                  (click)="cloneAsDraft()">Create new draft</button>
        </ng-container>
        <ng-container *appHasPermission="['RECEIPT', 'REFRESH']">
          <button mat-raised-button
                  *ngIf="isActionAllowed(rec.state, 'CHECK_STATUS')"
                  (click)="checkStatus()">Check Status</button>
        </ng-container>
        <ng-container *appHasPermission="['RECEIPT', 'CANCEL']">
          <button mat-raised-button color="warn"
                  *ngIf="isActionAllowed(rec.state, 'CANCEL')"
                  (click)="cancel()">Cancel</button>
        </ng-container>
        <ng-container *appHasPermission="['RECEIPT', 'SUBMIT']">
          <button mat-raised-button color="accent"
                  *ngIf="isActionAllowed(rec.state, 'RETRY')"
                  (click)="retry()">Retry</button>
        </ng-container>
      </ng-container>

      <ng-container tab-overview>
        <div class="dd-overview-layout">
          <app-detail-grid [rows]="overviewRows(rec)"></app-detail-grid>
          <div class="dd-side-stack">
            <mat-card *ngIf="rec.exchangeRate || rec.previousUuid || rec.sOrderNameCode || rec.grossWeight || rec.erpReferenceId" class="dd-panel">
              <mat-card-header><mat-card-title>v1.2 Metadata</mat-card-title></mat-card-header>
              <mat-card-content>
                <app-detail-grid [rows]="v12Rows(rec)"></app-detail-grid>
              </mat-card-content>
            </mat-card>
            <mat-card *ngIf="rec.grossWeight || rec.netWeight" class="dd-panel">
              <mat-card-header><mat-card-title>Logistics</mat-card-title></mat-card-header>
              <mat-card-content>
                <app-detail-grid [rows]="logisticsRows(rec)"></app-detail-grid>
              </mat-card-content>
            </mat-card>
            <mat-card *ngIf="rec.taxTotals || rec.extraReceiptDiscountData || rec.contractorData || rec.beneficiaryData" class="dd-panel">
              <mat-card-header><mat-card-title>Structured Data</mat-card-title></mat-card-header>
              <mat-card-content>
                <div *ngIf="rec.taxTotals"><strong>Tax Totals</strong><pre class="dd-json-block">{{ rec.taxTotals | json }}</pre></div>
                <div *ngIf="rec.extraReceiptDiscountData"><strong>Extra Receipt Discount</strong><pre class="dd-json-block">{{ rec.extraReceiptDiscountData | json }}</pre></div>
                <div *ngIf="rec.contractorData"><strong>Contractor</strong><pre class="dd-json-block">{{ rec.contractorData | json }}</pre></div>
                <div *ngIf="rec.beneficiaryData"><strong>Beneficiary</strong><pre class="dd-json-block">{{ rec.beneficiaryData | json }}</pre></div>
              </mat-card-content>
            </mat-card>
          </div>
        </div>
      </ng-container>

      <ng-container tab-lines>
        <div *ngIf="rec.lines?.length; else noLines" class="dd-table-wrap">
          <table mat-table [dataSource]="rec.lines" class="dd-lines-table">
            <ng-container matColumnDef="code">
              <th mat-header-cell *matHeaderCellDef>Code</th>
              <td mat-cell *matCellDef="let l">{{ l.itemCode }}</td>
              <td mat-footer-cell *matFooterCellDef></td>
            </ng-container>
            <ng-container matColumnDef="desc">
              <th mat-header-cell *matHeaderCellDef>Description</th>
              <td mat-cell *matCellDef="let l">{{ l.description }}</td>
              <td mat-footer-cell *matFooterCellDef class="dd-total-label">Totals</td>
            </ng-container>
            <ng-container matColumnDef="qty">
              <th mat-header-cell *matHeaderCellDef>Qty</th>
              <td mat-cell *matCellDef="let l" class="dd-num">{{ l.quantity }}</td>
              <td mat-footer-cell *matFooterCellDef></td>
            </ng-container>
            <ng-container matColumnDef="price">
              <th mat-header-cell *matHeaderCellDef>Unit Price</th>
              <td mat-cell *matCellDef="let l" class="dd-num">{{ l.unitPrice }}</td>
              <td mat-footer-cell *matFooterCellDef></td>
            </ng-container>
            <ng-container matColumnDef="discount">
              <th mat-header-cell *matHeaderCellDef>Discount</th>
              <td mat-cell *matCellDef="let l" class="dd-num">{{ sumAmount(l.commercialDiscountData) }}</td>
              <td mat-footer-cell *matFooterCellDef class="dd-num dd-total-cell">{{ sumLineDiscount(rec.lines) }}</td>
            </ng-container>
            <ng-container matColumnDef="total">
              <th mat-header-cell *matHeaderCellDef>Total</th>
              <td mat-cell *matCellDef="let l" class="dd-num">{{ l.total }}</td>
              <td mat-footer-cell *matFooterCellDef class="dd-num dd-total-cell">{{ sumLineTotal(rec.lines) }}</td>
            </ng-container>
            <ng-container matColumnDef="taxes">
              <th mat-header-cell *matHeaderCellDef>Taxes</th>
              <td mat-cell *matCellDef="let l">
                <span *ngFor="let t of l.taxes; let last = last">
                  {{ t.taxType }}: {{ t.taxAmount }}<span *ngIf="!last">, </span>
                </span>
              </td>
              <td mat-footer-cell *matFooterCellDef class="dd-num dd-total-cell">{{ sumLineTaxes(rec.lines) }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="lineColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: lineColumns;"></tr>
            <tr mat-footer-row *matFooterRowDef="lineColumns"></tr>
          </table>
        </div>
        <ng-template #noLines>
          <div class="dd-empty-lines">No line items on this receipt.</div>
        </ng-template>
      </ng-container>
    </app-document-detail-shell>

    <ng-template #loading>
      <div class="dd-loading">Loading receipt detail...</div>
    </ng-template>
  `,
  styleUrls: ['../../documents/shared/document-detail-content.scss']
})
export class EtaReceiptDetailComponent {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private service = inject(EtaReceiptService);
  private sessionCtx = inject(SessionContextService);
  private dialog = inject(MatDialog);
  private toast = inject(ToastNotificationService);
  context = toSignal(this.sessionCtx.context$, { initialValue: null });

  lineColumns = ['code', 'desc', 'qty', 'price', 'discount', 'total', 'taxes'];
  artifactTypes = ['SIGNED_JSON', 'ETA_RESPONSE'];
  private refresh$ = new BehaviorSubject<void>(undefined);

  receipt = toSignal(
    this.refresh$.pipe(switchMap(() => {
      const id = this.route.snapshot.paramMap.get('id')!;
      return this.service.getById(id);
    })), { initialValue: null as unknown as HttpResponse<EtaReceipt> }
  );

  submissions = toSignal(
    toObservable(this.receipt).pipe(
      switchMap(rec => {
        const body = rec?.body;
        if (!body?.companyId || !body?.id) return of([] as SubmissionAttemptResponse[]);
        return this.service.getSubmissions(body.companyId, body.id);
      })
    ), { initialValue: [] as SubmissionAttemptResponse[] }
  );

  isActionAllowed(state: string, action: string): boolean {
    return isAllowed(state, action, receiptTransitions);
  }

  sumAmount(arr: unknown[] | null | undefined): string {
    if (!arr?.length) return '0';
    return arr.reduce<number>((sum, x) => sum + Number((x as { amount?: unknown })?.amount ?? 0), 0).toFixed(2);
  }

  summaryTiles(rec: EtaReceipt): DocumentDetailTile[] {
    return [
      { label: 'Total Sales', value: rec.totalSalesAmount },
      { label: 'Discount', value: rec.totalCommercialDiscount },
      { label: 'Net Amount', value: rec.netAmount },
      { label: 'Total', value: rec.totalAmount, emphasis: true },
    ];
  }

  subline(rec: EtaReceipt): string {
    return [rec.issueDatetime, rec.currency, 'ETA', rec.paymentMethod].filter(Boolean).join(' - ');
  }

  overviewRows(rec: EtaReceipt): DetailGridRow[] {
    return [
      { label: 'State', value: rec.state },
      { label: 'Type', value: rec.documentType },
      { label: 'Issue Date', value: rec.issueDatetime },
      { label: 'Currency', value: rec.currency },
      { label: 'POS Serial', value: rec.posSerial },
      { label: 'Payment Method', value: rec.paymentMethod },
      { label: 'Original Receipt', value: rec.originalReceiptId, mono: true },
      { label: 'ETA UUID', value: rec.etaReceiptUuid, mono: true },
      { label: 'Submission ID', value: rec.etaSubmissionId, mono: true },
      { label: 'Total Sales', value: rec.totalSalesAmount },
      { label: 'Total Commercial Discount', value: rec.totalCommercialDiscount },
      { label: 'Net Amount', value: rec.netAmount },
      { label: 'Total', value: rec.totalAmount },
    ];
  }

  v12Rows(rec: EtaReceipt): DetailGridRow[] {
    return [
      { label: 'Exchange Rate', value: rec.exchangeRate },
      { label: 'Previous UUID', value: rec.previousUuid, mono: true },
      { label: 'Reference Old UUID', value: rec.referenceOldUuid, mono: true },
      { label: 'Order Name Code', value: rec.sOrderNameCode },
      { label: 'Delivery Mode', value: rec.orderDeliveryMode },
      { label: 'ERP Reference', value: rec.erpReferenceId, mono: true },
      { label: 'Original Invoice Number', value: rec.originalInvoiceNumber },
    ];
  }

  logisticsRows(rec: EtaReceipt): DetailGridRow[] {
    return [
      { label: 'Gross Weight', value: rec.grossWeight },
      { label: 'Net Weight', value: rec.netWeight },
    ];
  }

  sumLineDiscount(lines: EtaReceipt['lines']): string {
    return this.formatAmount((lines ?? []).reduce((sum, line) =>
      sum + Number(this.sumAmount(line.commercialDiscountData)), 0));
  }

  sumLineTotal(lines: EtaReceipt['lines']): string {
    return this.formatAmount((lines ?? []).reduce((sum, line) => sum + Number(line.total ?? 0), 0));
  }

  sumLineTaxes(lines: EtaReceipt['lines']): string {
    return this.formatAmount((lines ?? []).reduce((lineSum, line) => lineSum
        + (line.taxes ?? []).reduce((taxSum, tax) => taxSum + Number(tax.taxAmount ?? 0), 0), 0));
  }

  private formatAmount(value: unknown): string {
    const numeric = Number(value ?? 0);
    return Number.isFinite(numeric) ? numeric.toFixed(2) : String(value ?? '');
  }

  getArtifactUrlFn(): (type: string) => string {
    const rec = this.receipt()?.body;
    if (!rec) return () => '';
    return (type: string) => this.service.getArtifactUrl(rec.companyId, rec.id, type);
  }

  submit(): void {
    const rec = this.receipt()?.body;
    if (!rec) return;
    const companyId = rec.companyId;
    this.service.submit(companyId, rec.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Submit failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }

  cloneAsDraft(): void {
    const rec = this.receipt()?.body;
    if (!rec) return;
    const companyId = rec.companyId;
    const ref = this.dialog.open(ReceiptNumberDialogComponent, {
      width: '400px',
    });
    ref.afterClosed().subscribe(newNumber => {
      if (newNumber) {
        this.service.cloneAsDraft(companyId, rec.id, newNumber).subscribe({
          next: resp => this.router.navigate(['/receipts/eta', resp.body?.id]),
          error: (err) => this.toast.error('Clone failed: ' + (err?.error?.message || 'Unknown error')),
        });
      }
    });
  }

  checkStatus(): void {
    const rec = this.receipt()?.body;
    if (!rec) return;
    const companyId = rec.companyId;
    this.service.checkStatus(companyId, [rec.id]).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Check status failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }

  cancel(): void {
    const rec = this.receipt()?.body;
    if (!rec) return;
    const companyId = rec.companyId;
    const ref = this.dialog.open(CancelEtaDialogComponent, {
      width: '400px',
      data: { reason: '' },
    });
    ref.afterClosed().subscribe(result => {
      if (result?.reason) {
        this.service.cancel(companyId, rec.id, result.reason).subscribe({
          next: () => this.refresh$.next(),
          error: (err) => this.toast.error('Cancel failed: ' + (err?.error?.message || 'Unknown error')),
        });
      }
    });
  }

  retry(): void {
    const rec = this.receipt()?.body;
    if (!rec) return;
    const companyId = rec.companyId;
    this.service.retry(companyId, rec.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Retry failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }
}
