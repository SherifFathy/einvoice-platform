import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';

export interface EtaInvoice {
  id: string;
  companyId: string;
  branchId: string | null;
  invoiceNumber: string;
  documentType: string;
  documentTypeVersion: string;
  issueDatetime: string;
  serviceDeliveryDate: string | null;
  sellerData: Record<string, unknown>;
  buyerData: Record<string, unknown>;
  taxpayerActivityCode: string | null;
  purchaseOrderReference: string | null;
  purchaseOrderDescription: string | null;
  salesOrderReference: string | null;
  salesOrderDescription: string | null;
  proformaInvoiceNumber: string | null;
  paymentData: Record<string, unknown> | null;
  deliveryData: Record<string, unknown> | null;
  currency: string;
  totalSalesAmount: string;
  totalDiscountAmount: string;
  extraDiscountAmount: string;
  totalItemsDiscountAmount: string;
  netAmount: string;
  totalAmount: string;
  originalDocumentId: string | null;
  state: string;
  version: number;
  etaUuid: string | null;
  etaLongId: string | null;
  etaSubmissionId: string | null;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
  lines: EtaInvoiceLine[];
}

export interface EtaInvoiceLine {
  id: string;
  itemId: string | null;
  internalCode: string | null;
  itemType: string;
  itemCode: string;
  description: string;
  unitType: string;
  quantity: string;
  unitValue: Record<string, unknown>;
  salesTotal: string;
  discountRate: string | null;
  discountAmount: string;
  itemsDiscount: string;
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

export interface EtaInvoiceListResult {
  items: EtaInvoice[];
  page: number;
  size: number;
  totalElements: number;
}

export interface ConflictBody {
  code: string;
  message: string;
  expectedVersion: number;
  actualVersion: number;
  current: EtaInvoice;
}

@Injectable({ providedIn: 'root' })
export class EtaInvoiceService {
  private http = inject(HttpClient);

  list(companyId: string, params: {
    status?: string;
    companyId?: string;
    dateFrom?: string;
    dateTo?: string;
    page?: number;
    size?: number;
  }): Observable<EtaInvoiceListResult> {
    const query: string[] = [];
    if (params.status) query.push(`status=${params.status}`);
    if (params.companyId) query.push(`companyId=${params.companyId}`);
    if (params.dateFrom) query.push(`dateFrom=${params.dateFrom}`);
    if (params.dateTo) query.push(`dateTo=${params.dateTo}`);
    query.push(`page=${params.page ?? 0}`);
    query.push(`size=${params.size ?? 50}`);
    return this.http.get<EtaInvoiceListResult>(
        `/api/companies/${companyId}/eta/invoices?${query.join('&')}`);
  }

  getById(companyId: string, id: string): Observable<HttpResponse<EtaInvoice>> {
    return this.http.get<EtaInvoice>(
        `/api/companies/${companyId}/eta/invoices/${id}`,
        { observe: 'response' });
  }

  create(companyId: string, body: unknown): Observable<HttpResponse<EtaInvoice>> {
    return this.http.post<EtaInvoice>(
        `/api/companies/${companyId}/eta/invoices`, body,
        { observe: 'response' });
  }

  update(companyId: string, id: string, body: unknown, ifMatch: string):
      Observable<HttpResponse<EtaInvoice>> {
    return this.http.put<EtaInvoice>(
        `/api/companies/${companyId}/eta/invoices/${id}`, body,
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
    return this.http.delete<void>(`/api/companies/${companyId}/eta/invoices/${id}`);
  }

  submit(companyId: string, id: string): Observable<{ state: string; etaUuid: string; etaSubmissionId: string }> {
    return this.http.post<{ state: string; etaUuid: string; etaSubmissionId: string }>(
        `/api/companies/${companyId}/eta/invoices/${id}/submit`, null);
  }

  cancel(companyId: string, id: string, reason: string): Observable<{ state: string }> {
    return this.http.post<{ state: string }>(
        `/api/companies/${companyId}/eta/invoices/${id}/cancel`, { reason });
  }

  retry(companyId: string, id: string): Observable<{ state: string; etaUuid: string; etaSubmissionId: string }> {
    return this.http.post<{ state: string; etaUuid: string; etaSubmissionId: string }>(
        `/api/companies/${companyId}/eta/invoices/${id}/retry`, null);
  }

  cloneAsDraft(companyId: string, rejectedId: string, newInvoiceNumber: string): Observable<HttpResponse<EtaInvoice>> {
    return this.http.post<EtaInvoice>(
        `/api/companies/${companyId}/eta/invoices/${rejectedId}/clone-as-draft`,
        { invoiceNumber: newInvoiceNumber }, { observe: 'response' });
  }

  checkStatus(companyId: string, documentIds: string[]): Observable<{ results: BulkStatusOutcome[] }> {
    return this.http.post<{ results: BulkStatusOutcome[] }>(
        `/api/companies/${companyId}/eta/invoices/check-status`,
        { documentIds });
  }

  getSubmissions(companyId: string, docId: string): Observable<SubmissionAttemptResponse[]> {
    return this.http.get<SubmissionAttemptResponse[]>(
        `/api/companies/${companyId}/eta/invoices/${docId}/submissions`);
  }

  getArtifactUrl(companyId: string, docId: string, type: string, attemptNumber?: number): string {
    let url = `/api/companies/${companyId}/eta/invoices/${docId}/artifacts/${type}`;
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
