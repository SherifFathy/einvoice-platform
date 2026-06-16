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
import { Subject, switchMap, takeUntil, of } from 'rxjs';
import { AuthService, EnvironmentEntry } from '../shared/services/auth.service';
import { SessionContextService } from '../shared/services/session-context.service';
import { PlatformBrandingService } from '../shared/services/platform-branding.service';

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
  loadingEnvironments = false;
  logoFailed = false;

  readonly authorityOptions = [
    { value: 'ETA', label: 'ETA' },
    { value: 'ZATCA', label: 'ZATCA' },
  ];

  environmentOptions: EnvironmentEntry[] = [];

  private destroy$ = new Subject<void>();

  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private sessionCtx = inject(SessionContextService);
  private branding = inject(PlatformBrandingService);
  private router = inject(Router);

  constructor() {
    this.form = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]],
      authority: [null as string | null, [Validators.required]],
      environment: [null as string | null, [Validators.required]],
    });

    this.form.get('authority')!.valueChanges.pipe(
      takeUntil(this.destroy$),
      switchMap((authority: string | null) => {
        this.form.get('environment')!.setValue(null);
        this.environmentOptions = [];
        if (!authority) {
          return of(null);
        }
        this.loadingEnvironments = true;
        return this.authService.listEnvironments(authority);
      }),
    ).subscribe({
      next: (res) => {
        if (res) {
          this.environmentOptions = res.environments;
        }
        this.loadingEnvironments = false;
      },
      error: () => {
        this.environmentOptions = [];
        this.loadingEnvironments = false;
      },
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get loginDisabled(): boolean {
    return this.form.invalid || this.submitting;
  }

  get logoUrl(): string {
    return this.branding.logoUrl();
  }

  onSubmit(): void {
    if (this.loginDisabled) return;
    this.submitting = true;
    this.errorMessage = '';

    const { email, password, authority, environment } = this.form.value;
    this.authService.login(email, password, authority, environment).subscribe({
      next: () => {
        this.sessionCtx.loadContext().subscribe({
          next: () => this.router.navigate(['/dashboard']),
          error: () => {
            this.errorMessage = 'Session initialization failed. Please try again.';
            this.submitting = false;
            this.authService.clearAuth();
          },
        });
      },
      error: (err) => {
        const code = err.error?.code;
        if (code === 'BAD_CREDENTIALS') {
          this.errorMessage = 'Invalid email or password';
        } else if (code === 'INVALID_AUTHORITY_ENVIRONMENT') {
          this.errorMessage = 'Invalid authority and environment combination';
        } else if (code === 'VALIDATION_ERROR') {
          this.errorMessage = 'Please check your input and try again';
        } else {
          this.errorMessage = err.error?.message || 'An error occurred during login';
        }
        this.submitting = false;
      },
    });
  }
}
