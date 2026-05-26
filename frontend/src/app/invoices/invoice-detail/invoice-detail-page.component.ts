import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { InvoiceDetailComponent } from '../invoice-detail/invoice-detail.component';

@Component({
  selector: 'app-invoice-detail-page',
  standalone: true,
  imports: [InvoiceDetailComponent],
  template: `
    <app-invoice-detail
      [invoiceId]="invoiceId"
      (back)="router.navigate(['/invoices'])"
      (edit)="router.navigate(['/invoices', $event, 'edit'])">
    </app-invoice-detail>
  `,
})
export class InvoiceDetailPageComponent {
  protected router = inject(Router);
  private route = inject(ActivatedRoute);
  invoiceId: string | null = null;

  constructor() {
    this.invoiceId = this.route.snapshot.paramMap.get('id');
  }
}
