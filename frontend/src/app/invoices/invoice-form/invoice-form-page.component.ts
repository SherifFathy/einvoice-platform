import { Component, inject, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { InvoiceFormComponent } from './invoice-form.component';
import { InvoiceService, InvoiceDetailResponse } from '../../shared/services/invoice.service';
import { CanComponentDeactivate } from '../../shared/guards/unsaved-changes.guard';

@Component({
  selector: 'app-invoice-form-page',
  standalone: true,
  imports: [InvoiceFormComponent],
  template: `
    <app-invoice-form
      [invoice]="invoice"
      [companyId]="companyId"
      (saved)="onSaved()"
      (cancelled)="router.navigate(['/invoices'])">
    </app-invoice-form>
  `,
})
export class InvoiceFormPageComponent implements CanComponentDeactivate {
  @ViewChild(InvoiceFormComponent) formComponent!: InvoiceFormComponent;

  protected router = inject(Router);
  private route = inject(ActivatedRoute);
  private invoiceService = inject(InvoiceService);

  invoice: InvoiceDetailResponse | null = null;
  companyId: number | null = null;
  private saved = false;

  constructor() {
    this.companyId = null;
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.invoiceService.get(id).subscribe({
        next: (inv) => this.invoice = inv,
        error: () => this.router.navigate(['/invoices']),
      });
    }
  }

  onSaved(): void {
    this.saved = true;
    this.router.navigate(['/invoices']);
  }

  canDeactivate(): boolean {
    if (this.saved) return true;
    if (!this.formComponent?.form?.dirty) return true;
    return confirm('You have unsaved changes. Are you sure you want to leave?');
  }
}
