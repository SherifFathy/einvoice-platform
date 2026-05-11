import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EtaConfigComponent } from './eta-config.component';
import { EtaConfigService } from '../services/eta-config.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import type { SessionContext } from '../../shared/services/auth.service';
import { of, Observable } from 'rxjs';
import type { EtaConfigResponse } from '../services/eta-config.service';

describe('EtaConfigComponent', () => {
  let fixture: ComponentFixture<EtaConfigComponent>;
  let component: EtaConfigComponent;
  let contextSubject: BehaviorSubject<SessionContext | null>;
  let mockSessionCtx: jasmine.SpyObj<SessionContextService>;
  let mockConfigService: jasmine.SpyObj<EtaConfigService>;
  let mockToast: jasmine.SpyObj<ToastNotificationService>;

  const contextWithPermissions = (edit: boolean): SessionContext => ({
    userId: 'u1',
    isSuperUser: false,
    mode: 'OPERATIONAL_MODE',
    activeCompanyId: 'c1',
    loginContext: { authority: 'ETA', environment: 'PREPROD', authorityEnvironmentId: 2 },
    companies: [{
      companyId: 'c1',
      companyNameEn: 'Test Company',
      companyNameAr: 'شركة اختبار',
      isActive: true,
      modules: {
        config: {
          visible: true,
          permissions: { view: true, create: true, edit, delete: edit, cancel: false, transfer: false, refresh: false, submit: false },
        },
      },
    }],
  });

  const mockContext = contextWithPermissions(true);

  const emptyConfigResponse = {
    id: null,
    companyId: 'c1',
    clientId: null,
    clientSecret1: null,
    clientSecret2: null,
    tokenName: null,
    tokenPass: null,
    submissionUrl: null,
    tokenUrl: null,
    posSerial: null,
    posOsVersion: null,
    posModel: null,
    isActive: true,
    createdAt: null,
    updatedAt: null,
  };

  beforeEach(() => {
    contextSubject = new BehaviorSubject<SessionContext | null>(mockContext);

    mockSessionCtx = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear'], {
      context$: contextSubject.asObservable(),
    });
    Object.defineProperty(mockSessionCtx, 'currentContext', {
      get: () => contextSubject.value,
      configurable: true,
    });

    mockConfigService = jasmine.createSpyObj('EtaConfigService', ['read', 'replace']);
    mockConfigService.read.and.returnValue(of(emptyConfigResponse));
    mockToast = jasmine.createSpyObj('ToastNotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [EtaConfigComponent, NoopAnimationsModule, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionContextService, useValue: mockSessionCtx },
        { provide: EtaConfigService, useValue: mockConfigService },
        { provide: ToastNotificationService, useValue: mockToast },
      ],
    });

    fixture = TestBed.createComponent(EtaConfigComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render empty form when GET returns all-null', () => {
    expect(component.form.get('clientId')?.value).toBe('');
    expect(component.form.get('submissionUrl')?.value).toBe('');
  });

  it('should populate form after successful round-trip', () => {
    const savedConfig = {
      ...emptyConfigResponse,
      id: 'uuid-1',
      clientId: 'abc-client',
      clientSecret1: 's1',
      clientSecret2: 's2',
      submissionUrl: 'https://sub.url',
      tokenUrl: 'https://token.url',
    };

    mockConfigService.read.and.returnValue(of(savedConfig));

    component['loadConfig']();
    fixture.detectChanges();

    expect(component.form.get('clientId')?.value).toBe('abc-client');
    expect(component.form.get('submissionUrl')?.value).toBe('https://sub.url');
  });

  it('should hide Save button when user lacks CONFIG/EDIT', () => {
    contextSubject.next(contextWithPermissions(false));
    fixture.detectChanges();

    const editButton = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(editButton).toBeFalsy();
  });

  it('should show Save button when user has CONFIG/EDIT', () => {
    contextSubject.next(contextWithPermissions(true));
    fixture.detectChanges();

    const submitButtons = fixture.nativeElement.querySelectorAll('button[type="submit"]');
    expect(submitButtons.length).toBeGreaterThan(0);
  });

  it('should require clientId field', () => {
    const control = component.form.get('clientId');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should require submissionUrl field', () => {
    const control = component.form.get('submissionUrl');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should require tokenUrl field', () => {
    const control = component.form.get('tokenUrl');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should require clientSecret1 field', () => {
    const control = component.form.get('clientSecret1');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should require clientSecret2 field', () => {
    const control = component.form.get('clientSecret2');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should require tokenName in Production environment', () => {
    component.isProductionEnv = true;
    component['updateTokenFieldValidators']();
    const control = component.form.get('tokenName');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should require tokenPass in Production environment', () => {
    component.isProductionEnv = true;
    component['updateTokenFieldValidators']();
    const control = component.form.get('tokenPass');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should not require tokenName in non-Production environment', () => {
    component.isProductionEnv = false;
    component['updateTokenFieldValidators']();
    const control = component.form.get('tokenName');
    control?.setValue('');
    expect(control?.hasError('required')).toBeFalse();
  });

  it('should display all labels in English', () => {
    const labels = fixture.nativeElement.querySelectorAll('mat-label');
    const texts = Array.from<HTMLElement>(labels).map((l) => l.textContent?.trim());
    expect(texts).toContain('Client ID *');
    expect(texts).toContain('Client Secret 1 *');
    expect(texts).toContain('Client Secret 2 *');
    expect(texts.some(t => t?.includes('Token Name'))).toBeTrue();
    expect(texts.some(t => t?.includes('Token Password'))).toBeTrue();
    expect(texts).toContain('Token URL *');
    expect(texts).toContain('Submission URL *');
    expect(texts).toContain('POS Serial');
    expect(texts).toContain('POS OS Version');
    expect(texts).toContain('POS Model');
  });

  it('should display validation messages in English', () => {
    const control = component.form.get('clientId');
    control?.setValue('');
    control?.markAsTouched();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('mat-error');
    expect(error.textContent).toContain('Client ID is required');
  });

  it('should not contain branchId field in template', () => {
    const html = fixture.nativeElement.innerHTML;
    expect(html.toLowerCase()).not.toContain('branchid');
    expect(html.toLowerCase()).not.toContain('branch id');
  });

  it('should include error code in toast on save failure', () => {
    contextSubject.next(contextWithPermissions(true));
    fixture.detectChanges();

    mockConfigService.replace.and.returnValue(
      of({ error: { code: 'BRANCH_ID_NOT_ALLOWED', message: 'not allowed' } }) as unknown as Observable<EtaConfigResponse>,
    );

    component.form.patchValue({
      clientId: 'x', clientSecret1: 'y', clientSecret2: 'z',
      tokenUrl: 'u', submissionUrl: 'v',
    });

    const errorResp = { error: { code: 'BRANCH_ID_NOT_ALLOWED', message: 'branchId is not allowed' } };
    mockConfigService.replace = jasmine.createSpy().and.returnValue({
      subscribe: (obs: { error: (e: unknown) => void }) => obs.error(errorResp),
    });

    component.onSubmit();
    expect(mockToast.error).toHaveBeenCalled();
    const toastArg = mockToast.error.calls.argsFor(0)[0];
    expect(toastArg).toContain('BRANCH_ID_NOT_ALLOWED');
  });
});
