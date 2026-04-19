import { Component, EventEmitter, inject, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { Subscription } from 'rxjs';
import { CompanyConfigService } from '../../../shared/services/company-config.service';

export const ZATCA_INVOICE_TYPES = [
  { value: 'TAX_INVOICE', label: 'Tax Invoice' },
  { value: 'SIMPLIFIED_TAX_INVOICE', label: 'Simplified Tax Invoice' },
  { value: 'CREDIT_NOTE', label: 'Credit Note' },
  { value: 'DEBIT_NOTE', label: 'Debit Note' },
];

export const ETA_INVOICE_TYPES = [
  { value: 'INVOICE', label: 'Invoice' },
  { value: 'CREDIT_NOTE', label: 'Credit Note' },
  { value: 'DEBIT_NOTE', label: 'Debit Note' },
];

export const ZATCA_SUBTYPE_FLAGS = [
  { control: 'thirdParty', label: 'Third Party' },
  { control: 'nominal', label: 'Nominal' },
  { control: 'export', label: 'Export' },
  { control: 'summary', label: 'Summary' },
  { control: 'selfBilled', label: 'Self-Billed' },
];

@Component({
  selector: 'app-header-step',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatCheckboxModule, MatDatepickerModule, MatNativeDateModule,
  ],
  templateUrl: './header-step.component.html',
  styles: `
    .form-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 16px; }
    .full-width { grid-column: 1 / -1; }
    .subtype-flags { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 8px; }
  `,
})
export class HeaderStepComponent implements OnChanges, OnInit, OnDestroy {
  @Input() form!: FormGroup;
  @Input() companyId: number | null = null;
  @Output() authorityChanged = new EventEmitter<string>();

  private companyConfig = inject(CompanyConfigService);
  private branchSub: Subscription | null = null;

  branches: any[] = [];
  authorityConfigs: any[] = [];
  enabledDocumentTypes: string[] = [];

  get authority(): string {
    return this.form?.get('authority')?.value || 'ZATCA';
  }

  get invoiceType(): string {
    return this.form?.get('type')?.value || '';
  }

  get isZatca(): boolean {
    return this.authority === 'ZATCA';
  }

  get isEta(): boolean {
    return this.authority === 'ETA';
  }

  get subtypeFlagsList() {
    return ZATCA_SUBTYPE_FLAGS;
  }

  get isCreditOrDebitNote(): boolean {
    return this.invoiceType === 'CREDIT_NOTE' || this.invoiceType === 'DEBIT_NOTE';
  }

  get invoiceTypes(): { value: string; label: string }[] {
    if (this.isEta && this.enabledDocumentTypes.length > 0) {
      return ETA_INVOICE_TYPES.filter(t => this.enabledDocumentTypes.includes(t.value));
    }
    return this.isEta ? ETA_INVOICE_TYPES : ZATCA_INVOICE_TYPES;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['companyId'] && this.companyId) {
      this.loadBranches();
    }
    if (changes['form'] && this.form) {
      this.setupBranchWatcher();
    }
  }

  ngOnInit(): void {
    if (this.form) {
      this.setupBranchWatcher();
    }
  }

  ngOnDestroy(): void {
    this.branchSub?.unsubscribe();
  }

  private setupBranchWatcher(): void {
    if (this.branchSub) return;
    const branchControl = this.form.get('branchId');
    if (!branchControl) return;
    this.branchSub = branchControl.valueChanges.subscribe((branchId: number | null) => {
      if (branchId) {
        this.onBranchChange(branchId);
      }
    });
  }

  onAuthorityChange(authority: string): void {
    const currentCurrency = this.form.get('currency')?.value;
    const defaultCurrency = authority === 'ZATCA' ? 'SAR' : 'EGP';
    const patch: Record<string, any> = {
      type: authority === 'ZATCA' ? 'TAX_INVOICE' : 'INVOICE',
    };
    if (!currentCurrency || currentCurrency === 'SAR' || currentCurrency === 'EGP') {
      patch['currency'] = defaultCurrency;
    }
    this.form.patchValue(patch);
    if (authority === 'ETA') {
      this.loadEtaDocumentTypes();
    }
    this.authorityChanged.emit(authority);
  }

  onBranchChange(branchId: number): void {
    if (this.companyId && branchId) {
      this.companyConfig.listAuthorityConfigs(this.companyId, branchId).subscribe({
        next: (configs) => {
          this.authorityConfigs = configs;
          if (this.isEta) {
            this.loadEtaDocumentTypes();
            const currentType = this.form.get('type')?.value;
            if (currentType && this.enabledDocumentTypes.length > 0 &&
                !this.enabledDocumentTypes.includes(currentType)) {
              this.form.patchValue({ type: this.invoiceTypes[0]?.value || 'INVOICE' });
            }
          }
        },
      });
    }
  }

  getFieldError(controlName: string): string | null {
    const control = this.form?.get(controlName);
    if (!control || !control.errors || !control.touched) return null;
    if (control.errors['required']) return 'This field is required';
    return null;
  }

  private loadBranches(): void {
    if (!this.companyId) return;
    this.companyConfig.listBranches(this.companyId).subscribe({
      next: (branches) => this.branches = branches,
    });
  }

  private loadEtaDocumentTypes(): void {
    const etaConfig = this.authorityConfigs.find((c: any) => c.authority === 'ETA');
    if (etaConfig?.enabledDocumentTypes) {
      this.enabledDocumentTypes = etaConfig.enabledDocumentTypes;
    } else {
      this.enabledDocumentTypes = [];
    }
  }
}
