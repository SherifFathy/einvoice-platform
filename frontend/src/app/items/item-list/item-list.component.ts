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
import { ItemService, ItemResponse } from '../../shared/services/item.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-item-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatPaginatorModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatSelectModule, MatDialogModule,
  ],
  templateUrl: './item-list.component.html',
  styles: `
    .toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
    .toolbar .spacer { flex: 1; }
    .search-field { width: 250px; }
    .filter-select { width: 140px; }
    table { width: 100%; }
    .actions { display: flex; gap: 4px; }
  `,
})
export class ItemListComponent implements OnInit {
  private itemService = inject(ItemService);
  private toast = inject(ToastNotificationService);
  private dialog = inject(MatDialog);

  @Output() editItem = new EventEmitter<ItemResponse | null>();
  @Output() importItems = new EventEmitter<void>();

  items: ItemResponse[] = [];
  totalElements = 0;
  pageSize = 20;
  pageIndex = 0;
  searchValue = '';
  scopeFilter = '';
  displayedColumns: string[] = ['code', 'nameEn', 'nameAr', 'unitOfMeasure', 'unitPrice', 'vatCategory', 'vatRate', 'authorityScope', 'isActive', 'actions'];

  private searchSubject = new Subject<string>();

  constructor() {
    this.searchSubject.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => this.loadItems());
  }

  ngOnInit(): void {
    this.loadItems();
  }

  loadItems(): void {
    this.itemService.list(this.pageIndex, this.pageSize,
        this.searchValue || undefined,
        this.scopeFilter || undefined).subscribe({
      next: (page) => {
        this.items = page.content;
        this.totalElements = page.totalElements;
      },
      error: (err) => this.toast.error(err.error?.error || 'Failed to load items'),
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

  onScopeFilterChange(): void {
    this.pageIndex = 0;
    this.loadItems();
  }

  onEdit(item: ItemResponse): void {
    this.editItem.emit(item);
  }

  onDelete(item: ItemResponse): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: { title: 'Delete Item', message: `Delete "${item.nameEn}" (${item.code})? This action cannot be undone.` },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.itemService.delete(item.id).subscribe({
          next: () => {
            this.toast.success(`Item "${item.nameEn}" deleted`);
            this.loadItems();
          },
          error: (err) => this.toast.error(err.error?.error || 'Failed to delete item'),
        });
      }
    });
  }

  downloadTemplate(): void {
    this.itemService.downloadTemplate().subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'items-template.xlsx';
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.toast.error('Failed to download template'),
    });
  }
}
