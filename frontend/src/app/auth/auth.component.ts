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
import { Subject, debounceTime, distinctUntilChanged, switchMap, takeUntil, of } from 'rxjs';
import { AuthService, EnvironmentEntry, CompanyEntry } from '../shared/services/auth.service';
import { SessionContextService } from '../shared/services/session-context.service';

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
  loadingCompanies = false;

  readonly authorityOptions = [
    { value: 'ETA', label: 'ETA' },
    { value: 'ZATCA', label: 'ZATCA' },
  ];

  environmentOptions: EnvironmentEntry[] = [];
  companyOptions: CompanyEntry[] = [];
  isSuperUser = false;

  private destroy$ = new Subject<void>();
  private companyLoad$ = new Subject<{ authority: string; environment: string; email: string }>();

  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private sessionCtx = inject(SessionContextService);
  private router = inject(Router);

  constructor() {
    this.form = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]],
      authority: [null as string | null, [Validators.required]],
      environment: [null as string | null, [Validators.required]],
      companyId: [null as string | null],
    });

    this.companyLoad$.pipe(
      takeUntil(this.destroy$),
      switchMap((args) => {
        this.loadingCompanies = true;
        return this.authService.listCompanies(args.authority, args.environment, args.email);
      }),
    ).subscribe({
      next: (res) => {
        this.companyOptions = res.companies;
        this.isSuperUser = res.isSuperUser;
        this.loadingCompanies = false;
      },
      error: () => {
        this.companyOptions = [];
        this.isSuperUser = false;
        this.loadingCompanies = false;
      },
    });

    this.form.get('authority')!.valueChanges.pipe(
      takeUntil(this.destroy$),
      switchMap((authority: string | null) => {
        this.form.get('environment')!.setValue(null);
        this.form.get('companyId')!.setValue(null);
        this.environmentOptions = [];
        this.companyOptions = [];
        this.isSuperUser = false;
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

    this.form.get('environment')!.valueChanges.pipe(
      takeUntil(this.destroy$),
    ).subscribe(() => {
      this.form.get('companyId')!.setValue(null);
      this.companyOptions = [];
      this.isSuperUser = false;
      this.tryLoadCompanies();
    });

    this.form.get('email')!.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$),
    ).subscribe(() => this.tryLoadCompanies());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get loginDisabled(): boolean {
    return this.form.invalid || this.submitting
      || (!this.form.get('companyId')!.value && !this.isSuperUser);
  }

  onSubmit(): void {
    if (this.loginDisabled) return;
    this.submitting = true;
    this.errorMessage = '';

    const { email, password, authority, environment, companyId } = this.form.value;
    this.authService.login(email, password, authority, environment, companyId).subscribe({
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
        } else if (code === 'COMPANY_CONTEXT_REQUIRED') {
          this.errorMessage = 'A company selection is required for non-administrator users';
        } else if (code === 'UNAUTHORIZED_CONTEXT') {
          this.errorMessage = 'You have no active assignments for the selected authority and environment';
        } else if (code === 'INACTIVE_COMPANY') {
          this.errorMessage = 'Selected company is not active';
        } else if (code === 'VALIDATION_ERROR') {
          this.errorMessage = 'Please check your input and try again';
        } else {
          this.errorMessage = err.error?.message || 'An error occurred during login';
        }
        this.submitting = false;
      },
    });
  }

  private tryLoadCompanies(): void {
    const authority = this.form.get('authority')!.value;
    const environment = this.form.get('environment')!.value;
    const email = this.form.get('email')!.value;
    if (authority && environment && email && this.form.get('email')!.valid) {
      this.form.get('companyId')!.setValue(null);
      this.companyLoad$.next({ authority, environment, email });
    }
  }
}
