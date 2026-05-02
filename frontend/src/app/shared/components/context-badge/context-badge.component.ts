import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-context-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (authService.isLoggedIn() && activeAuthority && activeDocType && activeSubEnv) {
      <span class="context-badge">
        {{ activeAuthority }} &middot; {{ activeDocType }} &middot; {{ activeSubEnv }}
      </span>
    }
  `,
  styles: [
    `
      .context-badge {
        display: inline-flex;
        align-items: center;
        background-color: rgba(255, 255, 255, 0.15);
        color: white;
        font-size: 12px;
        font-weight: 500;
        padding: 4px 12px;
        border-radius: 16px;
        margin-left: 12px;
        letter-spacing: 0.3px;
      }
    `,
  ],
})
export class ContextBadgeComponent {
  protected authService = inject(AuthService);

  get activeAuthority(): string | null {
    return this.authService.getCurrentUser()?.activeAuthority ?? null;
  }

  get activeDocType(): string | null {
    const raw = this.authService.getCurrentUser()?.activeDocType ?? null;
    if (!raw) return null;
    return raw.charAt(0) + raw.slice(1).toLowerCase();
  }

  get activeSubEnv(): string | null {
    const raw = this.authService.getCurrentUser()?.activeSubEnv ?? null;
    if (!raw) return null;
    return raw.charAt(0) + raw.slice(1).toLowerCase();
  }
}
