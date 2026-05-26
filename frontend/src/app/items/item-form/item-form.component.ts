import { Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { ItemService, ItemResponse } from '../../shared/services/item.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-item-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatSelectModule, MatIconModule,
  ],
  templateUrl: './item-form.component.html',
  styles: `
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .full-width { grid-column: 1 / -1; }
    .form-actions { display: flex; gap: 12px; margin-top: 16px; justify-content: flex-end; }
  `,
})
export class ItemFormComponent implements OnChanges {
  private fb = inject(FormBuilder);
  private itemService = inject(ItemService);
  private toast = inject(ToastNotificationService);

  @Input() item: ItemResponse | null = null;
  @Output() saved = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  form: FormGroup;
  submitting = false;
  isEdit = false;

  constructor() {
    this.form = this.fb.group({
      code: ['', Validators.required],
      nameEn: ['', Validators.required],
      nameAr: [''],
      unitOfMeasure: ['', Validators.required],
      unitPrice: [null, [Validators.required, Validators.min(0.01)]],
      vatCategory: ['S', Validators.required],
      vatRate: [15.00, [Validators.required, Validators.min(0)]],
      description: [''],
      authorityScope: ['BOTH'],
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['item'] && this.item) {
      this.isEdit = true;
      this.form.patchValue({
        code: this.item.code,
        nameEn: this.item.nameEn,
        nameAr: this.item.nameAr || '',
        unitOfMeasure: this.item.unitOfMeasure,
        unitPrice: this.item.unitPrice,
        vatCategory: this.item.vatCategory,
        vatRate: this.item.vatRate,
        description: this.item.description || '',
        authorityScope: this.item.authorityScope,
      });
    } else if (changes['item'] && !this.item) {
      this.isEdit = false;
      this.form.reset({ vatCategory: 'S', vatRate: 15.00, authorityScope: 'BOTH' });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    const request = this.form.value;
    const obs = this.isEdit && this.item
        ? this.itemService.update(this.item.id, request)
        : this.itemService.create(request);
    obs.subscribe({
      next: () => {
        this.toast.success(this.isEdit ? 'Item updated' : 'Item created');
        this.saved.emit();
        this.form.reset({ vatCategory: 'S', vatRate: 15.00, authorityScope: 'BOTH' });
        this.isEdit = false;
        this.submitting = false;
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to save item');
        this.submitting = false;
      },
    });
  }

  onCancel(): void {
    this.cancelled.emit();
    this.form.reset({ vatCategory: 'S', vatRate: 15.00, authorityScope: 'BOTH' });
    this.isEdit = false;
  }
}
