import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';

export interface EtaReceipt {
  id: string;
  companyId: string;
  branchId: string | null;
  receiptNumber: string;
  documentType: string;
  documentTypeVersion: string;
  issueDatetime: string;
  sellerData: Record<string, unknown>;
  buyerData: Record<string, unknown> | null;
  posSerial: string | null;
  paymentMethod: string | null;
  currency: string;
  totalSalesAmount: string;
  totalCommercialDiscount: string;
  extraDiscountAmount: string;
  totalItemsDiscountAmount: string;
  netAmount: string;
  totalAmount: string;
  exchangeRate: string | null;
  previousUuid: string | null;
  referenceOldUuid: string | null;
  sOrderNameCode: string | null;
  orderDeliveryMode: string | null;
  grossWeight: string | null;
  netWeight: string | null;
  taxTotals: Record<string, unknown> | null;
  extraReceiptDiscountData: Record<string, unknown> | null;
  contractorData: Record<string, unknown> | null;
  beneficiaryData: Record<string, unknown> | null;
  feesAmount: string;
  adjustment: string;
  erpReferenceId: string | null;
  originalInvoiceNumber: string | null;
  originalReceiptId: string | null;
  state: string;
  version: number;
  etaReceiptUuid: string | null;
  etaSubmissionId: string | null;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
  lines: EtaReceiptLine[];
}

export interface EtaReceiptLine {
  id: string;
  itemId: string | null;
  internalCode: string | null;
  itemType: string;
  itemCode: string;
  description: string;
  unitType: string;
  quantity: string;
  unitPrice: string;
  commercialDiscountData: unknown[];
  itemDiscountData: unknown[];
  salesTotal: string;
  valueDifference: string;
  totalTaxableFees: string;
  netTotal: string;
  taxAmount: string;
  total: string;
  taxes: EtaLineTax[];
}

export interface EtaLineTax {
  id: string;
  taxType: string;
  subType: string | null;
  taxRate: string | null;
  taxAmount: string;
}

export interface EtaReceiptListResult {
  items: EtaReceipt[];
  page: number;
  size: number;
  totalElements: number;
}

export interface ConflictBody {
  code: string;
  message: string;
  expectedVersion: number;
  actualVersion: number;
  current: EtaReceipt;
}

@Injectable({ providedIn: 'root' })
export class EtaReceiptService {
  private http = inject(HttpClient);

  list(params: {
    status?: string;
    companyId?: string;
    branchId?: string;
    receiptType?: string;
    dateFrom?: string;
    dateTo?: string;
    page?: number;
    size?: number;
  }): Observable<EtaReceiptListResult> {
    const query: string[] = [];
    if (params.status) query.push(`status=${params.status}`);
    if (params.companyId) query.push(`companyId=${params.companyId}`);
    if (params.branchId) query.push(`branchId=${params.branchId}`);
    if (params.receiptType) query.push(`receiptType=${params.receiptType}`);
    if (params.dateFrom) query.push(`dateFrom=${params.dateFrom}`);
    if (params.dateTo) query.push(`dateTo=${params.dateTo}`);
    query.push(`page=${params.page ?? 0}`);
    query.push(`size=${params.size ?? 50}`);
    return this.http.get<EtaReceiptListResult>(
        `/api/eta/receipts?${query.join('&')}`);
  }

  getById(id: string): Observable<HttpResponse<EtaReceipt>> {
    return this.http.get<EtaReceipt>(
        `/api/eta/receipts/${id}`,
        { observe: 'response' });
  }

  create(companyId: string, body: unknown): Observable<HttpResponse<EtaReceipt>> {
    return this.http.post<EtaReceipt>(
        `/api/companies/${companyId}/eta/receipts`, body,
        { observe: 'response' });
  }

  update(companyId: string, id: string, body: unknown, ifMatch: string):
      Observable<HttpResponse<EtaReceipt>> {
    return this.http.put<EtaReceipt>(
        `/api/companies/${companyId}/eta/receipts/${id}`, body,
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
    return this.http.delete<void>(`/api/companies/${companyId}/eta/receipts/${id}`);
  }

  submit(companyId: string, id: string): Observable<{ state: string; etaReceiptUuid: string; etaSubmissionId: string }> {
    return this.http.post<{ state: string; etaReceiptUuid: string; etaSubmissionId: string }>(
        `/api/companies/${companyId}/eta/receipts/${id}/submit`, null);
  }

  cancel(companyId: string, id: string, reason: string): Observable<{ state: string }> {
    return this.http.post<{ state: string }>(
        `/api/companies/${companyId}/eta/receipts/${id}/cancel`, { reason });
  }

  retry(companyId: string, id: string): Observable<{ state: string; etaReceiptUuid: string; etaSubmissionId: string }> {
    return this.http.post<{ state: string; etaReceiptUuid: string; etaSubmissionId: string }>(
        `/api/companies/${companyId}/eta/receipts/${id}/retry`, null);
  }

  cloneAsDraft(companyId: string, rejectedId: string, newReceiptNumber: string): Observable<HttpResponse<EtaReceipt>> {
    return this.http.post<EtaReceipt>(
        `/api/companies/${companyId}/eta/receipts/${rejectedId}/clone-as-draft`,
        { receiptNumber: newReceiptNumber }, { observe: 'response' });
  }

  checkStatus(companyId: string, documentIds: string[]): Observable<{ results: BulkStatusOutcome[] }> {
    return this.http.post<{ results: BulkStatusOutcome[] }>(
        `/api/companies/${companyId}/eta/receipts/check-status`,
        { documentIds });
  }

  getSubmissions(companyId: string, docId: string): Observable<SubmissionAttemptResponse[]> {
    return this.http.get<SubmissionAttemptResponse[]>(
        `/api/companies/${companyId}/eta/receipts/${docId}/submissions`);
  }

  getArtifactUrl(companyId: string, docId: string, type: string, attemptNumber?: number): string {
    let url = `/api/companies/${companyId}/eta/receipts/${docId}/artifacts/${type}`;
    if (attemptNumber) url += `?attemptNumber=${attemptNumber}`;
    return url;
  }
}

export interface BulkStatusOutcome {
  documentId: string;
  outcome: string;
  beforeState: string | null;
  afterState: string | null;
  etaResultCode: string | null;
  errorSummary: string | null;
}

export interface SubmissionAttemptResponse {
  id: string;
  attemptNumber: number;
  submittedBy: string;
  result: string;
  statusCode: number;
  errorSummary: string;
  submittedAt: string;
  completedAt: string;
}
