import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ItemListComponent } from './item-list/item-list.component';
import { ItemFormComponent } from './item-form/item-form.component';
import { ItemImportComponent } from './item-import/item-import.component';
import { ItemResponse } from '../shared/services/item.service';

@Component({
  selector: 'app-items',
  standalone: true,
  imports: [
    CommonModule, ItemListComponent, ItemFormComponent, ItemImportComponent,
  ],
  templateUrl: './items.component.html',
  styles: `
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .page-header h2 { margin: 0; }
  `,
})
export class ItemsComponent {
  view: 'list' | 'form' | 'import' = 'list';
  editItem: ItemResponse | null = null;

  showForm(item: ItemResponse | null): void {
    this.editItem = item;
    this.view = 'form';
  }

  showImport(): void {
    this.view = 'import';
  }

  showList(): void {
    this.view = 'list';
    this.editItem = null;
  }
}
