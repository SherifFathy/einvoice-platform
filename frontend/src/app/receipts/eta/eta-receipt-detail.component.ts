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
import { SubmissionHistoryComponent } from '../../documents/shared/submission-history.component';
import { ArtifactDownloadComponent } from '../../documents/shared/artifact-download.component';
import { isAllowed, receiptTransitions } from '../../invoices/shared/generated/eta-states';
import { CancelEtaDialogComponent } from '../../shared/dialogs/cancel-eta-dialog.component';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { BehaviorSubject, switchMap } from 'rxjs';

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
            SubmissionHistoryComponent, ArtifactDownloadComponent],
  template: `
    <div class="detail-container" *ngIf="receipt()?.body as rec">
      <div class="detail-header">
        <h2>Receipt {{ rec.receiptNumber }}</h2>
        <div>
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
        </div>
      </div>

      <mat-card>
        <mat-card-content>
          <p><strong>State:</strong> {{ rec.state }}</p>
          <p><strong>Type:</strong> {{ rec.documentType }}</p>
          <p><strong>Issue Date:</strong> {{ rec.issueDatetime | date:'short' }}</p>
          <p><strong>Currency:</strong> {{ rec.currency }}</p>
          <p><strong>POS Serial:</strong> {{ rec.posSerial }}</p>
          <p><strong>Payment Method:</strong> {{ rec.paymentMethod }}</p>
          <p><strong>Total Sales:</strong> {{ rec.totalSalesAmount }}</p>
          <p><strong>Net Amount:</strong> {{ rec.netAmount }}</p>
          <p><strong>Total:</strong> {{ rec.totalAmount }}</p>
          <p *ngIf="rec.originalReceiptId"><strong>Original Receipt:</strong> {{ rec.originalReceiptId }}</p>
          <p *ngIf="rec.etaReceiptUuid"><strong>ETA UUID:</strong> {{ rec.etaReceiptUuid }}</p>
          <p *ngIf="rec.etaSubmissionId"><strong>Submission ID:</strong> {{ rec.etaSubmissionId }}</p>
        </mat-card-content>
      </mat-card>

      <mat-card *ngIf="rec.lines?.length" class="lines-card">
        <mat-card-header><mat-card-title>Line Items</mat-card-title></mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="rec.lines">
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
            <ng-container matColumnDef="total">
              <th mat-header-cell *matHeaderCellDef>Total</th>
              <td mat-cell *matCellDef="let l">{{ l.total }}</td>
            </ng-container>
            <ng-container matColumnDef="taxes">
              <th mat-header-cell *matHeaderCellDef>Taxes</th>
              <td mat-cell *matCellDef="let l">
                <span *ngFor="let t of l.taxes; let last = last">
                  {{ t.taxType }}: {{ t.taxAmount }}<span *ngIf="!last">, </span>
                </span>
              </td>
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
        [docId]="receipt()?.body?.id ?? ''"
        [getArtifactUrl]="getArtifactUrlFn()">
      </app-artifact-download>
    </div>
  `,
  styles: [`
    .detail-container { padding: 16px; }
    .detail-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .lines-card { margin-top: 16px; }
  `]
})
export class EtaReceiptDetailComponent {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private service = inject(EtaReceiptService);
  private sessionCtx = inject(SessionContextService);
  private dialog = inject(MatDialog);
  private toast = inject(ToastNotificationService);
  context = toSignal(this.sessionCtx.context$, { initialValue: null });

  lineColumns = ['code', 'desc', 'qty', 'total', 'taxes'];
  artifactTypes = ['SIGNED_JSON', 'ETA_RESPONSE'];
  private refresh$ = new BehaviorSubject<void>(undefined);

  receipt = toSignal(
    this.refresh$.pipe(switchMap(() => {
      const id = this.route.snapshot.paramMap.get('id')!;
      const companyId = this.context()?.activeCompanyId ?? '';
      return this.service.getById(companyId, id);
    })), { initialValue: null as unknown as HttpResponse<EtaReceipt> }
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

  isActionAllowed(state: string, action: string): boolean {
    return isAllowed(state, action, receiptTransitions);
  }

  getArtifactUrlFn(): (type: string) => string {
    const rec = this.receipt()?.body;
    if (!rec) return () => '';
    const companyId = this.context()?.activeCompanyId ?? '';
    return (type: string) => this.service.getArtifactUrl(companyId, rec.id, type);
  }

  submit(): void {
    const rec = this.receipt()?.body;
    if (!rec) return;
    const companyId = this.context()?.activeCompanyId ?? '';
    this.service.submit(companyId, rec.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Submit failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }

  cloneAsDraft(): void {
    const rec = this.receipt()?.body;
    if (!rec) return;
    const companyId = this.context()?.activeCompanyId ?? '';
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
    const companyId = this.context()?.activeCompanyId ?? '';
    this.service.checkStatus(companyId, [rec.id]).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Check status failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }

  cancel(): void {
    const rec = this.receipt()?.body;
    if (!rec) return;
    const companyId = this.context()?.activeCompanyId ?? '';
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
    const companyId = this.context()?.activeCompanyId ?? '';
    this.service.retry(companyId, rec.id).subscribe({
      next: () => this.refresh$.next(),
      error: (err) => this.toast.error('Retry failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }
}
