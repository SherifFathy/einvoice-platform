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
import { MatCheckboxChange, MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ZatcaSimplifiedService, ZatcaSimplifiedListResult } from './services/zatca-simplified.service';
import { SessionContextService } from '../shared/services/session-context.service';
import { HasPermissionDirective } from '../shared/directives/has-permission.directive';
import {
  BranchLookupService,
  groupBranchesByCompany,
} from '../shared/services/branch-lookup.service';
import { BulkStatusCheckDialogComponent } from '../documents/shared/bulk-status-check.dialog';
import { toSignal } from '@angular/core/rxjs-interop';
import { BehaviorSubject, catchError, of, switchMap, tap } from 'rxjs';

@Component({
  selector: 'app-zatca-simplified-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, MatTableModule,
            MatPaginatorModule, MatButtonModule, MatIconModule, MatChipsModule,
            MatFormFieldModule, MatSelectModule, MatInputModule,
            MatCheckboxModule, MatDialogModule, MatProgressBarModule,
            HasPermissionDirective],
  template: `
    <div class="list-container">
      <div class="list-header">
        <h2>ZATCA Simplified (B2C)</h2>
        <div>
          <ng-container *appHasPermission="['SIMPLIFIED', 'REFRESH']">
            <button mat-raised-button color="accent"
                    *ngIf="selectedIds.size > 0"
                    (click)="bulkCheckStatus()">
              <mat-icon>sync</mat-icon> Check Status ({{ selectedIds.size }})
            </button>
          </ng-container>
          <button mat-raised-button color="primary" routerLink="new"
                  *appHasPermission="['SIMPLIFIED', 'CREATE']">New Simplified</button>
        </div>
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
          <mat-label>Branch</mat-label>
          <mat-select [(ngModel)]="branchFilter"
                      (selectionChange)="refresh$.next()">
            <mat-option value="">All branches</mat-option>
            @for (group of branchesByCompany(); track group.companyId) {
              <mat-optgroup [label]="group.companyNameEn">
                @for (branch of group.branches; track branch.id) {
                  <mat-option [value]="branch.id">{{ branch.nameEn }}</mat-option>
                }
              </mat-optgroup>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="statusFilter" (selectionChange)="refresh$.next()">
            <mat-option value="">All</mat-option>
            <mat-option value="DRAFT">Draft</mat-option>
            <mat-option value="SUBMITTING">Submitting</mat-option>
            <mat-option value="SUBMITTED">Submitted</mat-option>
            <mat-option value="IN_REVIEW">In Review</mat-option>
            <mat-option value="ACCEPTED">Accepted</mat-option>
            <mat-option value="REJECTED">Rejected</mat-option>
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
        <div class="state-banner">Loading documents…</div>
      } @else if (error()) {
        <div class="state-banner error">
          Could not load documents. Please try again.
          <div><button mat-button color="primary" (click)="refresh$.next()">Retry</button></div>
        </div>
      } @else if ((documents()?.items ?? []).length === 0) {
        <div class="state-banner">No documents match the current filters.</div>
      } @else {
      <table mat-table [dataSource]="documents()?.items ?? []">
        <ng-container matColumnDef="select">
          <th mat-header-cell *matHeaderCellDef>
            <mat-checkbox (change)="toggleAll($event)"
                          [checked]="allSelected()"
                          [indeterminate]="someSelected()">
            </mat-checkbox>
          </th>
          <td mat-cell *matCellDef="let row">
            <mat-checkbox [checked]="selectedIds.has(row.id)"
                          (change)="toggle(row.id, $event)">
            </mat-checkbox>
          </td>
        </ng-container>
        <ng-container matColumnDef="invoiceNumber">
          <th mat-header-cell *matHeaderCellDef>Document Number</th>
          <td mat-cell *matCellDef="let row">
            <ng-container *appHasPermission="['SIMPLIFIED', 'VIEW']; else plainInvoiceNumber">
              <a [routerLink]="['/simplified', row.id]" class="doc-link">
                {{ row.invoiceNumber }}
              </a>
            </ng-container>
            <ng-template #plainInvoiceNumber>{{ row.invoiceNumber }}</ng-template>
          </td>
        </ng-container>
        <ng-container matColumnDef="company">
          <th mat-header-cell *matHeaderCellDef>Company</th>
          <td mat-cell *matCellDef="let row">{{ companyName(row.companyId) }}</td>
        </ng-container>
        <ng-container matColumnDef="issueDate">
          <th mat-header-cell *matHeaderCellDef>Issue Date</th>
          <td mat-cell *matCellDef="let row">{{ row.issueDate }}</td>
        </ng-container>
        <ng-container matColumnDef="total">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let row">{{ row.taxInclusiveAmount }} {{ row.currency }}</td>
        </ng-container>
        <ng-container matColumnDef="reportingStatus">
          <th mat-header-cell *matHeaderCellDef>Reporting Status</th>
          <td mat-cell *matCellDef="let row">
            <mat-chip *ngIf="row.reportingStatus">{{ row.reportingStatus }}</mat-chip>
          </td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let row">
            <mat-chip>{{ row.status }}</mat-chip>
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let row">
            <ng-container *appHasPermission="['SIMPLIFIED', 'VIEW']">
              <button mat-icon-button [routerLink]="['/simplified', row.id]">
                <mat-icon>visibility</mat-icon>
              </button>
            </ng-container>
            <ng-container *appHasPermission="['SIMPLIFIED', 'EDIT']">
              <button mat-icon-button *ngIf="row.status === 'DRAFT'"
                      [routerLink]="['/simplified', row.id, 'edit']">
                <mat-icon>edit</mat-icon>
              </button>
            </ng-container>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns;"></tr>
      </table>
      <mat-paginator [length]="documents()?.totalElements ?? 0"
        [pageSize]="50" [pageIndex]="currentPage"
        (page)="onPage($event)"></mat-paginator>
      }
    </div>
  `,
  styles: [`
    .list-container { padding: 16px; }
    .list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .list-header div { display: flex; gap: 8px; align-items: center; }
    .filters { display: flex; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
    .filters mat-form-field { width: 180px; }
    .doc-link { color: #1976d2; font-weight: 500; text-decoration: none; }
    .doc-link:hover { text-decoration: underline; }
    .state-banner { padding: 24px; text-align: center; color: #666; }
    .state-banner.error { color: #c62828; }
  `]
})
export class ZatcaSimplifiedListComponent {
  private service = inject(ZatcaSimplifiedService);
  private sessionCtx = inject(SessionContextService);
  private branchLookup = inject(BranchLookupService);
  private dialog = inject(MatDialog);
  context = toSignal(this.sessionCtx.context$, { initialValue: null });
  branches = toSignal(this.branchLookup.list(), { initialValue: [] });
  refresh$ = new BehaviorSubject<void>(undefined);

