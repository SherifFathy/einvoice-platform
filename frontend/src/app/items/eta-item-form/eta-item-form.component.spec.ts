import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { EtaItemFormComponent } from './eta-item-form.component';
import { EtaItemService } from '../services/eta-item.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import type { SessionContext } from '../../shared/services/auth.service';

describe('EtaItemFormComponent', () => {
  let fixture: ComponentFixture<EtaItemFormComponent>;
  let component: EtaItemFormComponent;
  let contextSubject: BehaviorSubject<SessionContext | null>;
  let mockSessionCtx: jasmine.SpyObj<SessionContextService>;
  let mockEtaService: jasmine.SpyObj<EtaItemService>;
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

    mockEtaService = jasmine.createSpyObj('EtaItemService', ['get', 'create', 'update']);
    mockToast = jasmine.createSpyObj('ToastNotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [EtaItemFormComponent, NoopAnimationsModule, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionContextService, useValue: mockSessionCtx },
        { provide: EtaItemService, useValue: mockEtaService },
        { provide: ToastNotificationService, useValue: mockToast },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
      ],
    });

    fixture = TestBed.createComponent(EtaItemFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render form title "New ETA Item" in English', () => {
    const title = fixture.nativeElement.querySelector('mat-card-title');
    expect(title.textContent).toContain('New ETA Item');
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

  it('should have itemType field required', () => {
    const control = component.form.get('itemType')!;
    control.setValue('');
    control.markAsTouched();
    expect(control.hasError('required')).toBeTrue();
  });

  it('should have itemCode field required', () => {
    const control = component.form.get('itemCode')!;
    control.setValue('');
    control.markAsTouched();
    expect(control.hasError('required')).toBeTrue();
  });

  it('should have itemType select with GS1 and EGS options', () => {
    expect(component.itemTypes.length).toBe(2);
    expect(component.itemTypes.map(t => t.value)).toEqual(['GS1', 'EGS']);
  });

  it('should display all field labels in English', () => {
    const labels = fixture.nativeElement.querySelectorAll('mat-label');
    const texts = Array.from(labels).map(l => (l as HTMLElement).textContent?.trim());
    expect(texts).toContain('Internal Code *');
    expect(texts).toContain('Item Type *');
    expect(texts).toContain('Item Code *');
    expect(texts).toContain('Name (English) *');
    expect(texts).toContain('Name (Arabic)');
    expect(texts).toContain('Unit Price');
    expect(texts).toContain('Tax Rate');
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

  it('should enable submit when form is valid', () => {
    component.form.patchValue({
      internalCode: 'SKU-001',
      itemType: 'EGS',
      itemCode: 'EG-001',
      nameEn: 'Widget',
    });
    fixture.detectChanges();

    const submitBtn = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(submitBtn.disabled).toBeFalse();
  });
});
