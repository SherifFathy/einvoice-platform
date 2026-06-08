import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { Subject, takeUntil } from 'rxjs';
import { SessionContextService } from '../shared/services/session-context.service';
import { SessionContext } from '../shared/services/auth.service';
import {
  CompanyCard,
  DashboardKpi,
  DashboardService,
  RecentActivityEntry,
} from './services/dashboard.service';

/** Document states in canonical display order for the KPI panel. */
const STATUS_ORDER = [
  'DRAFT',
  'SUBMITTING',
  'SUBMITTED',
  'IN_REVIEW',
  'ACCEPTED',
  'REJECTED',
  'CANCELLED',
];

@Component({
  selector: 'app-dashboard',
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatIconModule,
    MatChipsModule,
    MatButtonModule,
  ],
  templateUrl: './dashboard.component.html',
  styles: `
    .dashboard-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 16px;
      margin-top: 16px;
    }
    .company-card {
      padding: 16px;
    }
    .company-card.inactive {
      opacity: 0.6;
      border-color: #bdbdbd;
    }
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
    }
    .company-name {
      font-weight: 600;
      font-size: 16px;
    }
    .company-name-ar {
      color: #666;
      font-size: 14px;
    }
    .card-stats {
      display: flex;
      gap: 24px;
      margin-top: 12px;
    }
    .stat-value {
      font-size: 20px;
      font-weight: 600;
    }
    .stat-value.failed {
      color: #c62828;
    }
    .stat-label {
      color: #666;
      font-size: 12px;
    }
    .cert-line {
      margin-top: 12px;
      font-size: 13px;
    }
    .cert-line.warn {
      color: #e65100;
    }
    .cert-line.expired {
      color: #c62828;
      font-weight: 600;
    }
    .kpi-panel {
      display: flex;
      gap: 32px;
      flex-wrap: wrap;
      margin-top: 16px;
    }
    .kpi-block {
      flex: 1 1 320px;
    }
    .kpi-row {
      display: flex;
      justify-content: space-between;
      padding: 2px 0;
      font-size: 13px;
    }
    .kpi-total {
      font-weight: 600;
      border-top: 1px solid #e0e0e0;
      margin-top: 4px;
      padding-top: 4px;
    }
    .activity-row {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      padding: 6px 0;
      border-bottom: 1px solid #f0f0f0;
      font-size: 13px;
    }
    .admin-banner {
      background-color: #fff3e0;
      border: 1px solid #ff9800;
      border-radius: 4px;
      padding: 12px 16px;
      margin-bottom: 16px;
      color: #e65100;
      font-size: 14px;
    }
    .state-banner {
      padding: 24px;
      text-align: center;
      color: #666;
    }
    .state-banner.error {
      color: #c62828;
    }
    .dashboard-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    h2 {
      margin-bottom: 0;
    }
    section {
      margin-top: 24px;
    }
  `,
})
export class DashboardComponent implements OnInit, OnDestroy {
  protected sessionCtx = inject(SessionContextService);
  private dashboardService = inject(DashboardService);

  companies: SessionContext['companies'] = [];
  isAdminMode = false;
  isSuperUserOperational = false;

  loading = false;
  error = false;
  cards: CompanyCard[] = [];
  kpi: DashboardKpi | null = null;
  activity: RecentActivityEntry[] = [];

  readonly statusOrder = STATUS_ORDER;

  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    const ctx = this.sessionCtx.currentContext;
    if (ctx) {
      this.updateState(ctx);
    }

    this.sessionCtx.context$.pipe(
      takeUntil(this.destroy$),
    ).subscribe((next) => {
      if (next) {
        this.updateState(next);
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Re-fetches dashboard data (used by the error-state Retry button). */
  reload(): void {
    this.loadOperationalData();
  }

  private updateState(ctx: SessionContext): void {
    this.isAdminMode = ctx.mode === 'ADMIN_MODE';
    this.isSuperUserOperational = ctx.isSuperUser && ctx.mode === 'OPERATIONAL_MODE';
    this.companies = ctx.companies ?? [];
    if (!this.isAdminMode) {
      this.loadOperationalData();
    }
  }

  private loadOperationalData(): void {
    this.loading = true;
    this.error = false;

    this.dashboardService.summary().pipe(
      takeUntil(this.destroy$),
    ).subscribe({
      next: (summary) => {
        this.cards = summary.cards;
        this.kpi = summary.kpi;
        this.loading = false;
      },
      error: () => {
        this.error = true;
        this.loading = false;
      },
    });

    this.dashboardService.recentActivity().pipe(
      takeUntil(this.destroy$),
    ).subscribe({
      next: (result) => {
        this.activity = result.entries;
      },
      error: () => {
        this.error = true;
      },
    });
  }
}
