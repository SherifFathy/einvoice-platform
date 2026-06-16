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
import { ZatcaSimplifiedService, ZatcaSimplifiedDocument, SubmissionAttemptResponse } from './services/zatca-simplified.service';
import { SessionContextService } from '../shared/services/session-context.service';
import { HasPermissionDirective } from '../shared/directives/has-permission.directive';
import { DetailGridComponent, DetailGridRow } from '../documents/shared/detail-grid.component';
import { DocumentDetailShellComponent, DocumentDetailTile } from '../documents/shared/document-detail-shell.component';
import { ToastNotificationService } from '../shared/services/toast.service';
import { toSignal, toObservable } from '@angular/core/rxjs-interop';
import { BehaviorSubject, of, switchMap } from 'rxjs';

@Component({
  selector: 'app-simplified-number-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule,
            MatFormFieldModule, MatInputModule, FormsModule],
  template: `
    <h2 mat-dialog-title>Enter new invoice number</h2>
    <mat-dialog-content>
      <mat-form-field class="full-width">
        <mat-label>Invoice Number</mat-label>
        <input matInput [(ngModel)]="invoiceNumber" required>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="primary" [mat-dialog-close]="invoiceNumber"
              [disabled]="!invoiceNumber">Create</button>
    </mat-dialog-actions>
  `,
  styles: [`.full-width { width: 100%; }`]
})
export class SimplifiedNumberDialogComponent {
  invoiceNumber = '';
}

@Component({
  selector: 'app-simplified-cancel-reason-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule,
            MatFormFieldModule, MatInputModule, FormsModule],
  template: `
    <h2 mat-dialog-title>Cancel document</h2>
    <mat-dialog-content>
      <mat-form-field class="full-width">
        <mat-label>Reason</mat-label>
        <input matInput [(ngModel)]="reason" required>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Back</button>
      <button mat-raised-button color="warn" [mat-dialog-close]="reason"
              [disabled]="!reason">Confirm Cancel</button>
    </mat-dialog-actions>
  `,
  styles: [`.full-width { width: 100%; }`]
})
export class SimplifiedCancelReasonDialogComponent {
  reason = '';
}