  loading = signal(true);
  error = signal(false);

  columns = ['select', 'invoiceNumber', 'company', 'issueDate', 'total', 'reportingStatus', 'status', 'actions'];
  currentPage = 0;
  statusFilter = '';
  companyFilter = '';
  branchFilter = '';
  dateFrom = '';
  dateTo = '';
  selectedIds = new Set<string>();

  private readonly emptyResult: ZatcaSimplifiedListResult = {
    items: [], page: 0, size: 50, totalElements: 0,
  };

  documents = toSignal(
    this.refresh$.pipe(
      tap(() => { this.loading.set(true); this.error.set(false); }),
      switchMap(() => this.loadDocuments().pipe(
        catchError(() => { this.error.set(true); return of(this.emptyResult); }),
      )),
      tap(() => this.loading.set(false)),
    ),
    { initialValue: this.emptyResult },
  );

  private loadDocuments() {
    return this.service.list({
      status: this.statusFilter || undefined,
      company: this.companyFilter || undefined,
      branchId: this.branchFilter || undefined,
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

  branchesByCompany() {
    return groupBranchesByCompany(this.branches());
  }

  onPage(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.refresh$.next();
  }

  toggle(id: string, event: MatCheckboxChange): void {
    if (event.checked) {
      this.selectedIds.add(id);
    } else {
      this.selectedIds.delete(id);
    }
  }

  toggleAll(event: MatCheckboxChange): void {
    const items = this.documents()?.items ?? [];
    if (event.checked) {
      items.forEach(i => this.selectedIds.add(i.id));
    } else {
      this.selectedIds.clear();
    }
  }

  allSelected(): boolean {
    const items = this.documents()?.items ?? [];
    return items.length > 0 && items.every(i => this.selectedIds.has(i.id));
  }

  someSelected(): boolean {
    const items = this.documents()?.items ?? [];
    return items.some(i => this.selectedIds.has(i.id)) && !this.allSelected();
  }

  async bulkCheckStatus(): Promise<void> {
    const ids = Array.from(this.selectedIds);
    if (ids.length === 0) return;
    const items = this.documents()?.items ?? [];
    const companyId = items.find(i => ids.includes(i.id))?.companyId ?? this.companyFilter ?? '';

    const response = await this.service.bulkCheckStatus(companyId, ids);
    this.dialog.open(BulkStatusCheckDialogComponent, {
      width: '800px',
      data: { outcomes: [], streaming: true, fetchResponse: response, totalCount: ids.length },
    }).afterClosed().subscribe(() => {
      this.selectedIds.clear();
      this.refresh$.next();
    });
  }
}
