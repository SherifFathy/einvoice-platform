import { Component, Input, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../../services/auth.service';
import { ToastNotificationService } from '../../services/toast.service';

@Component({
  selector: 'app-environment-selector',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './environment-selector.component.html',
  styleUrls: ['./environment-selector.component.scss'],
})
export class EnvironmentSelectorComponent {
  protected authService = inject(AuthService);
  private toast = inject(ToastNotificationService);

  @Input() permittedEnvironments: string[] = [];
  @Output() environmentSelected = new EventEmitter<string>();

  get activeEnvironment(): string {
    return this.authService.getActiveEnvironment() ?? '';
  }

  get displayLabel(): string {
    return this.activeEnvironment || 'Select Environment';
  }

  selectEnvironment(environment: string): void {
    this.authService.selectEnvironment(environment).subscribe({
      next: () => this.environmentSelected.emit(environment),
      error: (err) => {
        const message = err?.error?.message ?? 'Failed to select environment. You may not have permission.';
        this.toast.error(message);
      },
    });
  }
}
