import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { ZatcaCustomerService, ZatcaCustomerResponse } from '../services/zatca-customer.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import type { SessionContext } from '../../shared/services/auth.service';

@Component({
  selector: 'app-zatca-customer-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule, MatTableModule, MatPaginatorModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatSelectModule, MatSlideToggleModule, MatTooltipModule, MatDialogModule,
    HasPermissionDirective,
  ],
  templateUrl: './zatca-customer-list.component.html',
  styleUrls: ['./zatca-customer-list.component.scss'],
})
export class ZatcaCustomerListComponent implements OnInit, OnDestroy {
  private customerService = inject(ZatcaCustomerService);
  private sessionCtx = inject(SessionContextService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);
  private destroy$ = new Subject<void>();

  customers: ZatcaCustomerResponse[] = [];
  totalElements = 0;
  pageSize = 20;
  pageIndex = 0;
  searchValue = '';
  showInactive = false;
  selectedCompanyId = '';
  companies: SessionContext['companies'] = [];
  companyNameMap: Record<string, string> = {};

  displayedColumns: string[] = [
    'company', 'nameEn', 'nameAr', 'vatNumber', 'customerType', 'isActive', 'actions',
  ];

  private searchSubject = new Subject<string>();

  constructor() {
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$),
    ).subscribe(() => this.loadCustomers());
  }

  ngOnInit(): void {
    this.sessionCtx.context$.pipe(takeUntil(this.destroy$)).subscribe((ctx) => {
      if (ctx) {
        this.companies = ctx.companies;
        this.companyNameMap = {};
        for (const c of ctx.companies) {
          this.companyNameMap[c.companyId] = c.companyNameEn;
        }
        this.loadCustomers();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadCustomers(): void {
    const pathCompanyId = this.sessionCtx.currentContext?.activeCompanyId ?? '';
    if (!pathCompanyId) return;
    const filterId = this.selectedCompanyId || undefined;
    this.customerService.list(
      pathCompanyId, this.pageIndex, this.pageSize,
      this.searchValue || undefined, this.showInactive, filterId,
    ).subscribe({
      next: (res) => {
        this.customers = res.items;
        this.totalElements = res.page.total;
      },
      error: (err) => this.toast.error(err.error?.error || 'Failed to load ZATCA customers'),
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

  onCompanyChange(): void {
    this.pageIndex = 0;
    this.loadCustomers();
  }

  onShowInactiveToggle(): void {
    this.pageIndex = 0;
    this.loadCustomers();
  }

  onDelete(customer: ZatcaCustomerResponse): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Delete Customer',
        message: `Delete "${customer.nameEn}"? This action cannot be undone.`,
      },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.customerService.delete(customer.companyId, customer.id).subscribe({
          next: () => {
            this.toast.success(`Customer "${customer.nameEn}" deleted`);
            this.loadCustomers();
          },
          error: (err) => this.toast.error(err.error?.error || 'Failed to delete customer'),
        });
      }
    });
  }
}
