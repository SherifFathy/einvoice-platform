import { Component, inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subscription } from 'rxjs';
import { AuthService } from '../shared/services/auth.service';

interface LovOption {
  value: string;
  label: string;
}

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './auth.component.html',
  styleUrls: ['./auth.component.scss'],
})
export class AuthComponent implements OnDestroy {
  form: FormGroup;
  submitting = false;
  errorMessage = '';

  readonly authorityOptions: LovOption[] = [
    { value: 'ZATCA', label: 'Zatca' },
    { value: 'ETA', label: 'ETA' },
  ];

  private readonly docTypeMap: Record<string, LovOption[]> = {
    ZATCA: [{ value: 'INVOICE', label: 'Invoice' }],
    ETA: [
      { value: 'INVOICE', label: 'Invoice' },
      { value: 'RECEIPT', label: 'Receipt' },
    ],
  };

  private readonly subEnvMap: Record<string, LovOption[]> = {
    ZATCA: [
      { value: 'SANDBOX', label: 'Sandbox' },
      { value: 'SIMULATION', label: 'Simulation' },
      { value: 'PRODUCTION', label: 'Production' },
    ],
    ETA: [
      { value: 'PREPROD', label: 'Preprod' },
      { value: 'PRODUCTION', label: 'Production' },
    ],
  };

  docTypeOptions: LovOption[] = [];
  subEnvOptions: LovOption[] = [];

  private authoritySub: Subscription;

  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);

  constructor() {
    this.form = this.fb.group({
      authority: [null as string | null, [Validators.required]],
      docType: [null as string | null, [Validators.required]],
      subEnvironment: [null as string | null, [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
    });

    this.authoritySub = this.form.get('authority')!.valueChanges.subscribe((authority: string | null) => {
      if (authority) {
        this.docTypeOptions = this.docTypeMap[authority] ?? [];
        this.subEnvOptions = this.subEnvMap[authority] ?? [];
      } else {
        this.docTypeOptions = [];
        this.subEnvOptions = [];
      }
      this.form.get('docType')!.setValue(null);
      this.form.get('subEnvironment')!.setValue(null);
    });
  }

  ngOnDestroy(): void {
    this.authoritySub.unsubscribe();
  }

  get authorityValue(): string | null {
    return this.form.get('authority')?.value ?? null;
  }

  get docTypeValue(): string | null {
    return this.form.get('docType')?.value ?? null;
  }

  get subEnvValue(): string | null {
    return this.form.get('subEnvironment')?.value ?? null;
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    this.errorMessage = '';

    const { email, password, authority, docType, subEnvironment } = this.form.value;
    this.authService
      .login(email, password, authority, docType, subEnvironment)
      .subscribe({
        next: () => this.router.navigate(['/dashboard']),
        error: (err) => {
          this.errorMessage = err.error?.error || 'Invalid credentials';
          this.submitting = false;
        },
      });
  }
}
