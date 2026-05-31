import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { EtaCustomerFormComponent } from './eta-customer-form.component';
import { EtaCustomerService } from '../services/eta-customer.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import type { SessionContext } from '../../shared/services/auth.service';

describe('EtaCustomerFormComponent', () => {
  let fixture: ComponentFixture<EtaCustomerFormComponent>;
  let component: EtaCustomerFormComponent;
  let contextSubject: BehaviorSubject<SessionContext | null>;
  let mockSessionCtx: jasmine.SpyObj<SessionContextService>;
  let mockEtaService: jasmine.SpyObj<EtaCustomerService>;
  let mockToast: jasmine.SpyObj<ToastNotificationService>;

  const mockContext: SessionContext = {
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
        customers: {
          visible: true,
          permissions: { view: true, create: true, edit: true, delete: true, cancel: false, transfer: false, refresh: false, submit: false },
        },
      },
    }],
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

    mockEtaService = jasmine.createSpyObj('EtaCustomerService', ['get', 'create', 'update']);
    mockToast = jasmine.createSpyObj('ToastNotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [EtaCustomerFormComponent, NoopAnimationsModule, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionContextService, useValue: mockSessionCtx },
        { provide: EtaCustomerService, useValue: mockEtaService },
        { provide: ToastNotificationService, useValue: mockToast },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
      ],
    });

    fixture = TestBed.createComponent(EtaCustomerFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should require nameEn', () => {
    const control = component.form.get('nameEn');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should require customerType', () => {
    const control = component.form.get('customerType');
    control?.setValue('');
    control?.markAsTouched();
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should accept only B, P, F as customerType', () => {
    const control = component.form.get('customerType');
    const validTypes = ['B', 'P', 'F'];
    validTypes.forEach((t) => {
      control?.setValue(t);
      expect(control?.valid).toBeTrue();
    });
  });

  it('should require all ETA address fields', () => {
    const addressGroup = component.form.get('addressData');
    const requiredFields = ['country', 'governorate', 'regionCity', 'street', 'buildingNumber'];
    requiredFields.forEach((field) => {
      const control = addressGroup?.get(field);
      control?.setValue('');
      control?.markAsTouched();
      expect(control?.hasError('required')).toBeTrue();
    });
  });

  it('should be invalid when address fields are empty', () => {
    component.form.patchValue({
      nameEn: 'Test',
      customerType: 'B',
      addressData: { country: '', governorate: '', regionCity: '', street: '', buildingNumber: '' },
    });
    expect(component.form.invalid).toBeTrue();
  });

  it('should be valid with all required fields filled', () => {
    component.form.patchValue({
      nameEn: 'Test Customer',
      customerType: 'B',
      addressData: { country: 'EG', governorate: 'Cairo', regionCity: 'Nasr City', street: 'Abbas', buildingNumber: '12' },
    });
    expect(component.form.valid).toBeTrue();
  });

  it('should display all labels in English', () => {
    const labels = fixture.nativeElement.querySelectorAll('mat-label');
    const texts = Array.from(labels).map(l => (l as HTMLElement).textContent?.trim());
    expect(texts).toContain('Name (English) *');
    expect(texts).toContain('Name (Arabic)');
    expect(texts).toContain('Tax Number');
    expect(texts).toContain('Customer Type *');
    expect(texts).toContain('Country *');
    expect(texts).toContain('Governorate *');
    expect(texts).toContain('Region / City *');
    expect(texts).toContain('Street *');
    expect(texts).toContain('Building Number *');
  });

  it('should display buttons in English', () => {
    const buttons = fixture.nativeElement.querySelectorAll('button');
    const texts = Array.from(buttons).map(b => (b as HTMLElement).textContent?.trim());
    expect(texts).toContain('Cancel');
    expect(texts.some(t => t?.includes('Create Customer'))).toBeTrue();
  });

  it('should display validation messages in English', () => {
    const control = component.form.get('nameEn');
    control?.setValue('');
    control?.markAsTouched();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('mat-error');
    expect(error.textContent).toContain('Name (English) is required');
  });

  it('should display address validation messages in English', () => {
    const control = component.form.get('addressData.country');
    control?.setValue('');
    control?.markAsTouched();
    fixture.detectChanges();

    const errors = fixture.nativeElement.querySelectorAll('mat-error');
    const texts = Array.from(errors).map(e => (e as HTMLElement).textContent?.trim());
    expect(texts).toContain('Country is required');
  });

  it('should have nameAr input with dir="rtl"', () => {
    const inputs = fixture.nativeElement.querySelectorAll('input[dir="rtl"]');
    expect(inputs.length).toBeGreaterThan(0);
    const arabicInput = Array.from(inputs as NodeListOf<HTMLInputElement>).find(i => i.getAttribute('formcontrolname') === 'nameAr');
    expect(arabicInput).toBeTruthy();
    expect(arabicInput?.getAttribute('dir')).toBe('rtl');
  });

  it('should show "Edit ETA Customer" title in edit mode', () => {
    component.isEdit = true;
    fixture.detectChanges();
    const title = fixture.nativeElement.querySelector('mat-card-title');
    expect(title.textContent).toContain('Edit ETA Customer');
  });

  it('should show "New ETA Customer" title in create mode', () => {
    const title = fixture.nativeElement.querySelector('mat-card-title');
    expect(title.textContent).toContain('New ETA Customer');
  });

  it('should disable submit when form is invalid', () => {
    component.form.patchValue({ nameEn: '', customerType: '' });
    const submitBtn = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(submitBtn.disabled).toBeTrue();
  });
});