@Component({
  selector: 'app-zatca-simplified-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, MatButtonModule, MatIconModule,
            MatCardModule, MatTableModule, HasPermissionDirective,
            MatDialogModule, DetailGridComponent, DocumentDetailShellComponent],
  template: `
    <app-document-detail-shell
      *ngIf="document()?.body as doc; else loading"
      authority="ZATCA"
      eyebrow="Simplified Invoice"
      [documentNumber]="doc.invoiceNumber"
      [status]="doc.status"
      [secondaryStatus]="doc.reportingStatus"
      secondaryStatusLabel="Reporting"
      [subline]="subline(doc)"
      [backLink]="['/simplified']"
      backLabel="Simplified invoices"
      [tiles]="summaryTiles(doc)"
      [lineCount]="doc.lines.length"
      [submissions]="submissions()"
      [artifactTypes]="artifactTypes"
      [companyId]="doc.companyId"
      [docId]="doc.id"
      [getArtifactUrl]="getArtifactUrlFn()">
      <ng-container actions>
        <ng-container *appHasPermission="['SIMPLIFIED', 'EDIT']">
          <button mat-raised-button color="primary"
                  *ngIf="doc.status === 'DRAFT'"
                  [routerLink]="['/simplified', doc.id, 'edit']">Edit</button>
        </ng-container>
        <ng-container *appHasPermission="['SIMPLIFIED', 'SUBMIT']">
          <button mat-raised-button color="accent"
                  *ngIf="doc.status === 'DRAFT'"
                  (click)="submit()">Submit</button>
        </ng-container>
        <ng-container *appHasPermission="['SIMPLIFIED', 'CANCEL']">
          <button mat-raised-button color="warn"
                  *ngIf="doc.status === 'ACCEPTED'"
                  (click)="cancel()">Cancel</button>
        </ng-container>
        <ng-container *appHasPermission="['SIMPLIFIED', 'SUBMIT']">
          <button mat-raised-button color="accent"
                  *ngIf="doc.status === 'IN_REVIEW'"
                  (click)="retry()">Retry</button>
        </ng-container>
        <ng-container *appHasPermission="['SIMPLIFIED', 'REFRESH']">
          <button mat-stroked-button
                  *ngIf="doc.status === 'IN_REVIEW' || doc.status === 'SUBMITTED'"
                  (click)="checkStatus()">Check Status</button>
        </ng-container>
        <ng-container *appHasPermission="['SIMPLIFIED', 'CREATE']">
          <button mat-raised-button color="warn"
                  *ngIf="doc.status === 'REJECTED'"
                  (click)="cloneAsDraft()">Create new draft</button>
        </ng-container>
      </ng-container>

      <ng-container tab-overview>
        <div class="dd-overview-layout">
          <app-detail-grid [rows]="overviewRows(doc)"></app-detail-grid>
          <div class="dd-side-stack">
            <mat-card *ngIf="doc.qrCodeBase64" class="dd-panel">
              <mat-card-header><mat-card-title>QR Code</mat-card-title></mat-card-header>
              <mat-card-content>
                <img class="dd-qr" [src]="'data:image/png;base64,' + doc.qrCodeBase64" alt="QR Code">
              </mat-card-content>
            </mat-card>
            <mat-card *ngIf="doc.invoiceCounterValue != null" class="dd-panel">
              <mat-card-header><mat-card-title>Chain Snapshot</mat-card-title></mat-card-header>
              <mat-card-content>
                <app-detail-grid [rows]="chainRows(doc)"></app-detail-grid>
              </mat-card-content>
            </mat-card>
            <mat-card *ngIf="doc.cryptographicStampValue || doc.signedXmlArtifactId" class="dd-panel">
              <mat-card-header><mat-card-title>Cryptographic Stamp (BR-KSA-60)</mat-card-title></mat-card-header>
              <mat-card-content>
                <app-detail-grid [rows]="stampRows(doc)"></app-detail-grid>
              </mat-card-content>
            </mat-card>
          </div>
        </div>
      </ng-container>

      <ng-container tab-lines>
        <div *ngIf="doc.lines?.length; else noLines" class="dd-table-wrap">
          <table mat-table [dataSource]="doc.lines" class="dd-lines-table">
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
            <ng-container matColumnDef="net">
              <th mat-header-cell *matHeaderCellDef>Net</th>
              <td mat-cell *matCellDef="let l" class="dd-num">{{ l.netAmount }}</td>
              <td mat-footer-cell *matFooterCellDef class="dd-num dd-total-cell">{{ sumLineNet(doc.lines) }}</td>
            </ng-container>
            <ng-container matColumnDef="vat">
              <th mat-header-cell *matHeaderCellDef>VAT</th>
              <td mat-cell *matCellDef="let l">{{ l.vatCategoryCode }} ({{ l.vatRate }}%) = {{ l.vatAmount }}</td>
              <td mat-footer-cell *matFooterCellDef class="dd-num dd-total-cell">{{ sumLineVat(doc.lines) }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="lineColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: lineColumns;"></tr>
            <tr mat-footer-row *matFooterRowDef="lineColumns"></tr>
          </table>
        </div>
        <ng-template #noLines>
          <div class="dd-empty-lines">No line items on this simplified invoice.</div>
        </ng-template>
      </ng-container>
    </app-document-detail-shell>

    <ng-template #loading>
      <div class="dd-loading">Loading simplified invoice detail...</div>
    </ng-template>
  `,
  styleUrls: ['../documents/shared/document-detail-content.scss']
})
export class ZatcaSimplifiedDetailComponent {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private service = inject(ZatcaSimplifiedService);
  private sessionCtx = inject(SessionContextService);
  private dialog = inject(MatDialog);
  private toast = inject(ToastNotificationService);
  context = toSignal(this.sessionCtx.context$, { initialValue: null });

  lineColumns = ['code', 'desc', 'qty', 'price', 'net', 'vat'];
  artifactTypes = ['UBL_XML', 'SIGNED_UBL_XML', 'QR_PNG', 'ZATCA_REQUEST', 'ZATCA_RESPONSE'];
  private refresh$ = new BehaviorSubject<void>(undefined);

  document = toSignal(
    this.refresh$.pipe(switchMap(() => {
      const id = this.route.snapshot.paramMap.get('id')!;
      return this.service.getById(id);
    })), { initialValue: null as unknown as HttpResponse<ZatcaSimplifiedDocument> }
  );

  submissions = toSignal(
    toObservable(this.document).pipe(
      switchMap(doc => {
        const body = doc?.body;
        if (!body?.companyId || !body?.id) return of([] as SubmissionAttemptResponse[]);
        return this.service.getSubmissions(body.companyId, body.id);
      })
    ), { initialValue: [] as SubmissionAttemptResponse[] }
  );

  getArtifactUrlFn(): (type: string) => string {
    const doc = this.document()?.body;
    if (!doc) return () => '';
    return (type: string) => this.service.getArtifactUrl(doc.companyId, doc.id, type);
  }

  summaryTiles(doc: ZatcaSimplifiedDocument): DocumentDetailTile[] {
    return [
      { label: 'Tax Exclusive', value: doc.taxExclusiveAmount },
      { label: 'VAT', value: doc.taxAmount },
      { label: 'Tax Inclusive', value: doc.taxInclusiveAmount },
      { label: 'Payable', value: doc.payableAmount, emphasis: true },
    ];
  }

