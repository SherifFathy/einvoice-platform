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
import { ZatcaStandardService, ZatcaStandardListResult } from './services/zatca-standard.service';
import { SessionContextService } from '../shared/services/session-context.service';
import { HasPermissionDirective } from '../shared/directives/has-permission.directive';
import { BulkStatusCheckDialogComponent } from '../documents/shared/bulk-status-check.dialog';
import { toSignal } from '@angular/core/rxjs-interop';
import { BehaviorSubject, switchMap } from 'rxjs';

@Component({
  selector: 'app-zatca-standard-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, MatTableModule,
            MatPaginatorModule, MatButtonModule, MatIconModule, MatChipsModule,
            MatFormFieldModule, MatSelectModule, MatInputModule,
            MatCheckboxModule, MatDialogModule,
            HasPermissionDirective],
  template: `
    <div class="list-container">
      <div class="list-header">
        <h2>ZATCA Standard (B2B)</h2>
        <div>
          <button mat-raised-button color="accent"
                  *ngIf="selectedIds.size > 0"
                  *appHasPermission="['STANDARD', 'REFRESH']"
                  (click)="bulkCheckStatus()">
            <mat-icon>sync</mat-icon> Check Status ({{ selectedIds.size }})
          </button>
          <button mat-raised-button color="primary" routerLink="new"
                  *appHasPermission="['STANDARD', 'CREATE']">New Standard</button>
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
          <td mat-cell *matCellDef="let row">{{ row.invoiceNumber }}</td>
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
        <ng-container matColumnDef="clearanceStatus">
          <th mat-header-cell *matHeaderCellDef>Clearance</th>
          <td mat-cell *matCellDef="let row">
            <mat-chip *ngIf="row.clearanceStatus">{{ row.clearanceStatus }}</mat-chip>
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
            <ng-container *appHasPermission="['STANDARD', 'VIEW']">
              <button mat-icon-button [routerLink]="['/standard', row.id]">
                <mat-icon>visibility</mat-icon>
              </button>
            </ng-container>
            <ng-container *appHasPermission="['STANDARD', 'EDIT']">
              <button mat-icon-button *ngIf="row.status === 'DRAFT'"
                      [routerLink]="['/standard', row.id, 'edit']">
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
    </div>
  `,
  styles: [`
    .list-container { padding: 16px; }
    .list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .list-header div { display: flex; gap: 8px; align-items: center; }
    .filters { display: flex; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
    .filters mat-form-field { width: 180px; }
  `]
})
export class ZatcaStandardListComponent {
  private service = inject(ZatcaStandardService);
  private sessionCtx = inject(SessionContextService);
  private dialog = inject(MatDialog);
  context = toSignal(this.sessionCtx.context$, { initialValue: null });
  refresh$ = new BehaviorSubject<void>(undefined);

  columns = ['select', 'invoiceNumber', 'company', 'issueDate', 'total', 'clearanceStatus', 'status', 'actions'];
  currentPage = 0;
  statusFilter = '';
  companyFilter = '';
  dateFrom = '';
  dateTo = '';
  selectedIds = new Set<string>();

  documents = toSignal(
    this.refresh$.pipe(switchMap(() => this.loadDocuments())),
    { initialValue: { items: [] as ZatcaStandardListResult['items'],
        page: 0, size: 50, totalElements: 0 } }
  );

  private loadDocuments() {
    const ctx = this.context();
    const companyId = this.companyFilter || (ctx?.activeCompanyId ?? '');
    return this.service.list(companyId, {
      status: this.statusFilter || undefined,
      company: this.companyFilter || undefined,
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

  toggle(id: string, event: any): void {
    if (event.checked) {
      this.selectedIds.add(id);
    } else {
      this.selectedIds.delete(id);
    }
  }

  toggleAll(event: any): void {
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
    const ctx = this.context();
    const companyId = this.companyFilter || (ctx?.activeCompanyId ?? '');
    const ids = Array.from(this.selectedIds);
    if (ids.length === 0) return;

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
