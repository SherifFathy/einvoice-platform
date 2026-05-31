import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CustomerListComponent } from './customer-list/customer-list.component';
import { CustomerFormComponent } from './customer-form/customer-form.component';
import { CustomerImportComponent } from './customer-import/customer-import.component';
import { CustomerResponse } from '../shared/services/customer.service';

@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [
    CommonModule, CustomerListComponent, CustomerFormComponent, CustomerImportComponent,
  ],
  templateUrl: './customers.component.html',
  styles: `
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .page-header h2 { margin: 0; }
  `,
})
export class CustomersComponent {
  view: 'list' | 'form' | 'import' = 'list';
  editCustomer: CustomerResponse | null = null;

  showForm(customer: CustomerResponse | null): void {
    this.editCustomer = customer;
    this.view = 'form';
  }

  showImport(): void {
    this.view = 'import';
  }

  showList(): void {
    this.view = 'list';
    this.editCustomer = null;
  }
}
