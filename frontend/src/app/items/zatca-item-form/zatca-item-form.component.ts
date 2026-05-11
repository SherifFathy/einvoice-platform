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
import { ZatcaItemService } from '../services/zatca-item.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-zatca-item-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatSelectModule, MatIconModule,
    MatSlideToggleModule, HasPermissionDirective,
  ],
  templateUrl: './zatca-item-form.component.html',
  styleUrls: ['./zatca-item-form.component.scss'],
})
export class ZatcaItemFormComponent implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private itemService = inject(ZatcaItemService);
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

  vatCategories = [
    { value: 'S', label: 'S — Standard Rated' },
    { value: 'Z', label: 'Z — Zero Rated' },
    { value: 'E', label: 'E — Exempt' },
    { value: 'O', label: 'O — Out of Scope' },
  ];

  constructor() {
    this.form = this.fb.group({
      internalCode: ['', Validators.required],
      itemCode: [''],
      nameAr: [''],
      nameEn: ['', Validators.required],
      unitType: [''],
      unitPrice: ['', [Validators.min(0)]],
      vatCategory: ['', Validators.required],
      vatRate: [{ value: '', disabled: true }],
      isActive: [true],
    });

    this.form.get('vatCategory')!.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe((cat) => {
        const vatRate = this.form.get('vatRate')!;
        if (cat === 'S') {
          vatRate.enable();
          vatRate.setValidators([Validators.required, Validators.min(0)]);
        } else {
          vatRate.setValue(0);
          vatRate.disable();
          vatRate.clearValidators();
        }
        vatRate.updateValueAndValidity({ emitEvent: false });
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
          itemCode: item.itemCode || '',
          nameAr: item.nameAr || '',
          nameEn: item.nameEn,
          unitType: item.unitType || '',
          unitPrice: item.unitPrice ?? '',
          vatCategory: item.vatCategory,
          vatRate: item.vatRate ?? '',
          isActive: item.isActive,
        });
      },
      error: (err) => this.toast.error(err.error?.error || 'Failed to load item'),
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    const raw = this.form.getRawValue();
    const payload = { ...raw };
    if (payload.unitPrice === '' || payload.unitPrice === null) delete payload.unitPrice;
    if (payload.vatRate === '' || payload.vatRate === null) delete payload.vatRate;

    const obs = this.isEdit && this.itemId
      ? this.itemService.update(this.selectedCompanyId, this.itemId, payload)
      : this.itemService.create(this.selectedCompanyId, payload);

    obs.subscribe({
      next: () => {
        this.toast.success(this.isEdit ? 'Item updated' : 'Item created');
        this.submitting = false;
        this.router.navigate(['/items/zatca']);
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
