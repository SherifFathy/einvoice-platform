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
import { EtaInvoiceService, SubmissionAttemptResponse, EtaInvoice } from './services/eta-invoice.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { DetailGridComponent, DetailGridRow } from '../../documents/shared/detail-grid.component';
import { DocumentDetailShellComponent, DocumentDetailTile } from '../../documents/shared/document-detail-shell.component';
import { isAllowed, invoiceTransitions } from '../shared/generated/eta-states';
import { CancelEtaDialogComponent } from '../../shared/dialogs/cancel-eta-dialog.component';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { toSignal, toObservable } from '@angular/core/rxjs-interop';
import { BehaviorSubject, of, switchMap } from 'rxjs';

@Component({
  selector: 'app-invoice-number-dialog',
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
export class InvoiceNumberDialogComponent {
  invoiceNumber = '';
}

@Component({
  selector: 'app-eta-invoice-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, MatButtonModule, MatIconModule,
            MatCardModule, MatTableModule, HasPermissionDirective,
            DetailGridComponent, DocumentDetailShellComponent],
  template: `
    <app-document-detail-shell
      *ngIf="invoice()?.body as inv; else loading"
      authority="ETA"
      eyebrow="ETA Invoice"
      [documentNumber]="inv.invoiceNumber"
      [status]="inv.state"
      [subline]="subline(inv)"
      [backLink]="['/invoices/eta']"
      backLabel="ETA invoices"
      [tiles]="summaryTiles(inv)"
      [lineCount]="inv.lines.length"
      [submissions]="submissions()"
      [artifactTypes]="artifactTypes"
      [companyId]="inv.companyId"
      [docId]="inv.id"
      [getArtifactUrl]="getArtifactUrlFn()">
      <ng-container actions>
        <ng-container *appHasPermission="['INVOICE', 'EDIT']">
          <button mat-raised-button color="primary"
                  *ngIf="isActionAllowed(inv.state, 'EDIT')"
                  [routerLink]="['/invoices/eta', inv.id, 'edit']">Edit</button>
        </ng-container>
        <ng-container *appHasPermission="['INVOICE', 'SUBMIT']">
          <button mat-raised-button color="accent"
                  *ngIf="isActionAllowed(inv.state, 'SUBMIT')"
                  (click)="submit()">Submit</button>
        </ng-container>
        <ng-container *appHasPermission="['INVOICE', 'CREATE']">
          <button mat-raised-button color="warn"
                  *ngIf="inv.state === 'REJECTED'"
                  (click)="cloneAsDraft()">Create new draft</button>
        </ng-container>
        <ng-container *appHasPermission="['INVOICE', 'REFRESH']">
          <button mat-raised-button
                  *ngIf="isActionAllowed(inv.state, 'CHECK_STATUS')"
                  (click)="checkStatus()">Check Status</button>
        </ng-container>
        <ng-container *appHasPermission="['INVOICE', 'CANCEL']">
          <button mat-raised-button color="warn"
                  *ngIf="isActionAllowed(inv.state, 'CANCEL')"
                  (click)="cancel()">Cancel</button>
        </ng-container>
        <ng-container *appHasPermission="['INVOICE', 'SUBMIT']">
          <button mat-raised-button color="accent"
                  *ngIf="isActionAllowed(inv.state, 'RETRY')"
                  (click)="retry()">Retry</button>
        </ng-container>
      </ng-container>

      <ng-container tab-overview>
        <div class="dd-overview-layout">
          <app-detail-grid [rows]="overviewRows(inv)"></app-detail-grid>
          <div class="dd-side-stack">
            <mat-card class="dd-panel">
              <mat-card-header><mat-card-title>Authority References</mat-card-title></mat-card-header>
              <mat-card-content>
                <app-detail-grid [rows]="referenceRows(inv)"></app-detail-grid>
              </mat-card-content>
            </mat-card>
          </div>
        </div>
      </ng-container>

      <ng-container tab-lines>
        <div *ngIf="inv.lines?.length; else noLines" class="dd-table-wrap">
          <table mat-table [dataSource]="inv.lines" class="dd-lines-table">
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
            <ng-container matColumnDef="total">
              <th mat-header-cell *matHeaderCellDef>Total</th>
              <td mat-cell *matCellDef="let l" class="dd-num">{{ l.total }}</td>
              <td mat-footer-cell *matFooterCellDef class="dd-num dd-total-cell">{{ sumLineTotal(inv.lines) }}</td>
            </ng-container>
            <ng-container matColumnDef="taxes">
              <th mat-header-cell *matHeaderCellDef>Taxes</th>
              <td mat-cell *matCellDef="let l">
                <span *ngFor="let t of l.taxes; let last = last">
                  {{ t.taxType }}: {{ t.taxAmount }}<span *ngIf="!last">, </span>
                </span>
              </td>
              <td mat-footer-cell *matFooterCellDef class="dd-num dd-total-cell">{{ sumLineTaxes(inv.lines) }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="lineColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: lineColumns;"></tr>
            <tr mat-footer-row *matFooterRowDef="lineColumns"></tr>
          </table>
        </div>
        <ng-template #noLines>
          <div class="dd-empty-lines">No line items on this invoice.</div>
        </ng-template>
      </ng-container>
    </app-document-detail-shell>

    <ng-template #loading>
      <div class="dd-loading">Loading invoice detail...</div>
    </ng-template>
  `,
  styleUrls: ['../../documents/shared/document-detail-content.scss']
})
export class EtaInvoiceDetailComponent {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private service = inject(EtaInvoiceService);
  private sessionCtx = inject(SessionContextService);
  private dialog = inject(MatDialog);
  private toast = inject(ToastNotificationService);
  context = toSignal(this.sessionCtx.context$, { initialValue: null });

  lineColumns = ['code', 'desc', 'qty', 'total', 'taxes'];
  artifactTypes = ['SIGNED_JSON', 'ETA_RESPONSE'];
  private refresh$ = new BehaviorSubject<void>(undefined);

  invoice = toSignal(
    this.refresh$.pipe(switchMap(() => {
      const id = this.route.snapshot.paramMap.get('id')!;
      return this.service.getById(id);
    })), { initialValue: null as unknown as HttpResponse<EtaInvoice> }
  );

  submissions = toSignal(
    toObservable(this.invoice).pipe(
      switchMap(inv => {
        const body = inv?.body;
        if (!body?.companyId || !body?.id) return of([] as SubmissionAttemptResponse[]);
        return this.service.getSubmissions(body.companyId, body.id);
      })
    ), { initialValue: [] as SubmissionAttemptResponse[] }
  );

  isActionAllowed(state: string, action: string): boolean {
    return isAllowed(state, action, invoiceTransitions);
  }

  summaryTiles(inv: EtaInvoice): DocumentDetailTile[] {
    return [
      { label: 'Total Sales', value: inv.totalSalesAmount },
      { label: 'Net Amount', value: inv.netAmount },
      { label: 'Total', value: inv.totalAmount, emphasis: true },
    ];
  }

  subline(inv: EtaInvoice): string {
    return [inv.issueDatetime, inv.currency, 'ETA'].filter(Boolean).join(' - ');
  }

  overviewRows(inv: EtaInvoice): DetailGridRow[] {
    return [
      { label: 'State', value: inv.state },
      { label: 'Type', value: inv.documentType },
      { label: 'Issue Date', value: inv.issueDatetime },
      { label: 'Currency', value: inv.currency },
      { label: 'Total Sales', value: inv.totalSalesAmount },
      { label: 'Net Amount', value: inv.netAmount },
      { label: 'Total', value: inv.totalAmount },
    ];
  }

  referenceRows(inv: EtaInvoice): DetailGridRow[] {
    return [
      { label: 'ETA UUID', value: inv.etaUuid, mono: true },
      { label: 'Submission ID', value: inv.etaSubmissionId, mono: true },
    ];
  }

  sumLineTotal(lines: EtaInvoice['lines']): string {
    return this.formatAmount((lines ?? []).reduce((sum, line) => sum + Number(line.total ?? 0), 0));
  }

  sumLineTaxes(lines: EtaInvoice['lines']): string {
    return this.formatAmount((lines ?? []).reduce((lineSum, line) => lineSum
        + (line.taxes ?? []).reduce((taxSum, tax) => taxSum + Number(tax.taxAmount ?? 0), 0), 0));
  }

  private formatAmount(value: unknown): string {
    const numeric = Number(value ?? 0);
    return Number.isFinite(numeric) ? numeric.toFixed(2) : String(value ?? '');
  }

  getArtifactUrlFn(): (type: string) => string {
    const inv = this.invoice()?.body;
    if (!inv) return () => '';
    return (type: string) => this.service.getArtifactUrl(inv.companyId, inv.id, type);
  }

  submit(): void {
    const inv = this.invoice()?.body;
    if (!inv) return;
    const companyId = inv.companyId;
    this.service.submit(companyId, inv.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Submit failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }

  cloneAsDraft(): void {
    const inv = this.invoice()?.body;
    if (!inv) return;
    const companyId = inv.companyId;
    const ref = this.dialog.open(InvoiceNumberDialogComponent, {
      width: '400px',
    });
    ref.afterClosed().subscribe(newNumber => {
      if (newNumber) {
        this.service.cloneAsDraft(companyId, inv.id, newNumber).subscribe({
          next: resp => this.router.navigate(['/invoices/eta', resp.body?.id]),
          error: (err) => this.toast.error('Clone failed: ' + (err?.error?.message || 'Unknown error')),
        });
      }
    });
  }

  checkStatus(): void {
    const inv = this.invoice()?.body;
    if (!inv) return;
    const companyId = inv.companyId;
    this.service.checkStatus(companyId, [inv.id]).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Check status failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }

  cancel(): void {
    const inv = this.invoice()?.body;
    if (!inv) return;
    const companyId = inv.companyId;
    const ref = this.dialog.open(CancelEtaDialogComponent, {
      width: '400px',
      data: { reason: '' },
    });
    ref.afterClosed().subscribe(result => {
      if (result?.reason) {
        this.service.cancel(companyId, inv.id, result.reason).subscribe({
          next: () => this.refresh$.next(),
          error: (err) => this.toast.error('Cancel failed: ' + (err?.error?.message || 'Unknown error')),
        });
      }
    });
  }

  retry(): void {
    const inv = this.invoice()?.body;
    if (!inv) return;
    const companyId = inv.companyId;
    this.service.retry(companyId, inv.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Retry failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }
}
