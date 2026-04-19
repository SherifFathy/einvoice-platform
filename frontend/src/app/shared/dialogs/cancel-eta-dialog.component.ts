import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-cancel-eta-dialog',
  standalone: true,
  imports: [
    CommonModule, MatDialogModule, MatButtonModule,
    MatFormFieldModule, MatInputModule, FormsModule,
  ],
  template: `
    <h2 mat-dialog-title>Cancel ETA Invoice</h2>
    <mat-dialog-content>
      <p>Are you sure you want to cancel this invoice? This action cannot be undone.</p>
      <mat-form-field appearance="outline" style="width: 100%;">
        <mat-label>Reason</mat-label>
        <input matInput [(ngModel)]="reason" placeholder="Enter cancellation reason">
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="warn" [mat-dialog-close]="{ reason: reason }">Confirm</button>
    </mat-dialog-actions>
  `,
})
export class CancelEtaDialogComponent {
  reason = '';

  constructor(
    public dialogRef: MatDialogRef<CancelEtaDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { reason: string },
  ) {
    this.reason = data.reason || '';
  }
}
