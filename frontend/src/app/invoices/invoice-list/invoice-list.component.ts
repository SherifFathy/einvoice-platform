import { Component, EventEmitter, inject, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { InvoiceService, InvoiceListResponse } from '../../shared/services/invoice.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-invoice-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatPaginatorModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatSelectModule, MatDatepickerModule, MatNativeDateModule,
    MatDialogModule,
  ],
  templateUrl: './invoice-list.component.html',
  styles: `
    .toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
    .toolbar .spacer { flex: 1; }
    .search-field { width: 250px; }
    .filter-select { width: 150px; }
    .date-field { width: 150px; }
    table { width: 100%; }
    .actions { display: flex; gap: 4px; }
    .amount { text-align: right; }
  `,
})
export class InvoiceListComponent implements OnInit {
  private invoiceService = inject(InvoiceService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);

  @Output() editInvoice = new EventEmitter<string>();
  @Output() viewInvoice = new EventEmitter<string>();
  @Output() createInvoice = new EventEmitter<void>();

  invoices: InvoiceListResponse[] = [];
  totalElements = 0;
  pageSize = 20;
  pageIndex = 0;
  searchValue = '';
  statusFilter = '';
  typeFilter = '';
  dateFrom: Date | null = null;
  dateTo: Date | null = null;
  displayedColumns: string[] = ['invoiceNumber', 'type', 'status', 'issueDate', 'buyerName', 'totalWithVat', 'authority', 'actions'];

  private searchSubject = new Subject<string>();

  constructor() {
    this.searchSubject.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => this.loadInvoices());
  }

  ngOnInit(): void {
    this.loadInvoices();
  }

  loadInvoices(): void {
    const dateFromStr = this.dateFrom ? this.formatDate(this.dateFrom) : undefined;
    const dateToStr = this.dateTo ? this.formatDate(this.dateTo) : undefined;
    this.invoiceService.list(this.pageIndex, this.pageSize,
        this.statusFilter || undefined,
        this.typeFilter || undefined,
        dateFromStr, dateToStr,
        this.searchValue || undefined).subscribe({
      next: (page) => {
        this.invoices = page.content;
        this.totalElements = page.totalElements;
      },
      error: (err) => this.toast.error(err.error?.error || 'Failed to load invoices'),
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadInvoices();
  }

  onSearch(): void {
    this.searchSubject.next(this.searchValue);
  }

  onFilterChange(): void {
    this.pageIndex = 0;
    this.loadInvoices();
  }

  onView(invoice: InvoiceListResponse): void {
    this.viewInvoice.emit(invoice.id);
  }

  onEdit(invoice: InvoiceListResponse): void {
    this.editInvoice.emit(invoice.id);
  }

  onCancel(invoice: InvoiceListResponse): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: { title: 'Cancel Invoice', message: `Cancel draft invoice "${invoice.invoiceNumber}"?` },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.invoiceService.cancel(invoice.id).subscribe({
          next: () => {
            this.toast.success(`Invoice "${invoice.invoiceNumber}" cancelled`);
            this.loadInvoices();
          },
          error: (err) => this.toast.error(err.error?.error || 'Failed to cancel invoice'),
        });
      }
    });
  }

  private formatDate(date: Date): string {
    return date.toISOString().split('T')[0];
  }
}
