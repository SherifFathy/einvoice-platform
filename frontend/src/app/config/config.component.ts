import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Subject, takeUntil } from 'rxjs';
import { SessionContextService } from '../shared/services/session-context.service';

@Component({
  selector: 'app-config',
  imports: [
    CommonModule, RouterModule,
    MatCardModule, MatButtonModule, MatIconModule,
  ],
  templateUrl: './config.component.html',
  styles: [`
    .config-section { margin-bottom: 24px; }
    .scope-banner {
      display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
      padding: 12px 16px; margin-bottom: 24px; border-radius: 8px;
      background: #eef3fb; border: 1px solid #d6e0f5;
    }
    .scope-banner mat-icon { color: #3f51b5; }
    .scope-chip {
      padding: 2px 10px; border-radius: 12px; font-weight: 600; font-size: 0.85em;
      background: #3f51b5; color: #fff;
    }
    .scope-meta { color: #555; }
    .config-description { color: #666; font-size: 0.9em; }
  `],
})
export class ConfigComponent implements OnInit, OnDestroy {
  private sessionCtx = inject(SessionContextService);
  private destroy$ = new Subject<void>();

  /** Active authority + environment scope, derived from the login context. */
  authority: string | null = null;
  environment: string | null = null;
  companyId: string | null = null;
  companyName: string | null = null;

  get isEta(): boolean { return this.authority === 'ETA'; }
  get isZatca(): boolean { return this.authority === 'ZATCA'; }

  ngOnInit(): void {
    // The configuration screen is mapped to — and refreshed through — the
    // authority + environment the user logged in with, not a company picker.
    this.sessionCtx.context$.pipe(takeUntil(this.destroy$)).subscribe((ctx) => {
      if (!ctx) {
        this.authority = this.environment = this.companyId = this.companyName = null;
        return;
      }
      this.authority = ctx.loginContext?.authority ?? null;
      this.environment = ctx.loginContext?.environment ?? null;

      const activeCompanyId = ctx.activeCompanyId ?? ctx.companies?.[0]?.companyId ?? null;
      this.companyId = activeCompanyId;
      const company = ctx.companies?.find((c) => c.companyId === activeCompanyId)
        ?? ctx.companies?.[0];
      this.companyName = company?.companyNameEn ?? null;
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