  subline(doc: ZatcaSimplifiedDocument): string {
    return [[doc.issueDate, doc.issueTime].filter(Boolean).join(' '), doc.currency, 'ZATCA'].filter(Boolean).join(' - ');
  }

  overviewRows(doc: ZatcaSimplifiedDocument): DetailGridRow[] {
    return [
      { label: 'Status', value: doc.status },
      { label: 'Reporting Status', value: doc.reportingStatus },
      { label: 'Type', value: doc.invoiceTypeCode },
      { label: 'Transaction Type', value: doc.transactionTypeCode },
      { label: 'Business Process', value: doc.businessProcessCode },
      { label: 'Issuance Reason', value: doc.issuanceReason },
      { label: 'Issue Date', value: [doc.issueDate, doc.issueTime].filter(Boolean).join(' ') },
      { label: 'Currency', value: doc.currency },
      { label: 'Seller VAT Number', value: doc.sellerVatNumber },
      { label: 'Buyer VAT Number', value: doc.buyerVatNumber },
      { label: 'Billing Reference', value: doc.billingReferenceId, mono: true },
      { label: 'Payment Means', value: doc.paymentMeansCode ? `${doc.paymentMeansCode}${doc.paymentMeansText ? ' - ' + doc.paymentMeansText : ''}` : null },
      { label: 'Tax Exclusive', value: doc.taxExclusiveAmount },
      { label: 'VAT', value: doc.taxAmount },
      { label: 'Tax Inclusive', value: doc.taxInclusiveAmount },
      { label: 'Payable', value: doc.payableAmount },
      { label: 'ZATCA UUID', value: doc.zatcaUuid, mono: true },
    ];
  }

  chainRows(doc: ZatcaSimplifiedDocument): DetailGridRow[] {
    return [
      { label: 'Counter', value: doc.invoiceCounterValue },
      { label: 'Hash', value: doc.invoiceHash, mono: true },
      { label: 'Previous Hash', value: doc.previousInvoiceHash, mono: true },
    ];
  }

  stampRows(doc: ZatcaSimplifiedDocument): DetailGridRow[] {
    return [
      { label: 'Stamp Value (ECDSA SignatureValue)', value: doc.cryptographicStampValue, mono: true },
      { label: 'Signed XML Artifact', value: doc.signedXmlArtifactId ? 'SIGNED_UBL_XML download available below' : null },
      { label: 'Signed At', value: doc.signedAt, mono: true },
    ];
  }

  sumLineNet(lines: ZatcaSimplifiedDocument['lines']): string {
    return this.formatAmount((lines ?? []).reduce((sum, line) => sum + Number(line.netAmount ?? 0), 0));
  }

  sumLineVat(lines: ZatcaSimplifiedDocument['lines']): string {
    return this.formatAmount((lines ?? []).reduce((sum, line) => sum + Number(line.vatAmount ?? 0), 0));
  }

  submit(): void {
    const doc = this.document()?.body;
    if (!doc) return;
    const companyId = doc.companyId;
    this.service.submit(companyId, doc.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Submit failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }

  cancel(): void {
    const doc = this.document()?.body;
    if (!doc) return;
    const companyId = doc.companyId;
    const ref = this.dialog.open(SimplifiedCancelReasonDialogComponent, {
      width: '400px',
    });
    ref.afterClosed().subscribe(reason => {
      if (reason) {
        this.service.cancel(companyId, doc.id, reason).subscribe({
          next: () => this.refresh$.next(),
          error: (err) => this.toast.error('Cancel failed: ' + (err?.error?.message || 'Unknown error')),
        });
      }
    });
  }

  retry(): void {
    const doc = this.document()?.body;
    if (!doc) return;
    const companyId = doc.companyId;
    this.service.retry(companyId, doc.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Retry failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }

  checkStatus(): void {
    const doc = this.document()?.body;
    if (!doc) return;
    const companyId = doc.companyId;
    this.service.checkStatus(companyId, doc.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Check status failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }

  cloneAsDraft(): void {
    const doc = this.document()?.body;
    if (!doc) return;
    const companyId = doc.companyId;
    const ref = this.dialog.open(SimplifiedNumberDialogComponent, {
      width: '400px',
    });
    ref.afterClosed().subscribe(newNumber => {
      if (newNumber) {
        this.service.cloneAsDraft(companyId, doc.id, newNumber).subscribe({
          next: resp => this.router.navigate(['/simplified', resp.body?.id]),
          error: (err) => this.toast.error('Clone failed: ' + (err?.error?.message || 'Unknown error')),
        });
      }
    });
  }

  private formatAmount(value: unknown): string {
    const numeric = Number(value ?? 0);
    return Number.isFinite(numeric) ? numeric.toFixed(2) : String(value ?? '');
  }
}
