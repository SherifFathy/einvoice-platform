import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

@Injectable({ providedIn: 'root' })
export class ToastNotificationService {
  private snackBar = inject(MatSnackBar);

  show(message: string, type: ToastType = 'info', duration = 3000): void {
    const panelClass = `toast-${type}`;
    this.snackBar.open(message, 'Close', {
      duration,
      panelClass: [panelClass],
      horizontalPosition: 'end',
      verticalPosition: 'top',
    });
  }

  success(message: string): void {
    this.show(message, 'success');
  }

  error(message: string): void {
    this.show(message, 'error', 5000);
  }

  warning(message: string): void {
    this.show(message, 'warning', 4000);
  }

  info(message: string): void {
    this.show(message, 'info');
  }
}
