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
      search?: string): Observable<PageResponse<InvoiceListResponse>> {
    let url = `${this.apiUrl}?page=${page}&size=${size}`;
    if (status) url += `&status=${status}`;
    if (type) url += `&type=${type}`;
    if (dateFrom) url += `&dateFrom=${dateFrom}`;
    if (dateTo) url += `&dateTo=${dateTo}`;
    if (search) url += `&search=${encodeURIComponent(search)}`;
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
}
