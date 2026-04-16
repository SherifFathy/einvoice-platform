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
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { CustomerService, CustomerResponse } from '../../shared/services/customer.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-customer-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatPaginatorModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatSelectModule, MatDialogModule,
  ],
  templateUrl: './customer-list.component.html',
  styles: `
    .toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
    .toolbar .spacer { flex: 1; }
    .search-field { width: 250px; }
    .filter-select { width: 120px; }
    table { width: 100%; }
    .actions { display: flex; gap: 4px; }
  `,
})
export class CustomerListComponent implements OnInit {
  private customerService = inject(CustomerService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);

  @Output() editCustomer = new EventEmitter<CustomerResponse>();
  @Output() importCustomers = new EventEmitter<void>();

  customers: CustomerResponse[] = [];
  totalElements = 0;
  pageSize = 20;
  pageIndex = 0;
  searchValue = '';
  typeFilter = '';
  displayedColumns: string[] = ['nameEn', 'nameAr', 'vatNumber', 'customerType', 'contactEmail', 'isActive', 'actions'];

  private searchSubject = new Subject<string>();

  constructor() {
    this.searchSubject.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => this.loadCustomers());
  }

  ngOnInit(): void {
    this.loadCustomers();
  }

  loadCustomers(): void {
    this.customerService.list(this.pageIndex, this.pageSize,
        this.searchValue || undefined, this.typeFilter || undefined).subscribe({
      next: (page) => {
        this.customers = page.content;
        this.totalElements = page.totalElements;
      },
      error: (err) => this.toast.error(err.error?.error || 'Failed to load customers'),
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadCustomers();
  }

  onSearch(): void {
    this.searchSubject.next(this.searchValue);
  }

  onTypeFilterChange(): void {
    this.pageIndex = 0;
    this.loadCustomers();
  }

  onEdit(customer: CustomerResponse): void {
    this.editCustomer.emit(customer);
  }

  onDelete(customer: CustomerResponse): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: { title: 'Delete Customer', message: `Delete "${customer.nameEn}"? This action cannot be undone.` },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.customerService.delete(customer.id).subscribe({
          next: () => {
            this.toast.success(`Customer "${customer.nameEn}" deleted`);
            this.loadCustomers();
          },
          error: (err) => this.toast.error(err.error?.error || 'Failed to delete customer'),
        });
      }
    });
  }

  downloadTemplate(): void {
    this.customerService.downloadTemplate().subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'customers-template.xlsx';
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.toast.error('Failed to download template'),
    });
  }
}
