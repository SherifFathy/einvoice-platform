import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { EtaInvoice } from '../eta/services/eta-invoice.service';

export interface ConflictDialogData {
  expectedVersion: number;
  actualVersion: number;
  current: EtaInvoice;
  pendingChanges: unknown;
}

@Component({
  selector: 'app-conflict-resolution-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Version Conflict</h2>
    <mat-dialog-content>
      <p>This document was modified by another user.</p>
      <p>Your version: {{ data.expectedVersion }} | Current version: {{ data.actualVersion }}</p>

      <div class="diff-side-by-side">
        <div class="diff-panel yours">
          <h4>Your Changes</h4>
          <div class="diff-field" *ngFor="let f of displayedFields">
            <span class="label">{{ f.label }}</span>
            <span class="value">{{ getPending(f.key) }}</span>
          </div>
        </div>
        <div class="diff-panel server">
          <h4>Current (Server)</h4>
          <div class="diff-field" *ngFor="let f of displayedFields">
            <span class="label">{{ f.label }}</span>
            <span class="value">{{ getField(data.current, f.key) }}</span>
          </div>
        </div>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="discard()">Discard mine</button>
      <button mat-raised-button color="primary" (click)="overwrite()">
        Overwrite with mine
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .diff-side-by-side { display: flex; gap: 16px; margin: 16px 0; }
    .diff-panel { flex: 1; background: #f5f5f5; border-radius: 4px; padding: 12px; }
    .diff-panel.yours { border-left: 3px solid #3f51b5; }
    .diff-panel.server { border-left: 3px solid #f44336; }
    .diff-field { display: flex; justify-content: space-between; padding: 4px 0; border-bottom: 1px solid #e0e0e0; }
    .diff-field .label { font-weight: bold; min-width: 120px; }
    .diff-field .value { flex: 1; text-align: right; word-break: break-all; }
  `]
})
export class ConflictResolutionDialogComponent {
  private dialogRef = inject(MatDialogRef<ConflictResolutionDialogComponent>);
  data: ConflictDialogData = inject(MAT_DIALOG_DATA);

  displayedFields = [
    { label: 'Invoice Number', key: 'invoiceNumber' },
    { label: 'Document Type', key: 'documentType' },
    { label: 'Currency', key: 'currency' },
    { label: 'Total Amount', key: 'totalAmount' },
    { label: 'State', key: 'state' },
    { label: 'Version', key: 'version' },
  ];

  getField(invoice: EtaInvoice, key: string): string {
    return String((invoice as unknown as Record<string, unknown>)[key] ?? '-');
  }

  getPending(key: string): string {
    const pending = this.data.pendingChanges as Record<string, unknown>;
    return String(pending?.[key] ?? '-');
  }

  discard(): void {
    this.dialogRef.close({ action: 'discard', current: this.data.current });
  }

  overwrite(): void {
    this.dialogRef.close({
      action: 'overwrite',
      actualVersion: this.data.actualVersion,
    });
  }
}
