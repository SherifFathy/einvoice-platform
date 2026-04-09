import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { HealthService, HelloResponse } from '../shared/services/health.service';

@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, MatCardModule],
  templateUrl: './dashboard.component.html',
  styles: ``,
})
export class DashboardComponent implements OnInit {
  healthData: HelloResponse | null = null;
  error = '';

  private healthService = inject(HealthService);

  ngOnInit(): void {
    this.healthService.getHello().subscribe({
      next: (data) => (this.healthData = data),
      error: (err) => (this.error = 'Backend not reachable: ' + err.message),
    });
  }
}
