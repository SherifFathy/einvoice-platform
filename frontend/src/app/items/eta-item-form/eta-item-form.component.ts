import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Subject, takeUntil } from 'rxjs';
import { EtaItemService } from '../services/eta-item.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-eta-item-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatSelectModule, MatIconModule,
    MatSlideToggleModule, HasPermissionDirective,
  ],
  templateUrl: './eta-item-form.component.html',
  styleUrls: ['./eta-item-form.component.scss'],
})
export class EtaItemFormComponent implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private itemService = inject(EtaItemService);
  private sessionCtx = inject(SessionContextService);
  private toast = inject(ToastNotificationService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroy$ = new Subject<void>();

  form: FormGroup;
  submitting = false;
  isEdit = false;
  itemId: string | null = null;
  selectedCompanyId = '';

  itemTypes = [
    { value: 'GS1', label: 'GS1' },
    { value: 'EGS', label: 'EGS' },
  ];

  constructor() {
    this.form = this.fb.group({
      internalCode: ['', Validators.required],
      itemType: ['', Validators.required],
      itemCode: ['', Validators.required],
      nameAr: [''],
      nameEn: ['', Validators.required],
      unitType: [''],
      unitPrice: ['', [Validators.min(0)]],
      taxType: [''],
      taxSubtype: [''],
      taxRate: ['', [Validators.min(0)]],
      isActive: [true],
    });
  }

  ngOnInit(): void {
    this.sessionCtx.context$.pipe(takeUntil(this.destroy$)).subscribe((ctx) => {
      if (ctx && ctx.companies.length > 0) {
        this.selectedCompanyId = ctx.companies[0].companyId;
      }
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.itemId = id;
      this.loadItem();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadItem(): void {
    if (!this.itemId || !this.selectedCompanyId) return;
    this.itemService.get(this.selectedCompanyId, this.itemId).subscribe({
      next: (item) => {
        this.form.patchValue({
          internalCode: item.internalCode,
          itemType: item.itemType,
          itemCode: item.itemCode,
          nameAr: item.nameAr || '',
          nameEn: item.nameEn,
          unitType: item.unitType || '',
          unitPrice: item.unitPrice ?? '',
          taxType: item.taxType || '',
          taxSubtype: item.taxSubtype || '',
          taxRate: item.taxRate ?? '',
          isActive: item.isActive,
        });
      },
      error: (err) => this.toast.error(err.error?.error || 'Failed to load item'),
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    const payload = { ...this.form.getRawValue() };
    if (payload.unitPrice === '' || payload.unitPrice === null) delete payload.unitPrice;
    if (payload.taxRate === '' || payload.taxRate === null) delete payload.taxRate;

    const obs = this.isEdit && this.itemId
      ? this.itemService.update(this.selectedCompanyId, this.itemId, payload)
      : this.itemService.create(this.selectedCompanyId, payload);

    obs.subscribe({
      next: () => {
        this.toast.success(this.isEdit ? 'Item updated' : 'Item created');
        this.submitting = false;
        this.router.navigate(['/items/eta']);
      },
      error: (err) => {
        this.toast.error(err.error?.error || 'Failed to save item');
        this.submitting = false;
      },
    });
  }

  onCancel(): void {
    history.back();
  }
}
