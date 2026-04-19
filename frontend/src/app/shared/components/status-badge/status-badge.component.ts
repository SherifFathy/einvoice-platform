import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

const STATUS_COLORS: Record<string, { bg: string; text: string }> = {
  DRAFT: { bg: '#e0e0e0', text: '#616161' },
  CANCELLED: { bg: '#e0e0e0', text: '#9e9e9e' },
  VALIDATED: { bg: '#fff9c4', text: '#f57f17' },
  READY_FOR_SUBMISSION: { bg: '#fff9c4', text: '#f57f17' },
  SUBMISSION_IN_PROGRESS: { bg: '#e3f2fd', text: '#1565c0' },
  CLEARED: { bg: '#e8f5e9', text: '#2e7d32' },
  REPORTED: { bg: '#e8f5e9', text: '#2e7d32' },
  ACCEPTED: { bg: '#e8f5e9', text: '#2e7d32' },
  IN_REVIEW: { bg: '#fff9c4', text: '#f57f17' },
  REJECTED: { bg: '#ffebee', text: '#c62828' },
  FAILED_RETRYABLE: { bg: '#ffebee', text: '#c62828' },
  FAILED_NON_RETRYABLE: { bg: '#ffebee', text: '#c62828' },
  SUBMISSION_AMBIGUOUS: { bg: '#fff3e0', text: '#e65100' },
};

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="status-badge" [ngStyle]="badgeStyle">
      {{ label }}
    </span>
  `,
  styleUrls: ['./status-badge.component.scss'],
})
export class StatusBadgeComponent {
  @Input() status = '';

  get label(): string {
    return this.status.replace(/_/g, ' ');
  }

  get badgeStyle(): Record<string, string> {
    const colors = STATUS_COLORS[this.status] ?? { bg: '#e0e0e0', text: '#616161' };
    return {
      'background-color': colors.bg,
      'color': colors.text,
    };
  }
}
