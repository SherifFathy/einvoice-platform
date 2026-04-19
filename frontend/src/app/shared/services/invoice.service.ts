import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from '../components/data-table/data-table.component';

export interface InvoiceListResponse {
  id: string;
  invoiceNumber: string;
  type: string;
  status: string;
  issueDate: string;
  buyerName: string | null;
  totalWithVat: number;
  amountDue: number;
  authority: string;
  createdAt: string;
}

export interface InvoiceDetailResponse {
  id: string;
  invoiceNumber: string;
  type: string;
  subtypeFlags: Record<string, boolean> | null;
  status: string;
  issueDate: string;
  supplyDate: string | null;
  supplyEndDate: string | null;
  currency: string;
  buyerId: number | null;
  buyerName: string | null;
  buyerData: Record<string, any> | null;
  sellerData: Record<string, any> | null;
  paymentMeansCode: string | null;
  paymentTerms: string | null;
  prepaidAmount: number;
  totalLineNet: number;
  totalAllowances: number;
  totalWithoutVat: number;
  totalVat: number;
  totalWithVat: number;
  amountDue: number;
  authority: string;
  environment: string;
  branchId: number | null;
  originalInvoiceId: string | null;
  externalInvoiceReference: string | null;
  notes: string | null;
  createdAt: string;
  lines: InvoiceLineResponse[];
  vatBreakdown: VatBreakdownResponse[];
}

export interface InvoiceLineResponse {
  id: number | null;
  itemId: number | null;
  descriptionEn: string;
  descriptionAr: string | null;
  quantity: number;
  unit: string;
  unitPrice: number;
  discountAmount: number;
  vatCategory: string;
  vatRate: number;
  lineNetAmount: number;
  lineVatAmount: number;
  lineTotal: number;
  sortOrder: number;
}

export interface VatBreakdownResponse {
  vatCategoryCode: string;
  vatRate: number;
  taxableAmount: number;
  taxAmount: number;
}

export interface CreateInvoiceRequest {
  type: string;
  subtypeFlags?: Record<string, boolean>;
  issueDate: string;
  supplyDate?: string | null;
  supplyEndDate?: string | null;
  currency?: string;
  buyerId?: number | null;
  branchId: number;
  authority: string;
  paymentMeansCode?: string | null;
  paymentTerms?: string | null;
  prepaidAmount?: number;
  totalAllowances?: number;
  originalInvoiceId?: string | null;
  externalInvoiceReference?: string | null;
  notes?: string | null;
  lines: InvoiceLineRequest[];
}

export interface InvoiceLineRequest {
  itemId?: number | null;
  descriptionEn: string;
  descriptionAr?: string | null;
  quantity: number;
  unit: string;
  unitPrice: number;
  discountAmount?: number;
  vatCategory: string;
  vatRate: number;
  sortOrder: number;
}

@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private http = inject(HttpClient);
  private readonly apiUrl = '/api/invoices';

  list(page: number, size: number, status?: string, type?: string,
      dateFrom?: string, dateTo?: string,
      search?: string, authority?: string,
      buyer?: string): Observable<PageResponse<InvoiceListResponse>> {
    let url = `${this.apiUrl}?page=${page}&size=${size}`;
    if (status) url += `&status=${status}`;
    if (type) url += `&type=${type}`;
    if (authority) url += `&authority=${authority}`;
    if (dateFrom) url += `&dateFrom=${dateFrom}`;
    if (dateTo) url += `&dateTo=${dateTo}`;
    if (search) url += `&search=${encodeURIComponent(search)}`;
    if (buyer) url += `&buyer=${encodeURIComponent(buyer)}`;
    return this.http.get<PageResponse<InvoiceListResponse>>(url);
  }

  get(id: string): Observable<InvoiceDetailResponse> {
    return this.http.get<InvoiceDetailResponse>(`${this.apiUrl}/${id}`);
  }

  create(request: CreateInvoiceRequest): Observable<InvoiceDetailResponse> {
    return this.http.post<InvoiceDetailResponse>(this.apiUrl, request);
  }

  update(id: string, request: CreateInvoiceRequest): Observable<InvoiceDetailResponse> {
    return this.http.put<InvoiceDetailResponse>(`${this.apiUrl}/${id}`, request);
  }

  cancel(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  cancelEta(id: string, reason?: string): Observable<{ invoiceId: string; status: string }> {
    return this.http.post<{ invoiceId: string; status: string }>(`${this.apiUrl}/${id}/cancel-eta`,
      { reason: reason || '' });
  }

  validate(id: string): Observable<ValidationResultResponse> {
    return this.http.post<ValidationResultResponse>(`${this.apiUrl}/${id}/validate`, null);
  }

  submit(id: string): Observable<SubmitResponse> {
    return this.http.post<SubmitResponse>(`${this.apiUrl}/${id}/submit`, null);
  }

  retry(id: string): Observable<SubmitResponse> {
    return this.http.post<SubmitResponse>(`${this.apiUrl}/${id}/retry`, null);
  }

  confirmSubmission(id: string): Observable<{ invoiceId: string; status: string }> {
    return this.http.post<{ invoiceId: string; status: string }>(`${this.apiUrl}/${id}/confirm-submission`, null);
  }

  checkStatus(id: string): Observable<{ invoiceId: string; status: string; previousStatus: string; checkedAt: string }> {
    return this.http.post<{ invoiceId: string; status: string; previousStatus: string; checkedAt: string }>(`${this.apiUrl}/${id}/check-status`, null);
  }

  getSubmissions(id: string): Observable<{ invoiceId: string; currentStatus: string; attempts: SubmissionAttemptResponse[] }> {
    return this.http.get<{ invoiceId: string; currentStatus: string; attempts: SubmissionAttemptResponse[] }>(`${this.apiUrl}/${id}/submissions`);
  }

  getArtifact(id: string, type: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/artifacts/${type}`, { responseType: 'blob' });
  }

  getArtifacts(id: string): Observable<ArtifactListResponse> {
    return this.http.get<ArtifactListResponse>(`${this.apiUrl}/${id}/artifacts`);
  }

  returnToDraft(id: string): Observable<{ invoiceId: string; status: string }> {
    return this.http.post<{ invoiceId: string; status: string }>(`${this.apiUrl}/${id}/return-to-draft`, null);
  }

  getEtaPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/eta-pdf`, { responseType: 'blob' });
  }
}

export interface ValidationResultResponse {
  valid: boolean;
  errors: ValidationItem[];
  warnings: ValidationItem[];
}

export interface ValidationItem {
  layer: string;
  authority: string | null;
  ruleId: string | null;
  field: string;
  message: string;
  severity: string;
}

export interface SubmitResponse {
  invoiceId: string;
  status: string;
  attemptNumber: number;
  authority: string;
  warnings: string[];
  errors: { code: string; message: string }[];
  submittedAt: string | null;
  completedAt: string | null;
}

export interface SubmissionAttemptResponse {
  attemptNumber: number;
  authority: string;
  environment: string;
  result: string;
  statusCode: number | null;
  errorSummary: string | null;
  submittedAt: string | null;
  completedAt: string | null;
}

export interface ArtifactListResponse {
  artifacts: { type: string; createdAt: string }[];
}
