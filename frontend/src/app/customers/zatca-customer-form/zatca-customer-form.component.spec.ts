import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { ZatcaCustomerFormComponent } from './zatca-customer-form.component';
import { ZatcaCustomerService } from '../services/zatca-customer.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import type { SessionContext } from '../../shared/services/auth.service';

describe('ZatcaCustomerFormComponent', () => {
  let fixture: ComponentFixture<ZatcaCustomerFormComponent>;
  let component: ZatcaCustomerFormComponent;
  let contextSubject: BehaviorSubject<SessionContext | null>;
  let mockSessionCtx: jasmine.SpyObj<SessionContextService>;
  let mockZatcaService: jasmine.SpyObj<ZatcaCustomerService>;
  let mockToast: jasmine.SpyObj<ToastNotificationService>;

  const mockContext: SessionContext = {
    userId: 'u1',
    isSuperUser: false,
    mode: 'OPERATIONAL_MODE',
    activeCompanyId: 'c1',
    loginContext: { authority: 'ZATCA', environment: 'PREPROD', authorityEnvironmentId: 3 },
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

    mockZatcaService = jasmine.createSpyObj('ZatcaCustomerService', ['get', 'create', 'update']);
    mockToast = jasmine.createSpyObj('ToastNotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [ZatcaCustomerFormComponent, NoopAnimationsModule, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionContextService, useValue: mockSessionCtx },
        { provide: ZatcaCustomerService, useValue: mockZatcaService },
        { provide: ToastNotificationService, useValue: mockToast },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
      ],
    });

    fixture = TestBed.createComponent(ZatcaCustomerFormComponent);
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

  it('should accept only B and P as customerType', () => {
    const control = component.form.get('customerType');
    const validTypes = ['B', 'P'];
    validTypes.forEach((t) => {
      control?.setValue(t);
      expect(control?.valid).toBeTrue();
    });
  });

  it('should offer only B and P customer type options', () => {
    expect(component.customerTypes.length).toBe(2);
    const values = component.customerTypes.map(ct => ct.value);
    expect(values).toEqual(['B', 'P']);
  });

  it('should require all ZATCA address fields', () => {
    const addressGroup = component.form.get('addressData');
    const requiredFields = ['streetName', 'buildingNumber', 'city', 'postalCode', 'districtName', 'country'];
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
      addressData: { streetName: '', buildingNumber: '', city: '', postalCode: '', districtName: '', country: '' },
    });
    expect(component.form.invalid).toBeTrue();
  });

  it('should be valid with all required fields filled', () => {
    component.form.patchValue({
      nameEn: 'Test Customer',
      customerType: 'B',
      vatNumber: '300000000000003',
      addressData: { streetName: 'King Fahd Rd', buildingNumber: '1234', city: 'Riyadh', postalCode: '12211', districtName: 'Al Olaya', country: 'SA' },
    });
    expect(component.form.valid).toBeTrue();
  });

  it('should validate VAT number format only when customerType is B', () => {
    component.form.get('customerType')?.setValue('B');
    const control = component.form.get('vatNumber');
    control?.setValue('123');
    expect(control?.hasError('vatFormat')).toBeTrue();

    control?.setValue('300000000000003');
    expect(control?.hasError('vatFormat')).toBeFalse();
  });

  it('should require VAT number when customerType is B', () => {
    component.form.get('customerType')?.setValue('B');
    const control = component.form.get('vatNumber');
    control?.setValue('');
    expect(control?.hasError('required')).toBeTrue();
  });

  it('should not validate VAT format when customerType is P', () => {
    component.form.get('customerType')?.setValue('P');
    const control = component.form.get('vatNumber');
    control?.setValue('123');
    expect(control?.hasError('vatFormat')).toBeFalsy();
    expect(control?.hasError('required')).toBeFalsy();
  });

  it('should accept empty VAT number when customerType is P', () => {
    component.form.get('customerType')?.setValue('P');
    const control = component.form.get('vatNumber');
    control?.setValue('');
    expect(control?.hasError('vatFormat')).toBeFalsy();
    expect(control?.hasError('required')).toBeFalsy();
  });

  it('should display all labels in English', () => {
    const labels = fixture.nativeElement.querySelectorAll('mat-label');
    const texts = Array.from(labels).map(l => (l as HTMLElement).textContent?.trim());
    expect(texts).toContain('Name (English) *');
    expect(texts).toContain('Name (Arabic)');
    expect(texts).toContain('VAT Number');
    expect(texts).toContain('Customer Type *');
    expect(texts).toContain('Street Name *');
    expect(texts).toContain('Building Number *');
    expect(texts).toContain('City *');
    expect(texts).toContain('Postal Code *');
    expect(texts).toContain('District Name *');
    expect(texts).toContain('Country *');
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

  it('should display VAT validation message in English', () => {
    component.form.get('customerType')?.setValue('B');
    const control = component.form.get('vatNumber');
    control?.setValue('123');
    control?.markAsTouched();
    fixture.detectChanges();

    const errors = fixture.nativeElement.querySelectorAll('mat-error');
    const texts = Array.from(errors).map(e => (e as HTMLElement).textContent?.trim());
    expect(texts).toContain('VAT number must be 15 digits starting and ending with 3');
  });

  it('should display address validation messages in English', () => {
    const control = component.form.get('addressData.city');
    control?.setValue('');
    control?.markAsTouched();
    fixture.detectChanges();

    const errors = fixture.nativeElement.querySelectorAll('mat-error');
    const texts = Array.from(errors).map(e => (e as HTMLElement).textContent?.trim());
    expect(texts).toContain('City is required');
  });

  it('should have nameAr input with dir="rtl"', () => {
    const inputs = fixture.nativeElement.querySelectorAll('input[dir="rtl"]');
    expect(inputs.length).toBeGreaterThan(0);
    const arabicInput = Array.from(inputs as NodeListOf<HTMLInputElement>).find(i => i.getAttribute('formcontrolname') === 'nameAr');
    expect(arabicInput).toBeTruthy();
    expect(arabicInput?.getAttribute('dir')).toBe('rtl');
  });

  it('should show "Edit ZATCA Customer" title in edit mode', () => {
    component.isEdit = true;
    fixture.detectChanges();
    const title = fixture.nativeElement.querySelector('mat-card-title');
    expect(title.textContent).toContain('Edit ZATCA Customer');
  });

  it('should show "New ZATCA Customer" title in create mode', () => {
    const title = fixture.nativeElement.querySelector('mat-card-title');
    expect(title.textContent).toContain('New ZATCA Customer');
  });

  it('should disable submit when form is invalid', () => {
    component.form.patchValue({ nameEn: '', customerType: '' });
    const submitBtn = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(submitBtn.disabled).toBeTrue();
  });
});
