import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Subject, of, throwError } from 'rxjs';
import { signal, WritableSignal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { AuthService, EnvironmentsResponse, SessionContext } from '../shared/services/auth.service';
import { SessionContextService } from '../shared/services/session-context.service';
import { AuthComponent } from './auth.component';

describe('AuthComponent', () => {
  let component: AuthComponent;
  let fixture: ComponentFixture<AuthComponent>;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    const authSpy = jasmine.createSpyObj('AuthService', [
      'listEnvironments', 'login', 'logout', 'getToken', 'clearAuth'
    ]);
    (authSpy as Record<string, unknown>)['isLoggedIn'] = signal(false) as WritableSignal<boolean>;
    const ctxSpy = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear']);
    ctxSpy.loadContext.and.returnValue(of({} as SessionContext));

    authSpy.listEnvironments.and.returnValue(of({
      environments: [
        { id: 1, authority: 'ETA', environment: 'PREPROD', label: 'ETA Pre-Production', isActive: true },
        { id: 2, authority: 'ETA', environment: 'PRODUCTION', label: 'ETA Production', isActive: true },
      ]
    }));

    await TestBed.configureTestingModule({
      imports: [AuthComponent, ReactiveFormsModule],
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: SessionContextService, useValue: ctxSpy },
        provideRouter([]),
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

  it('should enable login when email password authority and environment are set', () => {
    component.form.get('email')!.setValue('test@test.com');
    component.form.get('password')!.setValue('pass');
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');
    expect(component.loginDisabled).toBeFalse();
  });

  it('should display BAD_CREDENTIALS error as user-readable message', () => {
    component.form.get('email')!.setValue('test@test.com');
    component.form.get('password')!.setValue('pass');
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');

    authService.login.and.returnValue(throwError(() => ({
      error: { code: 'BAD_CREDENTIALS', message: 'Invalid email or password' }
    })));

    component.onSubmit();
    expect(component.errorMessage).toBe('Invalid email or password');
  });

  it('should show only ETA environments when ETA authority is selected', () => {
    component.form.get('authority')!.setValue('ETA');
    expect(authService.listEnvironments).toHaveBeenCalledWith('ETA');
    expect(component.environmentOptions.every(e => e.authority === 'ETA')).toBeTrue();
  });

  it('should show only ZATCA environments when ZATCA authority is selected', () => {
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

  it('should allow login without company selection for any user', () => {
    component.form.get('email')!.setValue('test@test.com');
    component.form.get('password')!.setValue('pass');
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');
    expect(component.loginDisabled).toBeFalse();
  });

  it('should submit login with email password authority and environment only', () => {
    component.form.get('email')!.setValue('test@test.com');
    component.form.get('password')!.setValue('pass');
    component.form.get('authority')!.setValue('ETA');
    component.form.get('environment')!.setValue('PREPROD');

    authService.login.and.returnValue(of({
      accessToken: 'token123',
      tokenType: 'Bearer',
      expiresInSeconds: 28800,
      mode: 'AUTHORITY_SCOPED',
    }));

    component.onSubmit();
    expect(authService.login).toHaveBeenCalledWith(
      'test@test.com', 'pass', 'ETA', 'PREPROD'
    );
  });

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
