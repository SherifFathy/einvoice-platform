import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { ZatcaItemFormComponent } from './zatca-item-form.component';
import { ZatcaItemService } from '../services/zatca-item.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import type { SessionContext } from '../../shared/services/auth.service';

describe('ZatcaItemFormComponent', () => {
  let fixture: ComponentFixture<ZatcaItemFormComponent>;
  let component: ZatcaItemFormComponent;
  let contextSubject: BehaviorSubject<SessionContext | null>;
  let mockSessionCtx: jasmine.SpyObj<SessionContextService>;
  let mockZatcaService: jasmine.SpyObj<ZatcaItemService>;
  let mockToast: jasmine.SpyObj<ToastNotificationService>;

  const mockContext: SessionContext = {
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
        items: {
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

    mockZatcaService = jasmine.createSpyObj('ZatcaItemService', ['get', 'create', 'update']);
    mockToast = jasmine.createSpyObj('ToastNotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [ZatcaItemFormComponent, NoopAnimationsModule, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionContextService, useValue: mockSessionCtx },
        { provide: ZatcaItemService, useValue: mockZatcaService },
        { provide: ToastNotificationService, useValue: mockToast },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
      ],
    });

    fixture = TestBed.createComponent(ZatcaItemFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render form title "New ZATCA Item" in English', () => {
    const title = fixture.nativeElement.querySelector('mat-card-title');
    expect(title.textContent).toContain('New ZATCA Item');
  });

  it('should have internalCode field required', () => {
    const control = component.form.get('internalCode')!;
    control.setValue('');
    control.markAsTouched();
    expect(control.hasError('required')).toBeTrue();
  });

  it('should have nameEn field required', () => {
    const control = component.form.get('nameEn')!;
    control.setValue('');
    control.markAsTouched();
    expect(control.hasError('required')).toBeTrue();
  });

  it('should have vatCategory field required', () => {
    const control = component.form.get('vatCategory')!;
    control.setValue('');
    control.markAsTouched();
    expect(control.hasError('required')).toBeTrue();
  });

  it('should have vatCategory select with S, Z, E, O options', () => {
    expect(component.vatCategories.length).toBe(4);
    expect(component.vatCategories.map(c => c.value)).toEqual(['S', 'Z', 'E', 'O']);
  });

  it('should disable vatRate when category is not S', () => {
    component.form.get('vatCategory')!.setValue('Z');
    expect(component.form.get('vatRate')!.disabled).toBeTrue();
  });

  it('should enable vatRate when category is S', () => {
    component.form.get('vatCategory')!.setValue('S');
    expect(component.form.get('vatRate')!.enabled).toBeTrue();
  });

  it('should display all field labels in English', () => {
    const labels = fixture.nativeElement.querySelectorAll('mat-label');
    const texts = Array.from(labels).map(l => (l as HTMLElement).textContent?.trim());
    expect(texts).toContain('Internal Code *');
    expect(texts).toContain('Name (English) *');
    expect(texts).toContain('Name (Arabic)');
    expect(texts).toContain('VAT Category *');
    expect(texts).toContain('VAT Rate');
  });

  it('should display Arabic name input with dir="rtl" while labels stay LTR', () => {
    const arabicInput = fixture.nativeElement.querySelector('input[dir="rtl"]');
    expect(arabicInput).not.toBeNull();
    expect(arabicInput.getAttribute('dir')).toBe('rtl');

    const labels = fixture.nativeElement.querySelectorAll('mat-label');
    Array.from(labels).forEach(label => {
      expect((label as HTMLElement).getAttribute('dir')).toBeFalsy();
    });
  });

  it('should disable submit when form is invalid', () => {
    component.form.get('internalCode')!.setValue('');
    component.form.get('nameEn')!.setValue('');
    fixture.detectChanges();

    const submitBtn = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(submitBtn.disabled).toBeTrue();
  });
});
