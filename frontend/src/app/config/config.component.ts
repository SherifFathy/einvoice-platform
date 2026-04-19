import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CompanyConfigService } from '../shared/services/company-config.service';
import { ToastNotificationService } from '../shared/services/toast.service';
import { EtaPollingService, PollingStatusResponse } from '../shared/services/eta-polling.service';

@Component({
  selector: 'app-config',
  imports: [
    CommonModule, RouterModule,
    MatCardModule, MatButtonModule, MatIconModule, MatTableModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './config.component.html',
  styles: [`
    .config-section { margin-bottom: 24px; }
    .config-nav { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 24px; }
    table { width: 100%; }
    .polling-status { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
    .status-indicator { display: flex; align-items: center; gap: 6px; font-weight: 500; }
    .status-dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
    .status-dot.active { background-color: #4caf50; }
    .status-dot.stopped { background-color: #f44336; }
    .polling-meta { color: #666; font-size: 0.9em; }
    .polling-actions { display: flex; gap: 8px; }
  `],
})
export class ConfigComponent implements OnInit {
  private configService = inject(CompanyConfigService);
  private toast = inject(ToastNotificationService);
  private etaPollingService = inject(EtaPollingService);

  branches: { id: number; nameEn: string; branchCode: string }[] = [];
  displayedColumns = ['nameEn', 'branchCode', 'actions'];
  loading = false;

  pollingStatus: PollingStatusResponse | null = null;
  pollingLoading = false;
  pollingToggling = false;

  ngOnInit(): void {
    this.loadBranches();
    this.loadPollingStatus();
  }

  loadBranches(): void {
    this.loading = true;
    const companyIdStr = localStorage.getItem('companyId');
    if (!companyIdStr) {
      this.loading = false;
      return;
    }
    this.configService.listBranches(Number(companyIdStr)).subscribe({
      next: (branches) => { this.branches = branches; this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  loadPollingStatus(): void {
    this.pollingLoading = true;
    this.etaPollingService.getStatus().subscribe({
      next: (status) => { this.pollingStatus = status; this.pollingLoading = false; },
      error: () => { this.pollingLoading = false; },
    });
  }

  stopPolling(): void {
    this.pollingToggling = true;
    this.etaPollingService.stop().subscribe({
      next: (status) => {
        this.pollingStatus = status;
        this.pollingToggling = false;
        this.toast.success('ETA polling stopped');
      },
      error: () => {
        this.toast.error('Failed to stop polling');
        this.pollingToggling = false;
      },
    });
  }

  resumePolling(): void {
    this.pollingToggling = true;
    this.etaPollingService.resume().subscribe({
      next: (status) => {
        this.pollingStatus = status;
        this.pollingToggling = false;
        this.toast.success('ETA polling resumed');
      },
      error: () => {
        this.toast.error('Failed to resume polling');
        this.pollingToggling = false;
      },
    });
  }
}
