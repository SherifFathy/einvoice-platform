import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { InvoiceListComponent } from '../invoice-list/invoice-list.component';

@Component({
  selector: 'app-invoice-list-page',
  standalone: true,
  imports: [InvoiceListComponent],
  template: `
    <app-invoice-list
      (createInvoice)="router.navigate(['/invoices', 'new'])"
      (editInvoice)="onEdit($event)"
      (viewInvoice)="router.navigate(['/invoices', $event])">
    </app-invoice-list>
  `,
})
export class InvoiceListPageComponent {
  protected router = inject(Router);

  onEdit(id: string): void {
    this.router.navigate(['/invoices', id, 'edit']);
  }
}
