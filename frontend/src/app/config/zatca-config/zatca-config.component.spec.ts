import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ZatcaConfigComponent } from './zatca-config.component';
import { ZatcaConfigService } from '../services/zatca-config.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import type { SessionContext } from '../../shared/services/auth.service';
import { of, throwError } from 'rxjs';

describe('ZatcaConfigComponent', () => {
  let fixture: ComponentFixture<ZatcaConfigComponent>;
  let component: ZatcaConfigComponent;
  let contextSubject: BehaviorSubject<SessionContext | null>;
  let mockSessionCtx: jasmine.SpyObj<SessionContextService>;
  let mockConfigService: jasmine.SpyObj<ZatcaConfigService>;
  let mockToast: jasmine.SpyObj<ToastNotificationService>;

  const contextWithPermissions = (edit: boolean): SessionContext => ({
    userId: 'u1',
    isSuperUser: false,
    mode: 'OPERATIONAL_MODE',
    activeCompanyId: 'c1',
    loginContext: { authority: 'ZATCA', environment: 'SANDBOX', authorityEnvironmentId: 5 },
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
    privateKey: null,
    deviceUuid: null,
    csr: null,
    complianceCertificate: null,
    complianceApiSecret: null,
    productionCertificate: null,
    productionApiSecret: null,
    certificateExpiryDate: null,
    chainStateInitialized: false,
    isActive: null as boolean | null,
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

    mockConfigService = jasmine.createSpyObj('ZatcaConfigService', ['read', 'replace']);
    mockConfigService.read.and.returnValue(of(emptyConfigResponse));
    mockToast = jasmine.createSpyObj('ToastNotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [ZatcaConfigComponent, NoopAnimationsModule, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionContextService, useValue: mockSessionCtx },
        { provide: ZatcaConfigService, useValue: mockConfigService },
        { provide: ToastNotificationService, useValue: mockToast },
      ],
    });

    fixture = TestBed.createComponent(ZatcaConfigComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render empty form when GET returns all-null', () => {
    expect(component.form.get('privateKey')?.value).toBe('');
    expect(component.form.get('deviceUuid')?.value).toBe('');
  });

  it('should populate form after successful round-trip', () => {
    const savedConfig = {
      ...emptyConfigResponse,
      id: 'uuid-1',
      privateKey: 'test-private-key',
      deviceUuid: 'device-001',
      csr: 'test-csr',
      complianceCertificate: 'comp-cert',
      complianceApiSecret: 'comp-secret',
      productionCertificate: 'prod-cert',
      productionApiSecret: 'prod-secret',
      certificateExpiryDate: '2026-12-31',
      chainStateInitialized: true,
    };

    mockConfigService.read.and.returnValue(of(savedConfig));

    component['loadConfig']();
    fixture.detectChanges();

    expect(component.form.get('privateKey')?.value).toBe('test-private-key');
    expect(component.form.get('deviceUuid')?.value).toBe('device-001');
    expect(component.form.get('certificateExpiryDate')?.value).toBe('2026-12-31');
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

  it('should require privateKey field', () => {
    const control = component.form.get('privateKey');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should require deviceUuid field', () => {
    const control = component.form.get('deviceUuid');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should require csr field', () => {
    const control = component.form.get('csr');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should require complianceCertificate field', () => {
    const control = component.form.get('complianceCertificate');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should require complianceApiSecret field', () => {
    const control = component.form.get('complianceApiSecret');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should not require productionCertificate field', () => {
    const control = component.form.get('productionCertificate');
    control?.setValue('');
    expect(control?.hasError('required')).toBeFalse();
  });

  it('should not require productionApiSecret field', () => {
    const control = component.form.get('productionApiSecret');
    control?.setValue('');
    expect(control?.hasError('required')).toBeFalse();
  });

  it('should display all labels in English', () => {
    const labels = fixture.nativeElement.querySelectorAll('mat-label');
    const texts = Array.from<HTMLElement>(labels).map((l) => l.textContent?.trim());
    expect(texts).toContain('Private Key *');
    expect(texts).toContain('Device UUID *');
    expect(texts).toContain('CSR *');
    expect(texts).toContain('Compliance Certificate *');
    expect(texts).toContain('Compliance API Secret *');
    expect(texts).toContain('Production Certificate');
    expect(texts).toContain('Production API Secret');
    expect(texts).toContain('Certificate Expiry Date');
  });

  it('should display validation messages in English', () => {
    const control = component.form.get('privateKey');
    control?.setValue('');
    control?.markAsTouched();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('mat-error');
    expect(error.textContent).toContain('Private Key is required');
  });

  it('should not contain branchId field in template', () => {
    const html = fixture.nativeElement.innerHTML;
    expect(html.toLowerCase()).not.toContain('branchid');
    expect(html.toLowerCase()).not.toContain('branch id');
  });

  it('should show chain state initialized indicator after first save', () => {
    const savedConfig = {
      ...emptyConfigResponse,
      id: 'uuid-1',
      privateKey: 'key',
      deviceUuid: 'dev',
      csr: 'csr',
      complianceCertificate: 'cert',
      complianceApiSecret: 'secret',
      chainStateInitialized: true,
    };

    mockConfigService.read.and.returnValue(of(savedConfig));
    component['loadConfig']();
    fixture.detectChanges();

    expect(component.chainStateInitialized).toBeTrue();
    expect(component.saved).toBeTrue();
  });

  it('should include error code in toast on save failure', () => {
    contextSubject.next(contextWithPermissions(true));
    fixture.detectChanges();

    mockConfigService.replace.and.returnValue(throwError(() => ({ error: { code: 'BRANCH_ID_NOT_ALLOWED', message: 'not allowed' } })));

    component.form.patchValue({
      privateKey: 'x', deviceUuid: 'y', csr: 'z',
      complianceCertificate: 'a', complianceApiSecret: 'b',
    });

    component.onSubmit();
    expect(mockToast.error).toHaveBeenCalled();
  });
});
