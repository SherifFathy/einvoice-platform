import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressBarModule } from '@angular/material/progress-bar';

export interface BulkStatusDialogData {
  outcomes: BulkStatusDialogOutcome[];
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
    <h2 mat-dialog-title>Check Status Results</h2>
    <mat-dialog-content>
      <table mat-table [dataSource]="data.outcomes">
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
                  [class.outcome-error]="row.outcome === 'ETA_ERROR'"
                  [class.outcome-notfound]="row.outcome === 'NOT_FOUND'">
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
      <button mat-raised-button color="primary" mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .outcome-updated { color: #4caf50; font-weight: 600; }
    .outcome-unchanged { color: #9e9e9e; }
    .outcome-forbidden { color: #f44336; font-weight: 600; }
    .outcome-error { color: #ff9800; font-weight: 600; }
    .outcome-notfound { color: #9e9e9e; }
  `]
})
export class BulkStatusCheckDialogComponent {
  columns = ['docId', 'outcome', 'before', 'after'];

  constructor(
    public dialogRef: MatDialogRef<BulkStatusCheckDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: BulkStatusDialogData
  ) {}
}
