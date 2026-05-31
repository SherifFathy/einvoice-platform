import { Component, Inject, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthService } from '../../shared/services/auth.service';

export interface BulkStatusDialogData {
  outcomes: BulkStatusDialogOutcome[];
  streaming?: boolean;
  fetchResponse?: Response;
  totalCount?: number;
}

export interface BulkStatusDialogOutcome {
  documentId: string;
  outcome: string;
  beforeState: string | null;
  afterState: string | null;
  errorSummary: string | null;
}

@Component({
  selector: 'app-bulk-status-check-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule,
            MatTableModule, MatProgressBarModule],
  template: `
    <h2 mat-dialog-title>
      Check Status Results
      <span *ngIf="streaming" style="font-size: 0.8em; color: #666;">
        ({{ outcomes.length }}{{ totalCount ? ' / ' + totalCount : '' }})
      </span>
    </h2>
    <mat-dialog-content>
      <mat-progress-bar *ngIf="streaming && !completed"
                        mode="indeterminate" style="margin-bottom: 8px;"></mat-progress-bar>
      <table mat-table [dataSource]="outcomes">
        <ng-container matColumnDef="docId">
          <th mat-header-cell *matHeaderCellDef>Document ID</th>
          <td mat-cell *matCellDef="let row">{{ row.documentId | slice:0:8 }}...</td>
        </ng-container>
        <ng-container matColumnDef="outcome">
          <th mat-header-cell *matHeaderCellDef>Outcome</th>
          <td mat-cell *matCellDef="let row">
            <span [class.outcome-updated]="row.outcome === 'UPDATED'"
                  [class.outcome-unchanged]="row.outcome === 'UNCHANGED'"
                  [class.outcome-forbidden]="row.outcome === 'FORBIDDEN'"
                  [class.outcome-error]="row.outcome === 'ETA_ERROR' || row.outcome === 'ZATCA_ERROR'"
                  [class.outcome-notfound]="row.outcome === 'NOT_FOUND'"
                  [class.outcome-cancelled]="row.outcome === 'CANCELLED_NO_OP'">
              {{ row.outcome }}
            </span>
          </td>
        </ng-container>
        <ng-container matColumnDef="before">
          <th mat-header-cell *matHeaderCellDef>Before</th>
          <td mat-cell *matCellDef="let row">{{ row.beforeState || '-' }}</td>
        </ng-container>
        <ng-container matColumnDef="after">
          <th mat-header-cell *matHeaderCellDef>After</th>
          <td mat-cell *matCellDef="let row">{{ row.afterState || '-' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns;"></tr>
      </table>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-raised-button *ngIf="streaming && !completed"
              color="warn" (click)="cancel()">Cancel</button>
      <button mat-raised-button color="primary"
              [mat-dialog-close]="outcomes">Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .outcome-updated { color: #4caf50; font-weight: 600; }
    .outcome-unchanged { color: #9e9e9e; }
    .outcome-forbidden { color: #f44336; font-weight: 600; }
    .outcome-error { color: #ff9800; font-weight: 600; }
    .outcome-notfound { color: #9e9e9e; }
    .outcome-cancelled { color: #ff5722; font-weight: 600; }
  `]
})
export class BulkStatusCheckDialogComponent {
  columns = ['docId', 'outcome', 'before', 'after'];
  outcomes: BulkStatusDialogOutcome[] = [];
  streaming = false;
  completed = false;
  private runId: string | null = null;
  private auth = inject(AuthService);

  constructor(
    public dialogRef: MatDialogRef<BulkStatusCheckDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: BulkStatusDialogData
  ) {
    if (data.streaming && data.fetchResponse) {
      this.streaming = true;
      this.outcomes = data.outcomes ?? [];
      this.consumeStream(data.fetchResponse);
    } else {
      this.outcomes = data.outcomes ?? [];
      this.completed = true;
    }
  }

  private async consumeStream(response: Response): Promise<void> {
    this.runId = response.headers.get('Run-Id');
    const reader = response.body?.getReader();
    if (!reader) {
      this.completed = true;
      return;
    }
    const decoder = new TextDecoder();
    let buffer = '';
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';
        for (const line of lines) {
          if (line.trim()) {
            try {
              const obj = JSON.parse(line);
              this.outcomes = [...this.outcomes, {
                documentId: obj.documentId,
                outcome: obj.outcome,
                beforeState: obj.beforeState,
                afterState: obj.afterState,
                errorSummary: obj.errorSummary,
              }];
            } catch { /* skip malformed lines */ }
          }
        }
      }
    } finally {
      this.completed = true;
    }
  }

  async cancel(): Promise<void> {
    if (this.runId) {
      try {
        const token = this.auth.getToken();
        const headers: Record<string, string> = {};
        if (token) { headers['Authorization'] = `Bearer ${token}`; }
        await fetch(`/api/runs/${this.runId}`, {
          method: 'DELETE',
          headers,
        });
      } catch { /* ignore */ }
    }
  }
}
