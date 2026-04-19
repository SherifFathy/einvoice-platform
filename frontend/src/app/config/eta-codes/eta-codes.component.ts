import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { EtaCodeService, CodeResponse, PublishedCodeResponse } from '../../shared/services/eta-code.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-eta-codes',
  imports: [
    CommonModule, ReactiveFormsModule,
    MatCardModule, MatButtonModule, MatIconModule, MatTableModule,
    MatTabsModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatProgressSpinnerModule, MatSnackBarModule,
  ],
  templateUrl: './eta-codes.component.html',
  styles: [`
    .codes-container { max-width: 900px; margin: 0 auto; padding: 16px; }
    .status-badge { padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
    .status-PENDING { background: #fff3e0; color: #e65100; }
    .status-APPROVED { background: #e8f5e9; color: #2e7d32; }
    .status-REJECTED { background: #ffebee; color: #c62828; }
    .filter-row { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
    .register-form { display: flex; gap: 12px; margin-bottom: 16px; align-items: flex-start; flex-wrap: wrap; }
    .register-form .full-width { flex: 1 1 100%; }
    table { width: 100%; }
    .actions-cell { display: flex; gap: 4px; }
    .error-hint { color: #d32f2f; font-size: 12px; margin-top: 4px; }
  `],
})
export class EtaCodesComponent implements OnInit {
  private etaCodeService = inject(EtaCodeService);
  private toast = inject(ToastNotificationService);

  codes: CodeResponse[] = [];
  publishedCodes: PublishedCodeResponse[] = [];
  displayedColumns = ['itemCode', 'codeType', 'description', 'status', 'etaCodeId', 'createdAt', 'actions'];
  publishedDisplayedColumns = ['itemCode', 'codeType', 'description', 'publishedBy'];
  loading = false;
  loadingPublished = false;
  searchError: string | null = null;

  statusFilter = new FormControl('');
  searchFilter = new FormControl('');
  publishedSearch = new FormControl('');

  registerForm = new FormGroup({
    itemCode: new FormControl('', [Validators.required]),
    codeType: new FormControl('', [Validators.required]),
    description: new FormControl(''),
  });

  editForm = new FormGroup({
    id: new FormControl<number | null>(null),
    itemCode: new FormControl('', [Validators.required]),
    codeType: new FormControl('', [Validators.required]),
    description: new FormControl(''),
  });

  editing = false;

  ngOnInit(): void {
    this.loadCodes();
  }

  loadCodes(): void {
    this.loading = true;
    this.etaCodeService.list({
      status: this.statusFilter.value || undefined,
      search: this.searchFilter.value || undefined,
    }).subscribe({
      next: (res) => { this.codes = res.content; this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  applyFilter(): void {
    this.loadCodes();
  }

  registerCode(): void {
    if (this.registerForm.invalid) return;
    const val = this.registerForm.value;
    this.etaCodeService.register({
      itemCode: val.itemCode!,
      codeType: val.codeType!,
      description: val.description || undefined,
    }).subscribe({
      next: () => {
        this.toast.show('Item code registered successfully');
        this.registerForm.reset();
        this.loadCodes();
      },
      error: (err) => {
        this.toast.show('Registration failed: ' + (err.error?.error ?? err.message));
      },
    });
  }

  startEdit(code: CodeResponse): void {
    this.editing = true;
    this.editForm.patchValue({
      id: code.id,
      itemCode: code.itemCode,
      codeType: code.codeType,
      description: code.description ?? '',
    });
  }

  cancelEdit(): void {
    this.editing = false;
    this.editForm.reset();
  }

  saveEdit(): void {
    if (this.editForm.invalid) return;
    const val = this.editForm.value;
    this.etaCodeService.update(val.id!, {
      itemCode: val.itemCode!,
      codeType: val.codeType!,
      description: val.description || undefined,
    }).subscribe({
      next: () => {
        this.toast.show('Item code updated');
        this.cancelEdit();
        this.loadCodes();
      },
      error: (err) => {
        this.toast.show('Update failed: ' + (err.error?.error ?? err.message));
      },
    });
  }

  searchPublished(): void {
    const query = this.publishedSearch.value;
    if (!query) return;
    this.loadingPublished = true;
    this.searchError = null;
    this.etaCodeService.searchPublished(query).subscribe({
      next: (res) => { this.publishedCodes = res.content; this.loadingPublished = false; },
      error: (err) => {
        this.loadingPublished = false;
        this.searchError = err.error?.error ?? 'Search failed. Please try again.';
        this.publishedCodes = [];
      },
    });
  }

  getStatusClass(status: string): string {
    return `status-${status}`;
  }
}
