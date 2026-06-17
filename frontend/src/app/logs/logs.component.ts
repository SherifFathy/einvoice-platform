import { Component, inject, OnInit } from '@angular/core';
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
import { MatExpansionModule } from '@angular/material/expansion';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { AuditLogService, AuditLogResponse } from '../shared/services/audit-log.service';
import { PageResponse } from '../shared/components/data-table/data-table.component';
import { SessionContextService } from '../shared/services/session-context.service';
import { SessionContext } from '../shared/services/auth.service';
import { ToastNotificationService } from '../shared/services/toast.service';

@Component({
  selector: 'app-logs',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatPaginatorModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatSelectModule, MatDatepickerModule, MatNativeDateModule,
    MatExpansionModule,
  ],
  templateUrl: './logs.component.html',
  styles: `
    .toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
    .toolbar .spacer { flex: 1; }
    .filter-select { width: 180px; }
    .filter-input { width: 160px; }
    .date-field { width: 150px; }
    table { width: 100%; }
    .mono { font-family: monospace; font-size: 13px; }
    .diff-container { display: flex; gap: 16px; flex-wrap: wrap; }
    .diff-panel { flex: 1; min-width: 300px; }
    .diff-panel pre {
      background: #f5f5f5; border: 1px solid #e0e0e0; border-radius: 4px;
      padding: 12px; margin: 0; white-space: pre-wrap; word-break: break-word;
      font-size: 12px; max-height: 300px; overflow-y: auto;
    }
    .diff-panel h4 { margin: 0 0 8px 0; font-size: 13px; color: #666; }
    .badge {
      display: inline-block; padding: 2px 8px; border-radius: 12px;
      font-size: 11px; font-weight: 500; text-transform: uppercase;
    }
    .badge-create { background: #e8f5e9; color: #2e7d32; }
    .badge-update { background: #e3f2fd; color: #1565c0; }
    .badge-delete { background: #fce4ec; color: #c62828; }
    .badge-deactivate { background: #fff3e0; color: #e65100; }
    .badge-activate { background: #e8f5e9; color: #2e7d32; }
    .badge-default { background: #f5f5f5; color: #616161; }
    .action-cell { text-transform: none; }
  `,
})
export class LogsComponent implements OnInit {
  private auditLogService = inject(AuditLogService);
  private sessionCtx = inject(SessionContextService);
  private toast = inject(ToastNotificationService);

  logs: AuditLogResponse[] = [];
  totalElements = 0;
  pageSize = 20;
  pageIndex = 0;
  entityTypeFilter = '';
  entityIdFilter = '';
  companyFilter = '';
  dateFrom: Date | null = null;
  dateTo: Date | null = null;
  expandedLogId: number | null = null;

  companies: SessionContext['companies'] = [];
  showCompanyColumn = false;

  private filterSubject = new Subject<void>();

  constructor() {
    this.filterSubject.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => this.loadLogs());
  }

  ngOnInit(): void {
    const ctx = this.sessionCtx.currentContext;
    if (ctx) {
      this.companies = ctx.companies ?? [];
      this.showCompanyColumn = this.companies.length > 1;
    }
    this.loadLogs();
  }

  get displayedColumns(): string[] {
    const base = ['action', 'userId', 'entityType', 'entityId', 'ipAddress', 'timestamp', 'details'];
    return this.showCompanyColumn ? ['company', ...base] : base;
  }

  loadLogs(): void {
    const fromStr = this.dateFrom ? this.dateFrom.toISOString() : undefined;
    const toStr = this.dateTo ? this.dateTo.toISOString() : undefined;
    this.auditLogService.list(
        this.pageIndex, this.pageSize,
        this.entityTypeFilter || undefined,
        this.entityIdFilter || undefined,
        fromStr, toStr,
        this.companyFilter || undefined).subscribe({
      next: (page: PageResponse<AuditLogResponse>) => {
        this.logs = page.content;
        this.totalElements = page.totalElements;
      },
      error: (err: unknown) => {
        const errorObj = err as { error?: { error?: string } };
        this.toast.error(errorObj.error?.error || 'Failed to load audit logs');
      },
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadLogs();
  }

  onFilterChange(): void {
    this.pageIndex = 0;
    this.loadLogs();
  }

  onDebouncedFilter(): void {
    this.pageIndex = 0;
    this.filterSubject.next();
  }

  toggleDetails(log: AuditLogResponse): void {
    this.expandedLogId = this.expandedLogId === log.id ? null : log.id;
  }

  formatJson(json: string | null): string {
    if (!json) return '';
    try {
      return JSON.stringify(JSON.parse(json), null, 2);
    } catch {
      return json;
    }
  }

  getActionBadgeClass(action: string): string {
    if (action.includes('create')) return 'badge-create';
    if (action.includes('update') || action.includes('assign')) return 'badge-update';
    if (action.includes('delete') || action.includes('remove')) return 'badge-delete';
    if (action.includes('deactivate')) return 'badge-deactivate';
    if (action.includes('activate')) return 'badge-activate';
    return 'badge-default';
  }

  formatTimestamp(ts: string): string {
    return new Date(ts).toLocaleString();
  }
}
