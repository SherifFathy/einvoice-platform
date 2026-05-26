import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject, of } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { ZatcaCustomerListComponent } from './zatca-customer-list.component';
import { ZatcaCustomerService, ZatcaCustomerPage } from '../services/zatca-customer.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import type { SessionContext } from '../../shared/services/auth.service';

describe('ZatcaCustomerListComponent', () => {
  let fixture: ComponentFixture<ZatcaCustomerListComponent>;
  let component: ZatcaCustomerListComponent;
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

  const viewerContext: SessionContext = {
    ...mockContext,
    companies: [{
      ...mockContext.companies[0],
      modules: {
        customers: {
          visible: true,
          permissions: { view: true, create: false, edit: false, delete: false, cancel: false, transfer: false, refresh: false, submit: false },
        },
      },
    }],
  };

  const mockPage: ZatcaCustomerPage = {
    items: [
      {
        id: '550e8400-e29b-41d4-a716-446655440001', companyId: 'c1',
        nameEn: 'Acme Ltd', nameAr: 'أكمة المحدودة', vatNumber: '300000000000003',
        customerType: 'B', isActive: true,
        addressData: { streetName: 'King Fahd Rd', buildingNumber: '1234', city: 'Riyadh', postalCode: '12211', districtName: 'Al Olaya', country: 'SA' },
        contactEmail: null, contactPhone: null,
        createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
      },
      {
        id: '550e8400-e29b-41d4-a716-446655440002', companyId: 'c1',
        nameEn: 'Beta Corp', nameAr: null, vatNumber: null,
        customerType: 'P', isActive: false,
        addressData: null, contactEmail: null, contactPhone: null,
        createdAt: '2026-01-02T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z',
      },
    ],
    page: { page: 0, size: 20, total: 2 },
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

    mockZatcaService = jasmine.createSpyObj('ZatcaCustomerService', ['list', 'get', 'create', 'update', 'delete']);
    mockZatcaService.list.and.returnValue(of(mockPage));

    mockToast = jasmine.createSpyObj('ToastNotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [ZatcaCustomerListComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionContextService, useValue: mockSessionCtx },
        { provide: ZatcaCustomerService, useValue: mockZatcaService },
        { provide: ToastNotificationService, useValue: mockToast },
        { provide: ActivatedRoute, useValue: {} },
      ],
    });

    fixture = TestBed.createComponent(ZatcaCustomerListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render correct column headers in English', () => {
    const headers = fixture.nativeElement.querySelectorAll('th.mat-mdc-header-cell');
    const headerTexts = Array.from(headers).map(h => (h as HTMLElement).textContent?.trim());
    expect(headerTexts).toContain('Name (EN)');
    expect(headerTexts).toContain('Name (AR)');
    expect(headerTexts).toContain('VAT Number');
    expect(headerTexts).toContain('Customer Type');
    expect(headerTexts).toContain('Active');
    expect(headerTexts).toContain('Actions');
  });

  it('should use VAT Number column instead of Tax Number', () => {
    const headers = fixture.nativeElement.querySelectorAll('th.mat-mdc-header-cell');
    const headerTexts = Array.from(headers).map(h => (h as HTMLElement).textContent?.trim());
    expect(headerTexts).toContain('VAT Number');
    expect(headerTexts).not.toContain('Tax Number');
  });

  it('should render customer data rows', () => {
    const rows = fixture.nativeElement.querySelectorAll('tr.mat-mdc-row');
    expect(rows.length).toBe(2);
  });

  it('should display Arabic name with dir="rtl" while row stays LTR', () => {
    const arabicCell = fixture.nativeElement.querySelector('td.mat-mdc-cell span[dir="rtl"]');
    expect(arabicCell).not.toBeNull();
    expect(arabicCell.textContent).toContain('أكمة المحدودة');
    expect(arabicCell.getAttribute('dir')).toBe('rtl');

    const row = arabicCell.closest('tr');
    expect(row?.getAttribute('dir')).toBeFalsy();
  });

  it('should show active status with Yes/No labels', () => {
    const cells = fixture.nativeElement.querySelectorAll('td.mat-mdc-cell');
    const texts = Array.from(cells).map(c => (c as HTMLElement).textContent?.trim());
    expect(texts).toContain('Yes');
    expect(texts).toContain('No');
  });

  it('should call list with correct parameters on load', () => {
    expect(mockZatcaService.list).toHaveBeenCalledWith('c1', 0, 20, undefined, false, undefined);
  });

  it('should pass search term to list after debounce', (done) => {
    component.searchValue = 'Acme';
    component.onSearch();
    setTimeout(() => {
      expect(mockZatcaService.list).toHaveBeenCalledWith('c1', 0, 20, 'Acme', false, undefined);
      done();
    }, 400);
  });

  it('should hide action buttons when user is VIEWER only', () => {
    contextSubject.next(viewerContext);
    fixture.detectChanges();

    const editButtons = fixture.nativeElement.querySelectorAll('a[mat-icon-button]');
    const deleteButtons = fixture.nativeElement.querySelectorAll('button[color="warn"]');
    expect(editButtons.length).toBe(0);
    expect(deleteButtons.length).toBe(0);
  });

  it('should hide "New Customer" button when user lacks CUSTOMERS/CREATE', () => {
    contextSubject.next(viewerContext);
    fixture.detectChanges();

    const newBtn = fixture.nativeElement.querySelector('button[mat-raised-button]');
    expect(newBtn).toBeNull();
  });

  it('should display "Search" label in English', () => {
    const label = fixture.nativeElement.querySelector('mat-label');
    expect(label.textContent).toContain('Search');
  });

  it('should display "Company" filter label in English', () => {
    const labels = fixture.nativeElement.querySelectorAll('mat-label');
    const texts = Array.from(labels).map(l => (l as HTMLElement).textContent?.trim());
    expect(texts).toContain('Company');
  });

  it('should display "Show inactive" toggle in English', () => {
    const toggle = fixture.nativeElement.querySelector('mat-slide-toggle');
    expect(toggle.textContent).toContain('Show inactive');
  });
});
