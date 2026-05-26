import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

export interface TableColumn {
  field: string;
  header: string;
  type?: 'text' | 'date' | 'boolean';
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
  ],
  templateUrl: './data-table.component.html',
  styleUrls: ['./data-table.component.scss'],
})
export class DataTableComponent<T> implements OnChanges {
  @Input() columns: TableColumn[] = [];
  @Input() data: T[] = [];
  @Input() totalElements = 0;
  @Input() pageSize = 20;
  @Input() pageIndex = 0;
  @Input() searchPlaceholder = 'Search...';
  @Input() showSearch = true;

  @Output() pageChange = new EventEmitter<PageEvent>();
  @Output() sortChange = new EventEmitter<Sort>();
  @Output() search = new EventEmitter<string>();
  @Output() rowClick = new EventEmitter<T>();

  displayedColumns: string[] = [];
  searchValue = '';
  private searchSubject = new Subject<string>();

  constructor() {
    this.searchSubject
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe((term) => this.search.emit(term));
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['columns']) {
      this.displayedColumns = this.columns.map((c) => c.field);
    }
  }

  onPageChange(event: PageEvent): void {
    this.pageChange.emit(event);
  }

  onSortChange(event: Sort): void {
    this.sortChange.emit(event);
  }

  onSearchDebounced(): void {
    this.searchSubject.next(this.searchValue);
  }

  onRowClick(row: T): void {
    this.rowClick.emit(row);
  }
}
