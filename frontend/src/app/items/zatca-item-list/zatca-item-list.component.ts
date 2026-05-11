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
import { ZatcaItemService, ZatcaItemResponse } from '../services/zatca-item.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import type { SessionContext } from '../../shared/services/auth.service';

@Component({
  selector: 'app-zatca-item-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule, MatTableModule, MatPaginatorModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatSelectModule, MatSlideToggleModule, MatTooltipModule, MatDialogModule,
    HasPermissionDirective,
  ],
  templateUrl: './zatca-item-list.component.html',
  styleUrls: ['./zatca-item-list.component.scss'],
})
export class ZatcaItemListComponent implements OnInit, OnDestroy {
  private itemService = inject(ZatcaItemService);
  private sessionCtx = inject(SessionContextService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);
  private destroy$ = new Subject<void>();

  items: ZatcaItemResponse[] = [];
  totalElements = 0;
  pageSize = 20;
  pageIndex = 0;
  searchValue = '';
  showInactive = false;
  selectedCompanyId = '';
  companies: SessionContext['companies'] = [];
  companyNameMap: Record<string, string> = {};

  displayedColumns: string[] = [
    'company', 'internalCode', 'nameEn', 'nameAr', 'vatCategory', 'vatRate', 'isActive', 'actions',
  ];

  private searchSubject = new Subject<string>();

  constructor() {
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$),
    ).subscribe(() => this.loadItems());
  }

  ngOnInit(): void {
    this.sessionCtx.context$.pipe(takeUntil(this.destroy$)).subscribe((ctx) => {
      if (ctx) {
        this.companies = ctx.companies;
        this.companyNameMap = {};
        for (const c of ctx.companies) {
          this.companyNameMap[c.companyId] = c.companyNameEn;
        }
        this.loadItems();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadItems(): void {
    const pathCompanyId = this.sessionCtx.currentContext?.activeCompanyId ?? '';
    if (!pathCompanyId) return;
    const filterId = this.selectedCompanyId || undefined;
    this.itemService.list(
      pathCompanyId, this.pageIndex, this.pageSize,
      this.searchValue || undefined, this.showInactive, filterId,
    ).subscribe({
      next: (res) => {
        this.items = res.items;
        this.totalElements = res.page.total;
      },
      error: (err) => this.toast.error(err.error?.error || 'Failed to load ZATCA items'),
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadItems();
  }

  onSearch(): void {
    this.searchSubject.next(this.searchValue);
  }

  onCompanyChange(): void {
    this.pageIndex = 0;
    this.loadItems();
  }

  onShowInactiveToggle(): void {
    this.pageIndex = 0;
    this.loadItems();
  }

  onDelete(item: ZatcaItemResponse): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Delete Item',
        message: `Delete "${item.nameEn}"? This action cannot be undone.`,
      },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.itemService.delete(item.companyId, item.id).subscribe({
          next: () => {
            this.toast.success(`Item "${item.nameEn}" deleted`);
            this.loadItems();
          },
          error: (err) => this.toast.error(err.error?.error || 'Failed to delete item'),
        });
      }
    });
  }
}
