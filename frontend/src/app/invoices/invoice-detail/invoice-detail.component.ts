import { Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { InvoiceService, InvoiceDetailResponse, SubmissionAttemptResponse, SubmitResponse } from '../../shared/services/invoice.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { CancelEtaDialogComponent } from '../../shared/dialogs/cancel-eta-dialog.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { SubmissionTimelineComponent } from '../../shared/components/submission-timeline/submission-timeline.component';

@Component({
  selector: 'app-invoice-detail',
  standalone: true,
  imports: [
    CommonModule, MatButtonModule, MatIconModule, MatCardModule, MatDividerModule,
    MatDialogModule, FormsModule, MatFormFieldModule, MatInputModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent, SubmissionTimelineComponent,
  ],
  templateUrl: './invoice-detail.component.html',
  styles: `
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 8px; }
    .header-title { display: flex; align-items: center; gap: 10px; }
    .actions-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .meta-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; margin-bottom: 16px; }
    .meta-label { font-weight: 500; color: #666; }
    .meta-value { margin-top: 4px; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #e0e0e0; }
    th { background: #f5f5f5; font-weight: 500; }
    .amount { text-align: right; }
    .totals-section { margin-top: 16px; max-width: 400px; margin-left: auto; }
    .totals-row { display: flex; justify-content: space-between; padding: 4px 0; }
    .totals-row.grand { font-weight: bold; font-size: 1.1em; border-top: 2px solid #333; padding-top: 8px; margin-top: 4px; }
    .vat-section { margin-top: 16px; }
    .section-title { font-size: 1em; font-weight: 600; margin: 20px 0 8px 0; color: #333; }
    .artifact-list { display: flex; gap: 8px; flex-wrap: wrap; }
  `,
})
export class InvoiceDetailComponent implements OnChanges {
  private invoiceService = inject(InvoiceService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);

  @Input() invoiceId: string | null = null;
  @Output() back = new EventEmitter<void>();
  @Output() edit = new EventEmitter<string>();
  @Output() statusChanged = new EventEmitter<void>();

  invoice: InvoiceDetailResponse | null = null;
  loading = false;
  cancelling = false;
  submitting = false;
  retrying = false;
  returningToDraft = false;

  submissionAttempts: SubmissionAttemptResponse[] = [];
  artifacts: { type: string; createdAt: string }[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['invoiceId'] && this.invoiceId) {
      this.loadInvoice();
    }
  }

  loadInvoice(): void {
    if (!this.invoiceId) return;
    this.loading = true;
    this.invoiceService.get(this.invoiceId).subscribe({
      next: (inv) => {
        this.invoice = inv;
        this.loading = false;
        this.loadSubmissions();
        this.loadArtifacts();
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to load invoice');
        this.loading = false;
      },
    });
  }

  private loadSubmissions(): void {
    if (!this.invoiceId) return;
    this.invoiceService.getSubmissions(this.invoiceId).subscribe({
      next: (res) => { this.submissionAttempts = res.attempts; },
      error: () => { this.submissionAttempts = []; },
    });
  }

  private loadArtifacts(): void {
    if (!this.invoiceId) return;
    this.invoiceService.getArtifacts(this.invoiceId).subscribe({
      next: (res) => { this.artifacts = res.artifacts; },
      error: () => { this.artifacts = []; },
    });
  }

  onEdit(): void {
    if (this.invoice) {
      this.edit.emit(this.invoice.id);
    }
  }

  onBack(): void {
    this.back.emit();
  }

  onCancelEta(): void {
    if (!this.invoice || !this.invoiceId || this.cancelling) return;
    const dialogRef = this.dialog.open(CancelEtaDialogComponent, {
      width: '400px',
      data: { reason: '' },
    });
    dialogRef.afterClosed().subscribe((result: { reason: string } | undefined) => {
      if (!result) return;
      this.cancelling = true;
      this.invoiceService.cancelEta(this.invoiceId!, result.reason || '').subscribe({
        next: () => {
          this.toast.success('Invoice cancelled successfully');
          this.cancelling = false;
          this.statusChanged.emit();
          this.loadInvoice();
        },
        error: (err) => {
          this.toast.error(err.error?.error || 'Failed to cancel invoice');
          this.cancelling = false;
        },
      });
    });
  }

  onSubmit(): void {
    if (!this.invoiceId || this.submitting) return;
    this.submitting = true;
    this.invoiceService.submit(this.invoiceId).subscribe({
      next: (res: SubmitResponse) => {
        this.submitting = false;
        if (res.errors && res.errors.length > 0) {
          this.toast.warning(`Submission completed with ${res.errors.length} error(s)`);
        } else {
          this.toast.success('Invoice submitted successfully');
        }
        this.statusChanged.emit();
        this.loadInvoice();
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Submission failed');
        this.submitting = false;
      },
    });
  }

  onRetry(): void {
    if (!this.invoiceId || this.retrying) return;
    this.retrying = true;
    this.invoiceService.retry(this.invoiceId).subscribe({
      next: (res: SubmitResponse) => {
        this.retrying = false;
        if (res.errors && res.errors.length > 0) {
          this.toast.warning(`Retry completed with ${res.errors.length} error(s)`);
        } else {
          this.toast.success('Retry submitted successfully');
        }
        this.statusChanged.emit();
        this.loadInvoice();
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Retry failed');
        this.retrying = false;
      },
    });
  }

  onReturnToDraft(): void {
    if (!this.invoiceId || this.returningToDraft) return;
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Return to Draft',
        message: 'Return this rejected invoice to draft for correction?',
      },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.returningToDraft = true;
      this.invoiceService.returnToDraft(this.invoiceId!).subscribe({
        next: () => {
          this.toast.success('Invoice returned to draft');
          this.returningToDraft = false;
          this.statusChanged.emit();
          this.loadInvoice();
        },
        error: (err) => {
          this.toast.error(err.error?.error || 'Failed to return to draft');
          this.returningToDraft = false;
        },
      });
    });
  }

  onCheckStatus(): void {
    if (!this.invoiceId) return;
    this.invoiceService.checkStatus(this.invoiceId).subscribe({
      next: (res) => {
        this.toast.info(`Status: ${res.previousStatus} \u2192 ${res.status}`);
        this.statusChanged.emit();
        this.loadInvoice();
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Status check failed');
      },
    });
  }

  private static readonly ARTIFACT_EXTENSIONS: Record<string, string> = {
    SIGNED_XML: '.xml',
    CLEARED_XML: '.xml',
    SIGNED_JSON: '.json',
    ETA_RESPONSE: '.json',
    ZATCA_RESPONSE: '.json',
    QR_CODE: '.png',
    ETA_PDF: '.pdf',
    ETA_CADES_SIG: '.p7s',
  };

  onDownloadArtifact(type: string): void {
    if (!this.invoiceId) return;
    this.invoiceService.getArtifact(this.invoiceId, type).subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const ext = InvoiceDetailComponent.ARTIFACT_EXTENSIONS[type] ?? '.bin';
        a.download = `invoice-${this.invoice?.invoiceNumber ?? this.invoiceId}-${type.toLowerCase()}${ext}`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Download failed');
      },
    });
  }

  onDownloadEtaPdf(): void {
    if (!this.invoiceId) return;
    this.invoiceService.getEtaPdf(this.invoiceId).subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `invoice-${this.invoice?.invoiceNumber ?? this.invoiceId}-eta.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'PDF download failed');
      },
    });
  }

  get canSubmit(): boolean {
    return this.invoice?.status === 'VALIDATED' || this.invoice?.status === 'READY_FOR_SUBMISSION';
  }

  get canRetry(): boolean {
    return this.invoice?.status === 'FAILED_RETRYABLE';
  }

  get canReturnToDraft(): boolean {
    return this.invoice?.status === 'REJECTED';
  }

  get canCheckStatus(): boolean {
    return this.invoice?.status === 'IN_REVIEW' || this.invoice?.status === 'SUBMISSION_AMBIGUOUS';
  }

  get canCancelEta(): boolean {
    return this.invoice?.status === 'ACCEPTED' && this.invoice?.authority === 'ETA';
  }

  get hasArtifacts(): boolean {
    return this.artifacts.length > 0;
  }

  formatArtifactType(type: string): string {
    return type.replace(/_/g, ' ');
  }
}
