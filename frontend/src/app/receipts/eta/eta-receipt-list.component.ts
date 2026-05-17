import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatToolbarModule } from '@angular/material/toolbar';
import { EtaReceiptService, EtaReceiptListResult } from './services/eta-receipt.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { BulkStatusCheckDialogComponent, BulkStatusDialogData } from '../../invoices/shared/bulk-status-check.dialog';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { BehaviorSubject, switchMap } from 'rxjs';

const RECEIPT_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: 'r', label: 'Standard (r)' },
  { value: 'rr', label: 'Return (rr)' },
  { value: 'rrwr', label: 'Return w/ Replace (rrwr)' },
  { value: 'cr', label: 'Cancellation (cr)' },
  { value: 'crr', label: 'Cancellation Refund (crr)' },
  { value: 'gs', label: 'General Sale (gs)' },
  { value: 'gsr', label: 'General Sale Return (gsr)' },
  { value: 'rt', label: 'Refund (rt)' },
  { value: 'rtr', label: 'Refund w/ Replace (rtr)' },
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

@Component({
  selector: 'app-eta-receipt-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, MatTableModule,
            MatPaginatorModule, MatButtonModule, MatIconModule, MatChipsModule,
            MatFormFieldModule, MatSelectModule, MatInputModule,
            MatCheckboxModule, MatDialogModule, MatToolbarModule,
            HasPermissionDirective],
  template: `
    <div class="list-container">
      <div class="list-header">
        <h2>ETA Receipts</h2>
        <button mat-raised-button color="primary" routerLink="new"
                *appHasPermission="['RECEIPT', 'CREATE']">New Receipt</button>
      </div>

      <div class="filters">
        <mat-form-field appearance="outline">
          <mat-label>Company</mat-label>
          <mat-select [(ngModel)]="companyFilter"
                      (selectionChange)="refresh$.next()">
            <mat-option value="">All</mat-option>
            @for (c of context()?.companies ?? []; track c.companyId) {
              <mat-option [value]="c.companyId">{{ c.companyNameEn }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="statusFilter" (selectionChange)="refresh$.next()">
            <mat-option value="">All</mat-option>
            <mat-option value="DRAFT">Draft</mat-option>
            <mat-option value="SUBMITTING">Submitting</mat-option>
            <mat-option value="VALID">Valid</mat-option>
            <mat-option value="REJECTED">Rejected</mat-option>
            <mat-option value="SUBMISSION_AMBIGUOUS">Ambiguous</mat-option>
            <mat-option value="CANCELLED">Cancelled</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select [(ngModel)]="receiptTypeFilter" (selectionChange)="refresh$.next()">
            <mat-option value="">All</mat-option>
            @for (t of typeOptions; track t.value) {
              <mat-option [value]="t.value">{{ t.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>From</mat-label>
          <input matInput type="date" [(ngModel)]="dateFrom" (change)="refresh$.next()">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>To</mat-label>
          <input matInput type="date" [(ngModel)]="dateTo" (change)="refresh$.next()">
        </mat-form-field>
      </div>

      <mat-toolbar *ngIf="selectedIds.size > 0" class="bulk-toolbar">
        <span>{{ selectedIds.size }} selected</span>
        <span class="spacer"></span>
        <ng-container *appHasPermission="['RECEIPT', 'REFRESH']">
          <button mat-raised-button color="accent" (click)="bulkCheckStatus()">
            <mat-icon>refresh</mat-icon> Check Status
          </button>
        </ng-container>
        <button mat-button (click)="clearSelection()">Clear</button>
      </mat-toolbar>

      <table mat-table [dataSource]="receipts()?.items ?? []">
        <ng-container matColumnDef="select">
          <th mat-header-cell *matHeaderCellDef>
            <mat-checkbox (change)="toggleAll($event.checked)"></mat-checkbox>
          </th>
          <td mat-cell *matCellDef="let row">
            <mat-checkbox [checked]="selectedIds.has(row.id)"
                          (change)="toggleSelect(row.id, $event.checked)"></mat-checkbox>
          </td>
        </ng-container>
        <ng-container matColumnDef="receiptNumber">
          <th mat-header-cell *matHeaderCellDef>Receipt Number</th>
          <td mat-cell *matCellDef="let row">{{ row.receiptNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="company">
          <th mat-header-cell *matHeaderCellDef>Company</th>
          <td mat-cell *matCellDef="let row">{{ companyName(row.companyId) }}</td>
        </ng-container>
        <ng-container matColumnDef="documentType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let row">{{ row.documentType }}</td>
        </ng-container>
        <ng-container matColumnDef="issueDatetime">
          <th mat-header-cell *matHeaderCellDef>Issue Date</th>
          <td mat-cell *matCellDef="let row">{{ row.issueDatetime | date:'short' }}</td>
        </ng-container>
        <ng-container matColumnDef="totalAmount">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let row">{{ row.totalAmount }} {{ row.currency }}</td>
        </ng-container>
        <ng-container matColumnDef="state">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let row">
            <mat-chip>{{ row.state }}</mat-chip>
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let row">
            <ng-container *appHasPermission="['RECEIPT', 'VIEW']">
              <button mat-icon-button [routerLink]="['/receipts/eta', row.id]">
                <mat-icon>visibility</mat-icon>
              </button>
            </ng-container>
            <ng-container *appHasPermission="['RECEIPT', 'EDIT']">
              <button mat-icon-button *ngIf="row.state === 'DRAFT'"
                      [routerLink]="['/receipts/eta', row.id, 'edit']">
                <mat-icon>edit</mat-icon>
              </button>
            </ng-container>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns;"></tr>
      </table>
      <mat-paginator [length]="receipts()?.totalElements ?? 0"
        [pageSize]="50" [pageIndex]="currentPage"
        (page)="onPage($event)"></mat-paginator>
    </div>
  `,
  styles: [`
    .list-container { padding: 16px; }
    .list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .filters { display: flex; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
    .filters mat-form-field { width: 180px; }
    .bulk-toolbar { margin-bottom: 8px; }
    .spacer { flex: 1 1 auto; }
  `]
})
export class EtaReceiptListComponent {
  private service = inject(EtaReceiptService);
  private sessionCtx = inject(SessionContextService);
  private dialog = inject(MatDialog);
  private toast = inject(ToastNotificationService);
  context = toSignal(this.sessionCtx.context$, { initialValue: null });
  refresh$ = new BehaviorSubject<void>(undefined);

