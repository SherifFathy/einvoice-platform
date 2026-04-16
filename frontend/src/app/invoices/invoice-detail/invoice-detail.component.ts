import { Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { InvoiceService, InvoiceDetailResponse } from '../../shared/services/invoice.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-invoice-detail',
  standalone: true,
  imports: [
    CommonModule, MatButtonModule, MatIconModule, MatCardModule, MatDividerModule,
  ],
  templateUrl: './invoice-detail.component.html',
  styles: `
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
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
    .status-badge { padding: 4px 12px; border-radius: 12px; font-size: 0.85em; font-weight: 500; }
    .status-DRAFT { background: #e3f2fd; color: #1565c0; }
    .status-CANCELLED { background: #fce4ec; color: #c62828; }
  `,
})
export class InvoiceDetailComponent implements OnChanges {
  private invoiceService = inject(InvoiceService);
  private toast = inject(ToastNotificationService);

  @Input() invoiceId: string | null = null;
  @Output() back = new EventEmitter<void>();
  @Output() edit = new EventEmitter<string>();

  invoice: InvoiceDetailResponse | null = null;
  loading = false;

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
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to load invoice');
        this.loading = false;
      },
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
}
