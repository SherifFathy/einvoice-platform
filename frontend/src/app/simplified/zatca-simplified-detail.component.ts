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
import { MatChipsModule } from '@angular/material/chips';
import { FormsModule } from '@angular/forms';
import { ZatcaSimplifiedService, ZatcaSimplifiedDocument, SubmissionAttemptResponse } from './services/zatca-simplified.service';
import { SessionContextService } from '../shared/services/session-context.service';
import { HasPermissionDirective } from '../shared/directives/has-permission.directive';
import { SubmissionHistoryComponent } from '../documents/shared/submission-history.component';
import { ArtifactDownloadComponent } from '../documents/shared/artifact-download.component';
import { ToastNotificationService } from '../shared/services/toast.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { BehaviorSubject, switchMap } from 'rxjs';

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
            MatCardModule, MatTableModule, MatChipsModule,
            HasPermissionDirective, MatDialogModule,
            SubmissionHistoryComponent, ArtifactDownloadComponent],
  template: `
    <div class="detail-container" *ngIf="document()?.body as doc">
      <div class="detail-header">
        <h2>Simplified {{ doc.invoiceNumber }}</h2>
        <div>
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
        </div>
      </div>

      <mat-card>
        <mat-card-content>
          <p><strong>Status:</strong> <mat-chip>{{ doc.status }}</mat-chip></p>
          <p><strong>Reporting Status:</strong>
            <mat-chip *ngIf="doc.reportingStatus" color="accent">{{ doc.reportingStatus }}</mat-chip>
            <span *ngIf="!doc.reportingStatus">&mdash;</span>
          </p>
          <p><strong>Type:</strong> {{ doc.invoiceTypeCode }}</p>
          <p><strong>Transaction Type:</strong> {{ doc.transactionTypeCode }}</p>
          <p><strong>Issue Date:</strong> {{ doc.issueDate }} {{ doc.issueTime }}</p>
          <p><strong>Currency:</strong> {{ doc.currency }}</p>
          <p><strong>Tax Exclusive:</strong> {{ doc.taxExclusiveAmount }}</p>
          <p><strong>VAT:</strong> {{ doc.taxAmount }}</p>
          <p><strong>Tax Inclusive:</strong> {{ doc.taxInclusiveAmount }}</p>
          <p><strong>Payable:</strong> {{ doc.payableAmount }}</p>
          <p *ngIf="doc.zatcaUuid"><strong>ZATCA UUID:</strong> {{ doc.zatcaUuid }}</p>

          <div *ngIf="doc.invoiceCounterValue != null" class="chain-snapshot">
            <h4>Chain Snapshot</h4>
            <p><strong>Counter:</strong> {{ doc.invoiceCounterValue }}</p>
            <p><strong>Hash:</strong> <code>{{ doc.invoiceHash }}</code></p>
            <p><strong>Previous Hash:</strong> <code>{{ doc.previousInvoiceHash }}</code></p>
          </div>

          <div *ngIf="doc.qrCodeBase64" class="qr-preview">
            <h4>QR Code</h4>
            <img [src]="'data:image/png;base64,' + doc.qrCodeBase64" alt="QR Code" width="200" height="200">
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card *ngIf="doc.lines?.length" class="lines-card">
        <mat-card-header><mat-card-title>Line Items</mat-card-title></mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="doc.lines">
            <ng-container matColumnDef="code">
              <th mat-header-cell *matHeaderCellDef>Code</th>
              <td mat-cell *matCellDef="let l">{{ l.itemCode }}</td>
            </ng-container>
            <ng-container matColumnDef="desc">
              <th mat-header-cell *matHeaderCellDef>Description</th>
              <td mat-cell *matCellDef="let l">{{ l.description }}</td>
            </ng-container>
            <ng-container matColumnDef="qty">
              <th mat-header-cell *matHeaderCellDef>Qty</th>
              <td mat-cell *matCellDef="let l">{{ l.quantity }}</td>
            </ng-container>
            <ng-container matColumnDef="price">
              <th mat-header-cell *matHeaderCellDef>Unit Price</th>
              <td mat-cell *matCellDef="let l">{{ l.unitPrice }}</td>
            </ng-container>
            <ng-container matColumnDef="net">
              <th mat-header-cell *matHeaderCellDef>Net</th>
              <td mat-cell *matCellDef="let l">{{ l.netAmount }}</td>
            </ng-container>
            <ng-container matColumnDef="vat">
              <th mat-header-cell *matHeaderCellDef>VAT</th>
              <td mat-cell *matCellDef="let l">{{ l.vatCategoryCode }} ({{ l.vatRate }}%) = {{ l.vatAmount }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="lineColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: lineColumns;"></tr>
          </table>
        </mat-card-content>
      </mat-card>

      <app-submission-history [attempts]="submissions()"></app-submission-history>

      <app-artifact-download
        [artifactTypes]="artifactTypes"
        [companyId]="context()?.activeCompanyId ?? ''"
        [docId]="document()?.body?.id ?? ''"
        [getArtifactUrl]="getArtifactUrlFn()">
      </app-artifact-download>
    </div>
  `,
  styles: [`
    .detail-container { padding: 16px; }
    .detail-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .lines-card { margin-top: 16px; }
    .chain-snapshot { margin-top: 16px; padding: 12px; background: #f5f5f5; border-radius: 4px; }
    .chain-snapshot code { font-size: 11px; word-break: break-all; }
    .qr-preview { margin-top: 16px; }
  `]
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
      const companyId = this.context()?.activeCompanyId ?? '';
      return this.service.getById(companyId, id);
    })), { initialValue: null as unknown as HttpResponse<ZatcaSimplifiedDocument> }
  );

  submissions = toSignal(
    this.route.paramMap.pipe(
      switchMap(params => {
        const id = params.get('id')!;
        const companyId = this.context()?.activeCompanyId ?? '';
        return this.service.getSubmissions(companyId, id);
      })
    ), { initialValue: [] as SubmissionAttemptResponse[] }
  );

  getArtifactUrlFn(): (type: string) => string {
    const doc = this.document()?.body;
    if (!doc) return () => '';
    const companyId = this.context()?.activeCompanyId ?? '';
    return (type: string) => this.service.getArtifactUrl(companyId, doc.id, type);
  }

  submit(): void {
    const doc = this.document()?.body;
    if (!doc) return;
    const companyId = this.context()?.activeCompanyId ?? '';
    this.service.submit(companyId, doc.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Submit failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }

  cancel(): void {
    const doc = this.document()?.body;
    if (!doc) return;
    const companyId = this.context()?.activeCompanyId ?? '';
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
    const companyId = this.context()?.activeCompanyId ?? '';
    this.service.retry(companyId, doc.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Retry failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }

  checkStatus(): void {
    const doc = this.document()?.body;
    if (!doc) return;
    const companyId = this.context()?.activeCompanyId ?? '';
    this.service.checkStatus(companyId, doc.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Check status failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }

  cloneAsDraft(): void {
    const doc = this.document()?.body;
    if (!doc) return;
    const companyId = this.context()?.activeCompanyId ?? '';
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
}
