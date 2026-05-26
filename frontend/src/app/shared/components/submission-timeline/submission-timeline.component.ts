import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { SubmissionAttemptResponse } from '../../services/invoice.service';

@Component({
  selector: 'app-submission-timeline',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    @if (attempts.length === 0) {
      <p class="no-attempts">No submission attempts recorded.</p>
    } @else {
      <div class="timeline">
        @for (attempt of attempts; track attempt.attemptNumber) {
          <div class="timeline-item" [ngClass]="getResultClass(attempt.result)">
            <div class="timeline-marker">
              <mat-icon [ngClass]="getResultClass(attempt.result)">
                {{ getResultIcon(attempt.result) }}
              </mat-icon>
            </div>
            <div class="timeline-content">
              <div class="timeline-header">
                <span class="attempt-number">Attempt #{{ attempt.attemptNumber }}</span>
                <span class="attempt-author">{{ attempt.authority }} ({{ attempt.environment }})</span>
                <span class="attempt-result" [ngClass]="getResultClass(attempt.result)">
                  {{ attempt.result }}
                </span>
              </div>
              <div class="timeline-meta">
                @if (attempt.submittedAt) {
                  <span class="meta-item">Submitted: {{ attempt.submittedAt | date:'medium' }}</span>
                }
                @if (attempt.completedAt) {
                  <span class="meta-item">Completed: {{ attempt.completedAt | date:'medium' }}</span>
                }
                @if (attempt.statusCode) {
                  <span class="meta-item">HTTP {{ attempt.statusCode }}</span>
                }
              </div>
              @if (attempt.errorSummary) {
                <div class="attempt-error">{{ attempt.errorSummary }}</div>
              }
            </div>
          </div>
        }
      </div>
    }
  `,
  styles: `
    .no-attempts { color: #999; font-style: italic; margin: 8px 0; }
    .timeline { display: flex; flex-direction: column; gap: 0; }
    .timeline-item { display: flex; gap: 12px; padding: 8px 0; border-left: 2px solid #e0e0e0; padding-left: 16px; position: relative; }
    .timeline-item:last-child { border-left-color: transparent; }
    .timeline-marker { position: absolute; left: -14px; top: 10px; }
    .timeline-marker mat-icon { font-size: 20px; width: 20px; height: 20px; background: white; border-radius: 50%; }
    .timeline-content { flex: 1; min-width: 0; }
    .timeline-header { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .attempt-number { font-weight: 600; font-size: 0.9em; }
    .attempt-author { color: #666; font-size: 0.85em; }
    .attempt-result { font-size: 0.8em; font-weight: 500; padding: 2px 8px; border-radius: 10px; }
    .timeline-meta { display: flex; gap: 12px; margin-top: 4px; flex-wrap: wrap; font-size: 0.8em; color: #888; }
    .meta-item { white-space: nowrap; }
    .attempt-error { margin-top: 4px; font-size: 0.85em; color: #d32f2f; background: #ffebee; padding: 4px 8px; border-radius: 4px; }
    .result-SUCCESS mat-icon, .result-SUCCESS { color: #388e3c; }
    .result-SUCCESS .attempt-result { background: #e8f5e9; color: #2e7d32; }
    .result-REJECTED mat-icon, .result-REJECTED { color: #d32f2f; }
    .result-REJECTED .attempt-result { background: #ffebee; color: #c62828; }
    .result-ERROR mat-icon, .result-ERROR { color: #d32f2f; }
    .result-ERROR .attempt-result { background: #ffebee; color: #c62828; }
    .result-TIMEOUT mat-icon, .result-TIMEOUT { color: #e65100; }
    .result-TIMEOUT .attempt-result { background: #fff3e0; color: #e65100; }
    .result-AMBIGUOUS mat-icon, .result-AMBIGUOUS { color: #e65100; }
    .result-AMBIGUOUS .attempt-result { background: #fff3e0; color: #e65100; }
  `,
})
export class SubmissionTimelineComponent {
  @Input() attempts: SubmissionAttemptResponse[] = [];

  getResultClass(result: string): string {
    return `result-${result}`;
  }

  getResultIcon(result: string): string {
    switch (result) {
      case 'SUCCESS': return 'check_circle';
      case 'REJECTED': return 'cancel';
      case 'ERROR': return 'error';
      case 'TIMEOUT': return 'schedule';
      case 'AMBIGUOUS': return 'help';
      default: return 'info';
    }
  }
}
