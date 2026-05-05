import { Component, EventEmitter, inject, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { CustomerService, BulkUploadResult } from '../../shared/services/customer.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-customer-import',
  standalone: true,
  imports: [
    CommonModule, MatButtonModule, MatIconModule, MatProgressBarModule,
    MatTableModule, MatCardModule,
  ],
  templateUrl: './customer-import.component.html',
  styles: `
    .upload-zone { border: 2px dashed #ccc; border-radius: 8px; padding: 32px; text-align: center; cursor: pointer; margin-bottom: 16px; }
    .upload-zone:hover { border-color: #1976d2; background: #f5f5f5; }
    .upload-zone.dragover { border-color: #1976d2; background: #e3f2fd; }
    .result-card { margin-top: 16px; }
    .error-table { width: 100%; }
    .stats { display: flex; gap: 24px; margin-bottom: 16px; }
    .stat { text-align: center; }
    .stat-value { font-size: 24px; font-weight: bold; }
    .stat-label { font-size: 12px; color: #666; }
  `,
})
export class CustomerImportComponent {
  private customerService = inject(CustomerService);
  private toast = inject(ToastNotificationService);

  @Output() importComplete = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  uploading = false;
  selectedFile: File | null = null;
  dragover = false;
  result: BulkUploadResult | null = null;
  errorColumns: string[] = ['row', 'field', 'message'];

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragover = true;
  }

  onDragLeave(): void {
    this.dragover = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragover = false;
    if (event.dataTransfer?.files?.length) {
      this.selectFile(event.dataTransfer.files[0]);
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.selectFile(input.files[0]);
    }
  }

  selectFile(file: File): void {
    if (!file.name.endsWith('.xlsx')) {
      this.toast.error('Please select an Excel file (.xlsx)');
      return;
    }
    this.selectedFile = file;
    this.result = null;
  }

  upload(): void {
    if (!this.selectedFile) return;
    this.uploading = true;
    this.customerService.bulkUpload(this.selectedFile).subscribe({
      next: (res) => {
        this.result = res;
        this.uploading = false;
        if (res.processed > 0) {
          this.toast.success(`${res.processed} customers imported`);
          this.importComplete.emit();
        }
        if (res.failed > 0) {
          this.toast.error(`${res.failed} rows had errors`);
        }
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Import failed');
        this.uploading = false;
      },
    });
  }

  onCancel(): void {
    this.cancelled.emit();
    this.selectedFile = null;
    this.result = null;
  }
}