  typeOptions = RECEIPT_TYPE_OPTIONS;
  columns = ['select', 'receiptNumber', 'company', 'documentType', 'issueDatetime',
      'totalAmount', 'state', 'actions'];
  currentPage = 0;
  statusFilter = '';
  companyFilter = '';
  receiptTypeFilter = '';
  dateFrom = '';
  dateTo = '';
  selectedIds = new Set<string>();

  receipts = toSignal(
    this.refresh$.pipe(switchMap(() => this.loadReceipts())),
    { initialValue: { items: [] as EtaReceiptListResult['items'],
        page: 0, size: 50, totalElements: 0 } }
  );

  private loadReceipts() {
    const ctx = this.context();
    const pathCompanyId = ctx?.activeCompanyId ?? '';
    return this.service.list(pathCompanyId, {
      status: this.statusFilter || undefined,
      companyId: this.companyFilter || undefined,
      receiptType: this.receiptTypeFilter || undefined,
      dateFrom: this.dateFrom || undefined,
      dateTo: this.dateTo || undefined,
      page: this.currentPage,
      size: 50,
    });
  }

  companyName(id: string): string {
    const companies = this.context()?.companies ?? [];
    return companies.find(c => c.companyId === id)?.companyNameEn ?? id;
  }

  onPage(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.refresh$.next();
  }

  toggleSelect(id: string, checked: boolean): void {
    if (checked) this.selectedIds.add(id);
    else this.selectedIds.delete(id);
  }

  toggleAll(checked: boolean): void {
    if (checked) {
      (this.receipts()?.items ?? []).forEach(r => this.selectedIds.add(r.id));
    } else {
      this.selectedIds.clear();
    }
  }

  clearSelection(): void {
    this.selectedIds.clear();
  }

  bulkCheckStatus(): void {
    const ctx = this.context();
    const companyId = ctx?.activeCompanyId ?? '';
    this.service.checkStatus(companyId, Array.from(this.selectedIds)).subscribe({
      next: resp => {
        this.dialog.open(BulkStatusCheckDialogComponent, {
          width: '700px',
          data: { outcomes: resp.results } as BulkStatusDialogData,
        });
        this.selectedIds.clear();
        this.refresh$.next();
      },
      error: (err) => this.toast.error('Bulk status check failed: ' + (err?.error?.message || 'Unknown error')),
    });
  }
}
