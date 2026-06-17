import { Component, OnDestroy, OnInit, inject } from '@angular/core';
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
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subject, takeUntil } from 'rxjs';
import { SessionContextService } from '../shared/services/session-context.service';
import { SessionContext } from '../shared/services/auth.service';
import {
  SubmissionLogRow,
  SubmissionLogService,
} from './services/submission-log.service';

const TRANSACTION_TYPES = ['INVOICE', 'RECEIPT', 'STANDARD', 'SIMPLIFIED'];
const OUTCOMES = ['SUCCESS', 'REJECTED', 'ERROR', 'TIMEOUT', 'AMBIGUOUS', 'IN_FLIGHT'];
const PAGE_SIZE = 20;

@Component({
  selector: 'app-submission-log',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, MatTableModule,
            MatPaginatorModule, MatButtonModule, MatIconModule, MatChipsModule,
            MatFormFieldModule, MatSelectModule, MatInputModule,
            MatProgressBarModule],
  template: `
    <div class="list-container">
      <div class="list-header">
        <h2>Submission Log</h2>
      </div>

      <div class="filters">
        @if (showCompanyColumn) {
          <mat-form-field appearance="outline">
            <mat-label>Company</mat-label>
            <mat-select [(ngModel)]="companyFilter" (selectionChange)="reload()">
              <mat-option value="">All</mat-option>
              @for (c of companies; track c.companyId) {
                <mat-option [value]="c.companyId">{{ c.companyNameEn }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select [(ngModel)]="transactionTypeFilter" (selectionChange)="reload()">
            <mat-option value="">All</mat-option>
            @for (t of transactionTypes; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Outcome</mat-label>
          <mat-select [(ngModel)]="outcomeFilter" (selectionChange)="reload()">
            <mat-option value="">All</mat-option>
            @for (o of outcomes; track o) {
              <mat-option [value]="o">{{ o }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>From</mat-label>
          <input matInput type="date" [(ngModel)]="dateFrom" (change)="reload()">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>To</mat-label>
          <input matInput type="date" [(ngModel)]="dateTo" (change)="reload()">
        </mat-form-field>
      </div>

      @if (loading) {
        <mat-progress-bar mode="indeterminate"></mat-progress-bar>
        <div class="state-banner">Loading submission log…</div>
      } @else if (error) {
        <div class="state-banner error">
          Could not load the submission log. Please try again.
          <div><button mat-button color="primary" (click)="reload()">Retry</button></div>
        </div>
      } @else if (rows.length === 0) {
        <div class="state-banner">No submissions match the current filters.</div>
      } @else {
        <table mat-table [dataSource]="rows">
          <ng-container matColumnDef="company">
            <th mat-header-cell *matHeaderCellDef>Company</th>
            <td mat-cell *matCellDef="let row">{{ row.companyName }}</td>
          </ng-container>
          <ng-container matColumnDef="transactionType">
            <th mat-header-cell *matHeaderCellDef>Type</th>
            <td mat-cell *matCellDef="let row">
              <mat-chip>{{ row.transactionType }}</mat-chip>
            </td>
          </ng-container>
          <ng-container matColumnDef="attemptNumber">
            <th mat-header-cell *matHeaderCellDef>Attempt</th>
            <td mat-cell *matCellDef="let row">{{ row.attemptNumber }}</td>
          </ng-container>
          <ng-container matColumnDef="outcome">
            <th mat-header-cell *matHeaderCellDef>Outcome</th>
            <td mat-cell *matCellDef="let row">
              <mat-chip [class.failed]="isFailure(row.outcome)">{{ row.outcome }}</mat-chip>
            </td>
          </ng-container>
          <ng-container matColumnDef="errorSummary">
            <th mat-header-cell *matHeaderCellDef>Detail</th>
            <td mat-cell *matCellDef="let row">{{ row.errorSummary }}</td>
          </ng-container>
          <ng-container matColumnDef="submittedAt">
            <th mat-header-cell *matHeaderCellDef>Submitted</th>
            <td mat-cell *matCellDef="let row">{{ row.submittedAt | date:'short' }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let row">
              <button mat-icon-button [routerLink]="detailLink(row)"
                      aria-label="Open document">
                <mat-icon>visibility</mat-icon>
              </button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns;"></tr>
        </table>
        <mat-paginator [length]="totalElements" [pageSize]="pageSize"
          [pageIndex]="currentPage" (page)="onPage($event)"></mat-paginator>
      }
    </div>
  `,
  styles: [`
    .list-container { padding: 16px; }
    .list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .filters { display: flex; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
    .filters mat-form-field { width: 180px; }
    .state-banner { padding: 24px; text-align: center; color: #666; }
    .state-banner.error { color: #c62828; }
    mat-chip.failed { background-color: #ffebee; color: #c62828; }
  `],
})
export class SubmissionLogComponent implements OnInit, OnDestroy {
  private service = inject(SubmissionLogService);
  private sessionCtx = inject(SessionContextService);

  readonly transactionTypes = TRANSACTION_TYPES;
  readonly outcomes = OUTCOMES;
  readonly pageSize = PAGE_SIZE;

  companies: SessionContext['companies'] = [];
  showCompanyColumn = false;

  loading = false;
  error = false;
  rows: SubmissionLogRow[] = [];
  totalElements = 0;
  currentPage = 0;

  companyFilter = '';
  transactionTypeFilter = '';
  outcomeFilter = '';
  dateFrom = '';
  dateTo = '';

  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    const ctx = this.sessionCtx.currentContext;
    if (ctx) {
      this.companies = ctx.companies ?? [];
      this.showCompanyColumn = this.companies.length > 1;
    }
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get columns(): string[] {
    const base = ['transactionType', 'attemptNumber', 'outcome', 'errorSummary',
        'submittedAt', 'actions'];
    return this.showCompanyColumn ? ['company', ...base] : base;
  }

  detailLink(row: SubmissionLogRow): unknown[] {
    return this.service.detailLink(row);
  }

  isFailure(outcome: string): boolean {
    return outcome === 'REJECTED' || outcome === 'ERROR' || outcome === 'TIMEOUT';
  }

  /** Resets to the first page and reloads (used by every filter change). */
  reload(): void {
    this.currentPage = 0;
    this.load();
  }

  onPage(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.load();
  }

  private load(): void {
    this.loading = true;
    this.error = false;
    this.service.list({
      companyId: this.companyFilter || undefined,
      transactionType: this.transactionTypeFilter || undefined,
      outcome: this.outcomeFilter || undefined,
      dateFrom: this.dateFrom || undefined,
      dateTo: this.dateTo || undefined,
      page: this.currentPage,
      size: this.pageSize,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (page) => {
        this.rows = page.items;
        this.totalElements = page.totalElements;
        this.loading = false;
      },
      error: () => {
        this.error = true;
        this.loading = false;
      },
    });
  }
}
