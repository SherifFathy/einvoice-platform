import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Subject, of, throwError } from 'rxjs';
import { signal, WritableSignal } from '@angular/core';
import { AuthService, EnvironmentsResponse, CompaniesResponse } from '../shared/services/auth.service';
import { SessionContextService } from '../shared/services/session-context.service';
import { AuthComponent } from './auth.component';

describe('AuthComponent', () => {
  let component: AuthComponent;
  let fixture: ComponentFixture<AuthComponent>;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    const authSpy = jasmine.createSpyObj('AuthService', [
      'listEnvironments', 'listCompanies', 'login', 'logout', 'getToken', 'clearAuth'
    ]);
    (authSpy as Record<string, unknown>)['isLoggedIn'] = signal(false) as WritableSignal<boolean>;
    const ctxSpy = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear']);

    authSpy.listEnvironments.and.returnValue(of({
      environments: [
        { id: 1, authority: 'ETA', environment: 'PREPROD', label: 'ETA Pre-Production', isActive: true },
        { id: 2, authority: 'ETA', environment: 'PRODUCTION', label: 'ETA Production', isActive: true },
      ]
    }));
    authSpy.listCompanies.and.returnValue(of({
      isSuperUser: false,
      companies: [
        { companyId: 'c1', nameEn: 'Company A', nameAr: 'شركة أ', taxNumber: 'T1', isActive: true },
      ]
    }));

    await TestBed.configureTestingModule({
      imports: [AuthComponent, ReactiveFormsModule],
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: SessionContextService, useValue: ctxSpy },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    fixture = TestBed.createComponent(AuthComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should disable login button when form is incomplete', () => {
    expect(component.loginDisabled).toBeTrue();
  });

  it('should load environments when authority is selected', () => {
    component.form.get('authority')!.setValue('ETA');
    expect(authService.listEnvironments).toHaveBeenCalledWith('ETA');
  });

  it('should load companies when environment and email are set', fakeAsync(() => {
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');
    component.form.get('email')!.setValue('test@test.com');
    tick(300);
    expect(authService.listCompanies).toHaveBeenCalledWith('ETA', 'PREPROD', 'test@test.com');
  }));

  it('should not call listCompanies when email is invalid', fakeAsync(() => {
    authService.listCompanies.calls.reset();
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');
    component.form.get('email')!.setValue('not-an-email');
    tick(300);
    expect(authService.listCompanies).not.toHaveBeenCalled();
  }));

  it('should display BAD_CREDENTIALS error as user-readable message', () => {
    component.form.get('email')!.setValue('test@test.com');
    component.form.get('password')!.setValue('pass');
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');
    component.form.get('companyId')!.setValue('c1');

    authService.login.and.returnValue(throwError(() => ({
      error: { code: 'BAD_CREDENTIALS', message: 'Invalid email or password' }
    })));

    component.onSubmit();
    expect(component.errorMessage).toBe('Invalid email or password');
  });

  it('should display UNAUTHORIZED_CONTEXT error', () => {
    component.form.get('email')!.setValue('test@test.com');
    component.form.get('password')!.setValue('pass');
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');
    component.form.get('companyId')!.setValue('c1');

    authService.login.and.returnValue(throwError(() => ({
      error: { code: 'UNAUTHORIZED_CONTEXT', message: 'No assignments' }
    })));

    component.onSubmit();
    expect(component.errorMessage).toContain('no active assignments');
  });

  it('should display COMPANY_CONTEXT_REQUIRED error', () => {
    component.form.get('email')!.setValue('test@test.com');
    component.form.get('password')!.setValue('pass');
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');
    component.form.get('companyId')!.setValue('c1');

    authService.login.and.returnValue(throwError(() => ({
      error: { code: 'COMPANY_CONTEXT_REQUIRED', message: 'Company required' }
    })));

    component.onSubmit();
    expect(component.errorMessage).toContain('company selection is required');
  });

  it('should show only ETA environments when ETA authority is selected (US3 acceptance #1)', () => {
    component.form.get('authority')!.setValue('ETA');
    expect(authService.listEnvironments).toHaveBeenCalledWith('ETA');
    expect(component.environmentOptions.every(e => e.authority === 'ETA')).toBeTrue();
  });

  it('should show only ZATCA environments when ZATCA authority is selected (US3 acceptance #1)', () => {
    authService.listEnvironments.and.returnValue(of({
      environments: [
        { id: 3, authority: 'ZATCA', environment: 'SANDBOX', label: 'ZATCA Sandbox', isActive: true },
        { id: 4, authority: 'ZATCA', environment: 'SIMULATION', label: 'ZATCA Simulation', isActive: true },
      ]
    }));
    component.form.get('authority')!.setValue('ZATCA');
    expect(authService.listEnvironments).toHaveBeenCalledWith('ZATCA');
    expect(component.environmentOptions.every(e => e.authority === 'ZATCA')).toBeTrue();
  });

  it('should disable login for regular user without company selection (US3 acceptance #2, SC-007)', fakeAsync(() => {
    component.form.get('email')!.setValue('test@test.com');
    component.form.get('password')!.setValue('pass');
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');
    tick(300);

    expect(component.isSuperUser).toBeFalse();
    expect(component.loginDisabled).toBeTrue();
  }));

  it('should expose isSuperUser=true and allow submit with null companyId for Super User (US3 acceptance #3)', fakeAsync(() => {
    authService.listCompanies.and.returnValue(of({
      isSuperUser: true,
      companies: [
        { companyId: 'c1', nameEn: 'Company A', nameAr: 'شركة أ', taxNumber: 'T1', isActive: true },
      ]
    }));

    component.form.get('email')!.setValue('super@test.com');
    component.form.get('password')!.setValue('pass');
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');
    tick(300);

    expect(component.isSuperUser).toBeTrue();
    component.form.get('companyId')!.setValue(null);
    expect(component.loginDisabled).toBeFalse();
  }));

  it('should keep isSuperUser=false for regular users so admin-mode option is gated off (US3 acceptance #2)', fakeAsync(() => {
    component.form.get('email')!.setValue('test@test.com');
    component.form.get('password')!.setValue('pass');
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');
    tick(300);

    expect(component.isSuperUser).toBeFalse();
    component.form.get('companyId')!.setValue(null);
    expect(component.loginDisabled).toBeTrue();
  }));

  it('should set loadingEnvironments flag during in-flight call', () => {
    const envSubject = new Subject<EnvironmentsResponse>();
    authService.listEnvironments.and.returnValue(envSubject.asObservable());

    expect(component.loadingEnvironments).toBeFalse();

    component.form.get('authority')!.setValue('ETA');
    expect(component.loadingEnvironments).toBeTrue();

    envSubject.next({
      environments: [
        { id: 1, authority: 'ETA', environment: 'PREPROD', label: 'ETA Pre-Production', isActive: true },
      ]
    });
    expect(component.loadingEnvironments).toBeFalse();
  });

  it('should set loadingCompanies flag during in-flight call', fakeAsync(() => {
    const companiesSubject = new Subject<CompaniesResponse>();
    authService.listCompanies.and.returnValue(companiesSubject.asObservable());

    component.form.get('email')!.setValue('test@test.com');
    component.form.get('password')!.setValue('pass');
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');

    expect(component.loadingCompanies).toBeTrue();

    companiesSubject.next({
      isSuperUser: false,
      companies: [
        { companyId: 'c1', nameEn: 'Company A', nameAr: 'شركة أ', taxNumber: 'T1', isActive: true },
      ]
    });
    expect(component.loadingCompanies).toBeFalse();
  }));

  it('should show the same generic error for invalid credentials regardless of which field was wrong (US3 acceptance #5, INV-10)', () => {
    component.form.get('email')!.setValue('wrong@test.com');
    component.form.get('password')!.setValue('pass');
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');
    component.form.get('companyId')!.setValue('c1');

    authService.login.and.returnValue(throwError(() => ({
      error: { code: 'BAD_CREDENTIALS', message: 'Invalid email or password' }
    })));

    component.onSubmit();
    const firstError = component.errorMessage;

    component.submitting = false;
    component.form.get('email')!.setValue('test@test.com');
    component.form.get('password')!.setValue('wrongpass');
    component.errorMessage = '';

    authService.login.and.returnValue(throwError(() => ({
      error: { code: 'BAD_CREDENTIALS', message: 'Invalid email or password' }
    })));

    component.onSubmit();
    expect(component.errorMessage).toBe(firstError);
    expect(component.errorMessage).toBe('Invalid email or password');
  });

  it('should cancel stale environment request when authority changes rapidly', () => {
    const etaSubject = new Subject<EnvironmentsResponse>();
    const zatcaSubject = new Subject<EnvironmentsResponse>();
    authService.listEnvironments.and.callFake((auth: string) => {
      return auth === 'ETA' ? etaSubject.asObservable() : zatcaSubject.asObservable();
    });

    component.form.get('authority')!.setValue('ETA');
    expect(component.loadingEnvironments).toBeTrue();

    component.form.get('authority')!.setValue('ZATCA');

    etaSubject.next({
      environments: [{ id: 1, authority: 'ETA', environment: 'PREPROD', label: 'ETA PP', isActive: true }]
    });

    zatcaSubject.next({
      environments: [{ id: 3, authority: 'ZATCA', environment: 'SANDBOX', label: 'ZATCA SB', isActive: true }]
    });

    expect(component.environmentOptions.length).toBe(1);
    expect(component.environmentOptions[0].authority).toBe('ZATCA');
  });
});
