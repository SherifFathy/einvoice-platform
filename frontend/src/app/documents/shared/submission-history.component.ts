import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';

/**
 * View-model accepted by the shared submission-history table. It is intentionally
 * decoupled from any single authority's DTO: ETA exposes `submittedAt`/`completedAt`
 * while ZATCA exposes `startedAt`/`finalisedAt`. Both shapes are structurally
 * assignable to this interface, and the template falls back across the two naming
 * conventions so timestamps render for every document type.
 */
export interface SubmissionAttemptView {
  attemptNumber: number;
  submittedBy?: string | null;
  result?: string | null;
  errorSummary?: string | null;
  submittedAt?: string | null;
  completedAt?: string | null;
  startedAt?: string | null;
  finalisedAt?: string | null;
}

@Component({
  selector: 'app-submission-history',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatTableModule],
  template: `
    <mat-card *ngIf="attempts.length" class="history-card">
      <mat-card-header>
        <mat-card-title>Submission History</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <table mat-table [dataSource]="attempts">
          <ng-container matColumnDef="number">
            <th mat-header-cell *matHeaderCellDef>#</th>
            <td mat-cell *matCellDef="let a">{{ a.attemptNumber }}</td>
          </ng-container>
          <ng-container matColumnDef="time">
            <th mat-header-cell *matHeaderCellDef>Timestamp</th>
            <td mat-cell *matCellDef="let a">{{ (a.submittedAt || a.startedAt) | date:'short' }}</td>
          </ng-container>
          <ng-container matColumnDef="user">
            <th mat-header-cell *matHeaderCellDef>User</th>
            <td mat-cell *matCellDef="let a">{{ a.submittedBy || '-' }}</td>
          </ng-container>
          <ng-container matColumnDef="result">
            <th mat-header-cell *matHeaderCellDef>Result</th>
            <td mat-cell *matCellDef="let a">
              <span [class.result-success]="a.result === 'SUCCESS'"
                    [class.result-failed]="a.result === 'REJECTED' || a.result === 'ERROR'"
                    [class.result-ambiguous]="a.result === 'AMBIGUOUS' || a.result === 'TIMEOUT'">
                {{ a.result || 'In progress' }}
              </span>
            </td>
          </ng-container>
          <ng-container matColumnDef="error">
            <th mat-header-cell *matHeaderCellDef>Error</th>
            <td mat-cell *matCellDef="let a">{{ a.errorSummary || '-' }}</td>
          </ng-container>
          <ng-container matColumnDef="completed">
            <th mat-header-cell *matHeaderCellDef>Completed</th>
            <td mat-cell *matCellDef="let a">
              <ng-container *ngIf="(a.completedAt || a.finalisedAt); else dash">
                {{ (a.completedAt || a.finalisedAt) | date:'short' }}
              </ng-container>
              <ng-template #dash>-</ng-template>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns;"></tr>
        </table>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .history-card { margin-top: 16px; }
    .result-success { color: #4caf50; font-weight: 600; }
    .result-failed { color: #f44336; font-weight: 600; }
    .result-ambiguous { color: #ff9800; font-weight: 600; }
  `]
})
export class SubmissionHistoryComponent {
  @Input() attempts: SubmissionAttemptView[] = [];
  columns = ['number', 'time', 'user', 'result', 'error', 'completed'];
}
