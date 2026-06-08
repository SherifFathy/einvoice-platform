import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CertificateStatus {
  daysRemaining: number;
  expiringSoon: boolean;
  expired: boolean;
}

export interface CompanyCard {
  companyId: string;
  nameEn: string;
  nameAr: string | null;
  taxNumber: string | null;
  active: boolean;
  pendingCount: number;
  failedCount: number;
  certificate: CertificateStatus | null;
}

export interface StatusBreakdown {
  total: number;
  byStatus: Record<string, number>;
}

export interface DashboardKpi {
  today: StatusBreakdown;
  thisMonth: StatusBreakdown;
}

export interface DashboardSummary {
  cards: CompanyCard[];
  kpi: DashboardKpi;
}

export interface RecentActivityEntry {
  attemptId: string;
  companyId: string;
  companyName: string;
  transactionType: string;
  documentId: string;
  outcome: string;
  submittedAt: string;
}

export interface RecentActivity {
  entries: RecentActivityEntry[];
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private http = inject(HttpClient);

  summary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>('/api/dashboard/summary');
  }

  recentActivity(): Observable<RecentActivity> {
    return this.http.get<RecentActivity>('/api/dashboard/recent-activity');
  }
}
