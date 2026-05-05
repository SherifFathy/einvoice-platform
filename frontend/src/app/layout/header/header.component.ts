import { Component, inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { Subscription } from 'rxjs';
import { AuthService } from '../../shared/services/auth.service';
import { SessionContextService } from '../../shared/services/session-context.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
  ],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent implements OnDestroy {
  protected authService = inject(AuthService);
  protected sessionCtx = inject(SessionContextService);
  private router = inject(Router);
  private titleService = inject(Title);
  private readonly titleSub!: Subscription;

  constructor() {
    this.titleSub = this.sessionCtx.context$.subscribe((ctx) => {
      if (ctx?.loginContext?.authority === 'ETA') {
        this.titleService.setTitle('ETA Platform');
      } else if (ctx?.loginContext?.authority === 'ZATCA') {
        this.titleService.setTitle('ZATCA Platform');
      } else {
        this.titleService.setTitle('E-Invoice Platform');
      }
    });
  }

  ngOnDestroy(): void {
    this.titleSub.unsubscribe();
  }

  get authority(): string {
    return this.sessionCtx.currentContext?.loginContext?.authority ?? '';
  }

  get environment(): string {
    return this.sessionCtx.currentContext?.loginContext?.environment ?? '';
  }

  get contextChip(): string {
    const ctx = this.sessionCtx.currentContext;
    if (!ctx) return '';
    if (ctx.mode === 'ADMIN_MODE') return 'Admin Mode';
    const companies = ctx.companies ?? [];
    if (companies.length === 1) return companies[0].companyNameEn;
    if (companies.length >= 2) return `${companies.length} companies`;
    return '';
  }

  get isSuperUser(): boolean {
    return this.sessionCtx.currentContext?.isSuperUser ?? false;
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => {
        this.sessionCtx.clear();
        this.router.navigate(['/login']);
      },
      error: () => {
        this.sessionCtx.clear();
        this.router.navigate(['/login']);
      },
    });
  }
}
