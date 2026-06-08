import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { AuthService } from '../../shared/services/auth.service';

export interface ZatcaStandardDocument {
  id: string;
  companyId: string;
  branchId: string | null;
  invoiceNumber: string;
  invoiceTypeCode: string;
  transactionTypeCode: string;
  businessProcessCode: string | null;
  issuanceReason: string | null;
  billingReferenceId: string | null;
  originalInvoiceNumber: string | null;
  erpReferenceId: string | null;
  issueDate: string;
  issueTime: string;
  supplyDate: string | null;
  supplyEndDate: string | null;
  sellerData: Record<string, unknown>;
  buyerData: Record<string, unknown>;
  sellerVatNumber: string | null;
  sellerCountryCode: string | null;
  buyerVatNumber: string | null;
  buyerCountryCode: string | null;
  currency: string;
  taxCurrency: string;
  lineExtensionAmount: string;
  taxExclusiveAmount: string;
  taxAmount: string;
  taxAmountAccountingCurrency: string;
  roundingAmount: string;
  taxInclusiveAmount: string;
  prepaidAmount: string;
  payableAmount: string;
  paymentMeansCode: string | null;
  paymentMeansText: string | null;
  invoiceCounterValue: number | null;
  previousInvoiceHash: string | null;
  invoiceHash: string | null;
  qrCodeBase64: string | null;
  clearanceStatus: string | null;
  zatcaResponseData: Record<string, unknown> | null;
  originalInvoiceId: string | null;
  status: string;
  version: number;
  zatcaUuid: string | null;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
  lines: ZatcaStandardLine[];
}

export interface ZatcaStandardLine {
  id: string;
  itemId: string | null;
  itemCode: string;
  description: string;
  unitType: string;
  quantity: string;
  unitPrice: string;
  lineExtensionAmount: string;
  discountAmount: string;
  allowanceAmount: string;
  netAmount: string;
  vatCategoryCode: string;
  vatRate: string | null;
  vatAmount: string;
  exemptionReasonCode: string | null;
  exemptionReasonText: string | null;
}

export interface ZatcaStandardListResult {
  items: ZatcaStandardDocument[];
  page: number;
  size: number;
  totalElements: number;
}

export interface ConflictBody {
  code: string;
  message: string;
  expectedVersion: number;
  actualVersion: number;
  current: ZatcaStandardDocument;
}

export interface SubmissionAttemptResponse {
  id: string;
  attemptNumber: number;
  chainCounterSnapshot: number | null;
  result: string;
  errorSummary: string | null;
  submittedBy: string | null;
  startedAt: string;
  finalisedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class ZatcaStandardService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);

  list(params: {
    status?: string;
    company?: string;
    dateFrom?: string;
    dateTo?: string;
    page?: number;
    size?: number;
  }): Observable<ZatcaStandardListResult> {
    const query: string[] = [];
    if (params.status) query.push(`status=${params.status}`);
    if (params.company) query.push(`company=${params.company}`);
    if (params.dateFrom) query.push(`dateFrom=${params.dateFrom}`);
    if (params.dateTo) query.push(`dateTo=${params.dateTo}`);
    query.push(`page=${params.page ?? 0}`);
    query.push(`size=${params.size ?? 50}`);
    return this.http.get<ZatcaStandardListResult>(
        `/api/zatca/standard?${query.join('&')}`);
  }

  getById(id: string): Observable<HttpResponse<ZatcaStandardDocument>> {
    return this.http.get<ZatcaStandardDocument>(
        `/api/zatca/standard/${id}`,
        { observe: 'response' });
  }

  create(companyId: string, body: unknown): Observable<HttpResponse<ZatcaStandardDocument>> {
    return this.http.post<ZatcaStandardDocument>(
        `/api/companies/${companyId}/zatca/standard`, body,
        { observe: 'response' });
  }

  update(companyId: string, id: string, body: unknown, ifMatch: string):
      Observable<HttpResponse<ZatcaStandardDocument>> {
    return this.http.put<ZatcaStandardDocument>(
        `/api/companies/${companyId}/zatca/standard/${id}`, body,
        { headers: new HttpHeaders({ 'If-Match': `"${ifMatch}"` }), observe: 'response' })
      .pipe(catchError(error => {
        if (error.status === 409
            && error.error?.code === 'OPTIMISTIC_LOCK_CONFLICT') {
          return throwError(() => error.error as ConflictBody);
        }
        return throwError(() => error);
      }));
  }

  delete(companyId: string, id: string): Observable<void> {
    return this.http.delete<void>(`/api/companies/${companyId}/zatca/standard/${id}`);
  }

  submit(companyId: string, id: string): Observable<{ state: string; clearanceStatus: string }> {
    return this.http.post<{ state: string; clearanceStatus: string }>(
        `/api/companies/${companyId}/zatca/standard/${id}/submit`, null);
  }

  cancel(companyId: string, id: string, reason: string): Observable<{ state: string; clearanceStatus: string }> {
    return this.http.post<{ state: string; clearanceStatus: string }>(
        `/api/companies/${companyId}/zatca/standard/${id}/cancel`,
        { reason });
  }

  retry(companyId: string, id: string): Observable<{ state: string; clearanceStatus: string }> {
    return this.http.post<{ state: string; clearanceStatus: string }>(
        `/api/companies/${companyId}/zatca/standard/${id}/retry`, null);
  }

  checkStatus(companyId: string, id: string): Observable<{ state: string; clearanceStatus: string }> {
    return this.http.post<{ state: string; clearanceStatus: string }>(
        `/api/companies/${companyId}/zatca/standard/${id}/check-status`, null);
  }

  cloneAsDraft(companyId: string, sourceId: string, newInvoiceNumber: string):
      Observable<HttpResponse<ZatcaStandardDocument>> {
    return this.http.post<ZatcaStandardDocument>(
        `/api/companies/${companyId}/zatca/standard/${sourceId}/clone-as-draft`,
        { newInvoiceNumber }, { observe: 'response' });
  }

  getSubmissions(companyId: string, docId: string): Observable<SubmissionAttemptResponse[]> {
    return this.http.get<SubmissionAttemptResponse[]>(
        `/api/companies/${companyId}/zatca/standard/${docId}/submissions`);
  }

  getArtifactUrl(companyId: string, docId: string, type: string): string {
    return `/api/companies/${companyId}/zatca/standard/${docId}/artifacts/${type}`;
  }

  bulkCheckStatus(companyId: string, documentIds: string[]): Promise<Response> {
    const token = this.auth.getToken();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) { headers['Authorization'] = `Bearer ${token}`; }
    return fetch(`/api/companies/${companyId}/zatca/standard/check-status`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ documentIds }),
    });
  }

  cancelRun(runId: string): Promise<Response> {
    const token = this.auth.getToken();
    const headers: Record<string, string> = {};
    if (token) { headers['Authorization'] = `Bearer ${token}`; }
    return fetch(`/api/runs/${runId}`, {
      method: 'DELETE',
      headers,
    });
  }
}
