import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Transaction classes spanned by the unified submission log. */
export type SubmissionTransactionType =
  | 'INVOICE'
  | 'RECEIPT'
  | 'STANDARD'
  | 'SIMPLIFIED';

/** One row of the unified submission log (matches `SubmissionLogRowDto`). */
export interface SubmissionLogRow {
  attemptId: string;
  companyId: string;
  companyName: string;
  transactionType: SubmissionTransactionType;
  documentId: string;
  attemptNumber: number;
  outcome: string;
  statusCode: number | null;
  errorSummary: string | null;
  submittedAt: string;
  completedAt: string | null;
  submittedBy: string | null;
}

/** Paged envelope returned by the Wave-9 read endpoints. */
export interface SubmissionLogPage {
  items: SubmissionLogRow[];
  page: number;
  size: number;
  totalElements: number;
}

/** Filter/paging parameters accepted by the submission-log endpoint. */
export interface SubmissionLogQuery {
  companyId?: string;
  transactionType?: string;
  outcome?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}

/**
 * Maps a transaction class to its class-correct detail route. Kept here so the
 * component (and its tests) share a single source of truth for per-row links.
 */
const DETAIL_ROUTE: Record<SubmissionTransactionType, string> = {
  INVOICE: '/invoices/eta',
  RECEIPT: '/receipts/eta',
  STANDARD: '/standard',
  SIMPLIFIED: '/simplified',
};

@Injectable({ providedIn: 'root' })
export class SubmissionLogService {
  private http = inject(HttpClient);

  list(params: SubmissionLogQuery): Observable<SubmissionLogPage> {
    const query: string[] = [];
    if (params.companyId) query.push(`companyId=${params.companyId}`);
    if (params.transactionType) query.push(`transactionType=${params.transactionType}`);
    if (params.outcome) query.push(`outcome=${params.outcome}`);
    if (params.dateFrom) query.push(`dateFrom=${params.dateFrom}`);
    if (params.dateTo) query.push(`dateTo=${params.dateTo}`);
    query.push(`page=${params.page ?? 0}`);
    query.push(`size=${params.size ?? 20}`);
    return this.http.get<SubmissionLogPage>(
      `/api/submission-log?${query.join('&')}`);
  }

  /**
   * Resolves the class-correct router link for a row, e.g.
   * `SIMPLIFIED → ['/simplified', documentId]`.
   */
  detailLink(row: SubmissionLogRow): unknown[] {
    return [DETAIL_ROUTE[row.transactionType], row.documentId];
  }
}
