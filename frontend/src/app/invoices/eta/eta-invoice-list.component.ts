import { Component, inject, signal } from '@angular/core';
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
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { EtaInvoiceService, EtaInvoiceListResult } from './services/eta-invoice.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { BulkStatusCheckDialogComponent, BulkStatusDialogData } from '../../documents/shared/bulk-status-check.dialog';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { BehaviorSubject, catchError, of, switchMap, tap } from 'rxjs';

@Component({
  selector: 'app-eta-invoice-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, MatTableModule,
            MatPaginatorModule, MatButtonModule, MatIconModule, MatChipsModule,
            MatFormFieldModule, MatSelectModule, MatInputModule,
            MatCheckboxModule, MatDialogModule, MatToolbarModule,
            MatProgressBarModule, HasPermissionDirective],
  template: `
    <div class="list-container">
      <div class="list-header">
        <h2>ETA Invoices</h2>
        <button mat-raised-button color="primary" routerLink="new"
                *appHasPermission="['INVOICE', 'CREATE']">New Invoice</button>
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
          <mat-label>From</mat-label>
          <input matInput type="date" [(ngModel)]="dateFrom" (change)="refresh$.next()">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>To</mat-label>
          <input matInput type="date" [(ngModel)]="dateTo" (change)="refresh$.next()">
        </mat-form-field>
      </div>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate"></mat-progress-bar>
        <div class="state-banner">Loading invoices…</div>
      } @else if (error()) {
        <div class="state-banner error">
          Could not load invoices. Please try again.
          <div><button mat-button color="primary" (click)="refresh$.next()">Retry</button></div>
        </div>
      } @else if ((invoices()?.items ?? []).length === 0) {
        <div class="state-banner">No documents match the current filters.</div>
      } @else {
        <mat-toolbar *ngIf="selectedIds.size > 0" class="bulk-toolbar">
          <span>{{ selectedIds.size }} selected</span>
          <span class="spacer"></span>
          <ng-container *appHasPermission="['INVOICE', 'REFRESH']">
            <button mat-raised-button color="accent" (click)="bulkCheckStatus()">
              <mat-icon>refresh</mat-icon> Check Status
            </button>
          </ng-container>
          <button mat-button (click)="clearSelection()">Clear</button>
        </mat-toolbar>

        <table mat-table [dataSource]="invoices()?.items ?? []">
        <ng-container matColumnDef="select">
          <th mat-header-cell *matHeaderCellDef>
            <mat-checkbox (change)="toggleAll($event.checked)"></mat-checkbox>
          </th>
          <td mat-cell *matCellDef="let row">
            <mat-checkbox [checked]="selectedIds.has(row.id)"
                          (change)="toggleSelect(row.id, $event.checked)"></mat-checkbox>
          </td>
        </ng-container>
        <ng-container matColumnDef="invoiceNumber">
          <th mat-header-cell *matHeaderCellDef>Document Number</th>
          <td mat-cell *matCellDef="let row">{{ row.invoiceNumber }}</td>
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
            <ng-container *appHasPermission="['INVOICE', 'VIEW']">
              <button mat-icon-button [routerLink]="['/invoices/eta', row.id]">
                <mat-icon>visibility</mat-icon>
              </button>
            </ng-container>
            <ng-container *appHasPermission="['INVOICE', 'EDIT']">
              <button mat-icon-button *ngIf="row.state === 'DRAFT'"
                      [routerLink]="['/invoices/eta', row.id, 'edit']">
                <mat-icon>edit</mat-icon>
              </button>
            </ng-container>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns;"></tr>
      </table>
      <mat-paginator [length]="invoices()?.totalElements ?? 0"
        [pageSize]="50" [pageIndex]="currentPage"
        (page)="onPage($event)"></mat-paginator>
      }
    </div>
  `,
  styles: [`
    .list-container { padding: 16px; }
    .list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .filters { display: flex; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
    .filters mat-form-field { width: 180px; }
    .bulk-toolbar { margin-bottom: 8px; }
    .spacer { flex: 1 1 auto; }
    .state-banner { padding: 24px; text-align: center; color: #666; }
    .state-banner.error { color: #c62828; }
  `]
})
export class EtaInvoiceListComponent {
  private service = inject(EtaInvoiceService);
  private sessionCtx = inject(SessionContextService);
  private dialog = inject(MatDialog);
  private toast = inject(ToastNotificationService);
  context = toSignal(this.sessionCtx.context$, { initialValue: null });
  refresh$ = new BehaviorSubject<void>(undefined);

  loading = signal(true);
  error = signal(false);

  columns = ['select', 'invoiceNumber', 'company', 'documentType', 'issueDatetime',
      'totalAmount', 'state', 'actions'];
  currentPage = 0;
  statusFilter = '';
  companyFilter = '';
  dateFrom = '';
  dateTo = '';
  selectedIds = new Set<string>();

  private readonly emptyResult: EtaInvoiceListResult = {
    items: [], page: 0, size: 50, totalElements: 0,
  };

  invoices = toSignal(
    this.refresh$.pipe(
      tap(() => { this.loading.set(true); this.error.set(false); }),
      switchMap(() => this.loadInvoices().pipe(
        catchError(() => { this.error.set(true); return of(this.emptyResult); }),
      )),
      tap(() => this.loading.set(false)),
    ),
    { initialValue: this.emptyResult },
  );

  private loadInvoices() {
    return this.service.list({
      status: this.statusFilter || undefined,
      companyId: this.companyFilter || undefined,
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
      (this.invoices()?.items ?? []).forEach(i => this.selectedIds.add(i.id));
    } else {
      this.selectedIds.clear();
    }
  }

  clearSelection(): void {
    this.selectedIds.clear();
  }

  bulkCheckStatus(): void {
    const items = this.invoices()?.items ?? [];
    const ids = Array.from(this.selectedIds);
    const companyId = items.find(i => ids.includes(i.id))?.companyId ?? this.companyFilter ?? '';
    this.service.checkStatus(companyId, ids).subscribe({
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
