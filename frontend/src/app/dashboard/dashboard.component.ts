import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, takeUntil } from 'rxjs';
import { StatusBadgeComponent } from '../shared/components/status-badge/status-badge.component';
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
    MatProgressSpinnerModule,
    StatusBadgeComponent,
  ],
  templateUrl: './dashboard.component.html',
  styles: `
    :host {
      display: block;
    }

    .dashboard-shell {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .dashboard-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 16px;
    }

    .company-card {
      border: 1px solid #eeeeee;
      border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
      overflow: hidden;
    }

    .company-card-content {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 18px;
    }

    .company-card.inactive {
      border-color: #bdbdbd;
      background: #fafafa;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
    }

    .company-identity {
      min-width: 0;
    }

    .company-name {
      font-weight: 600;
      font-size: 18px;
      line-height: 1.25;
      color: #212121;
      overflow-wrap: anywhere;
    }

    .company-name-ar {
      color: #616161;
      font-size: 14px;
      line-height: 1.4;
      margin-top: 2px;
      overflow-wrap: anywhere;
    }

    .company-tax-number {
      color: #757575;
      font-size: 12px;
      margin-top: 6px;
    }

    .card-stats {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 10px;
    }

    .stat-tile {
      border: 1px solid #eeeeee;
      border-radius: 8px;
      background: #fafafa;
      padding: 12px;
    }

    .stat-tile.failed.has-failures {
      border-color: #ffcdd2;
      background: #ffebee;
    }

    .stat-value {
      color: #212121;
      font-size: 26px;
      font-weight: 600;
      line-height: 1;
    }

    .stat-value.failed {
      color: #c62828;
    }

    .stat-label {
      color: #616161;
      font-size: 12px;
      margin-top: 6px;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .cert-line {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #616161;
      font-size: 13px;
      line-height: 1.4;
    }

    .cert-line.warn {
      color: #e65100;
    }

    .cert-line.expired {
      color: #c62828;
      font-weight: 600;
    }

    .kpi-panel {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 16px;
    }

    .kpi-block {
      border: 1px solid #eeeeee;
      border-radius: 8px;
      background: #ffffff;
      padding: 16px;
    }

    .section-header {
      margin-bottom: 12px;
    }

    .kpi-title-row {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 12px;
    }

    .kpi-title {
      font-size: 16px;
      font-weight: 600;
      color: #212121;
    }

    .kpi-zone {
      color: #9e9e9e;
      font-size: 12px;
    }

    .kpi-status-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
      gap: 8px;
    }

    .kpi-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
      border: 1px solid #eeeeee;
      border-radius: 8px;
      background: #fafafa;
      padding: 8px;
      font-size: 13px;
    }

    .kpi-row.is-zero {
      opacity: 0.56;
    }

    .kpi-count {
      color: #212121;
      font-weight: 600;
      min-width: 20px;
      text-align: right;
    }

    .kpi-total {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-top: 1px solid #eeeeee;
      font-weight: 600;
      margin-top: 14px;
      padding-top: 12px;
    }

    .kpi-total-value {
      font-size: 22px;
      color: #212121;
    }

    .activity-list {
      border: 1px solid #eeeeee;
      border-radius: 8px;
      overflow: hidden;
      background: #ffffff;
    }

    .activity-row {
      display: grid;
      grid-template-columns: minmax(140px, 1.4fr) minmax(100px, 0.9fr) minmax(120px, 0.8fr) minmax(120px, 0.8fr);
      align-items: center;
      gap: 12px;
      padding: 12px 14px;
      border-bottom: 1px solid #f5f5f5;
      font-size: 13px;
    }

    .activity-company {
      font-weight: 600;
      color: #212121;
      overflow-wrap: anywhere;
    }

    .activity-type,
    .activity-time {
      color: #616161;
    }

    .admin-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      background-color: #fff3e0;
      border: 1px solid #ff9800;
      border-radius: 8px;
      padding: 12px 16px;
      color: #e65100;
      font-size: 14px;
    }

    .state-banner {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 10px;
      min-height: 180px;
      border: 1px dashed #d6d6d6;
      border-radius: 8px;
      background: #fafafa;
      padding: 28px;
      text-align: center;
      color: #616161;
    }

    .state-banner mat-icon {
      color: #9e9e9e;
      font-size: 34px;
      height: 34px;
      width: 34px;
    }

    .state-banner.error {
      border-color: #ffcdd2;
      background: #ffebee;
      color: #c62828;
    }

    .state-banner.error mat-icon {
      color: #c62828;
    }

    .state-title {
      color: #212121;
      font-size: 16px;
      font-weight: 600;
    }

    .state-banner.error .state-title {
      color: #c62828;
    }

    .state-text {
      max-width: 420px;
      margin: 0;
    }

    .dashboard-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
    }

    .dashboard-title {
      margin: 0;
      font-size: 28px;
      line-height: 1.2;
    }

    .dashboard-subtitle {
      color: #616161;
      margin-top: 4px;
      font-size: 14px;
    }

    h2 {
      margin-bottom: 0;
    }

    h3 {
      margin: 0 0 12px;
      font-size: 18px;
      line-height: 1.3;
    }

    section {
      display: flex;
      flex-direction: column;
    }

    @media (max-width: 700px) {
      .dashboard-header {
        align-items: stretch;
        flex-direction: column;
      }

      .dashboard-header a {
        align-self: flex-start;
      }

      .activity-row {
        grid-template-columns: 1fr;
        gap: 6px;
      }
    }

    @media (max-width: 420px) {
      .dashboard-grid,
      .kpi-panel {
        grid-template-columns: 1fr;
      }

      .company-card-content,
      .kpi-block {
        padding: 14px;
      }
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
