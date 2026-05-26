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
    .admin-banner {
      background-color: #fff3e0;
      border: 1px solid #ff9800;
      border-radius: 4px;
      padding: 12px 16px;
      margin-bottom: 16px;
      color: #e65100;
      font-size: 14px;
    }
    .dashboard-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    h2 {
      margin-bottom: 0;
    }
  `,
})
export class DashboardComponent implements OnInit, OnDestroy {
  protected sessionCtx = inject(SessionContextService);

  companies: SessionContext['companies'] = [];
  isAdminMode = false;
  isSuperUserOperational = false;
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    const ctx = this.sessionCtx.currentContext;
    if (ctx) {
      this.updateState(ctx);
    }

    this.sessionCtx.context$.pipe(
      takeUntil(this.destroy$),
    ).subscribe((ctx) => {
      if (ctx) {
        this.updateState(ctx);
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateState(ctx: SessionContext): void {
    this.isAdminMode = ctx.mode === 'ADMIN_MODE';
    this.isSuperUserOperational = ctx.isSuperUser && ctx.mode === 'OPERATIONAL_MODE';
    this.companies = ctx.companies ?? [];
  }
}
