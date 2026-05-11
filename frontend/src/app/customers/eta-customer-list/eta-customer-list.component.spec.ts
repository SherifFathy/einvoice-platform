import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject, of } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { EtaCustomerListComponent } from './eta-customer-list.component';
import { EtaCustomerService, EtaCustomerPage } from '../services/eta-customer.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import type { SessionContext } from '../../shared/services/auth.service';

describe('EtaCustomerListComponent', () => {
  let fixture: ComponentFixture<EtaCustomerListComponent>;
  let component: EtaCustomerListComponent;
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

  const multiCompanyContext: SessionContext = {
    ...mockContext,
    companies: [
      {
        companyId: 'c1',
        companyNameEn: 'Alpha Co',
        companyNameAr: 'ألفا',
        isActive: true,
        modules: {
          customers: {
            visible: true,
            permissions: { view: true, create: true, edit: true, delete: true, cancel: false, transfer: false, refresh: false, submit: false },
          },
        },
      },
      {
        companyId: 'c2',
        companyNameEn: 'Beta Co',
        companyNameAr: 'بيتا',
        isActive: true,
        modules: {
          customers: {
            visible: true,
            permissions: { view: true, create: true, edit: true, delete: true, cancel: false, transfer: false, refresh: false, submit: false },
          },
        },
      },
    ],
  };

  const multiCompanyPage: EtaCustomerPage = {
    items: [
      {
        id: '550e8400-e29b-41d4-a716-446655440001', companyId: 'c1',
        nameEn: 'Acme Ltd', nameAr: 'أكمة المحدودة', taxNumber: '123-456-789',
        customerType: 'B', isActive: true,
        addressData: { country: 'EG', governorate: 'Cairo', regionCity: 'Nasr City', street: 'Abbas El-Akkad', buildingNumber: '12' },
        contactEmail: null, contactPhone: null,
        createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
      },
      {
        id: '550e8400-e29b-41d4-a716-446655440003', companyId: 'c2',
        nameEn: 'Beta Corp ZATCA', nameAr: null, taxNumber: '999-888',
        customerType: 'B', isActive: true,
        addressData: { country: 'EG', governorate: 'Cairo', regionCity: 'Nasr City', street: '10', buildingNumber: '1' },
        contactEmail: null, contactPhone: null,
        createdAt: '2026-01-03T00:00:00Z', updatedAt: '2026-01-03T00:00:00Z',
      },
    ],
    page: { page: 0, size: 20, total: 2 },
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

  const mockPage: EtaCustomerPage = {
    items: [
      {
        id: '550e8400-e29b-41d4-a716-446655440001', companyId: 'c1',
        nameEn: 'Acme Ltd', nameAr: 'أكمة المحدودة', taxNumber: '123-456-789',
        customerType: 'B', isActive: true,
        addressData: { country: 'EG', governorate: 'Cairo', regionCity: 'Nasr City', street: 'Abbas El-Akkad', buildingNumber: '12' },
        contactEmail: null, contactPhone: null,
        createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
      },
      {
        id: '550e8400-e29b-41d4-a716-446655440002', companyId: 'c1',
        nameEn: 'Beta Corp', nameAr: null, taxNumber: null,
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

    mockEtaService = jasmine.createSpyObj('EtaCustomerService', ['list', 'get', 'create', 'update', 'delete']);
    mockEtaService.list.and.returnValue(of(mockPage));

    mockToast = jasmine.createSpyObj('ToastNotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [EtaCustomerListComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionContextService, useValue: mockSessionCtx },
        { provide: EtaCustomerService, useValue: mockEtaService },
        { provide: ToastNotificationService, useValue: mockToast },
        { provide: ActivatedRoute, useValue: {} },
      ],
    });

    fixture = TestBed.createComponent(EtaCustomerListComponent);
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
    expect(headerTexts).toContain('Tax Number');
    expect(headerTexts).toContain('Customer Type');
    expect(headerTexts).toContain('Active');
    expect(headerTexts).toContain('Actions');
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
    expect(mockEtaService.list).toHaveBeenCalledWith('c1', 0, 20, undefined, false, undefined);
  });

  it('should pass search term to list after debounce', (done) => {
    component.searchValue = 'Acme';
    component.onSearch();
    setTimeout(() => {
      expect(mockEtaService.list).toHaveBeenCalledWith('c1', 0, 20, 'Acme', false, undefined);
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

  it('should render "Company" column header', () => {
    const headers = fixture.nativeElement.querySelectorAll('th.mat-mdc-header-cell');
    const headerTexts = Array.from(headers).map(h => (h as HTMLElement).textContent?.trim());
    expect(headerTexts).toContain('Company');
  });

  it('should display "All companies" as default option in dropdown', async () => {
    const trigger = fixture.nativeElement.querySelector('mat-select .mat-mdc-select-trigger') as HTMLElement;
    trigger.click();
    fixture.detectChanges();
    await fixture.whenStable();

    const allOption = document.querySelector('mat-option');
    expect(allOption).not.toBeNull();
    expect(allOption?.textContent?.trim()).toContain('All companies');
  });

  describe('multi-company filter', () => {
    beforeEach(() => {
      mockEtaService.list.calls.reset();
      mockEtaService.list.and.returnValue(of(multiCompanyPage));
      contextSubject.next(multiCompanyContext);
      fixture.detectChanges();
    });

    it('should call list without filterCompanyId when "All companies" is selected', () => {
      component.selectedCompanyId = '';
      component.loadCustomers();
      expect(mockEtaService.list).toHaveBeenCalledWith('c1', 0, 20, undefined, false, undefined);
    });

    it('should call list with filterCompanyId when a specific company is selected', () => {
      component.selectedCompanyId = 'c2';
      component.loadCustomers();
      expect(mockEtaService.list).toHaveBeenCalledWith('c1', 0, 20, undefined, false, 'c2');
    });

    it('should show company name from companyNameMap in each row', () => {
      const cells = fixture.nativeElement.querySelectorAll('td.mat-mdc-cell');
      const texts = Array.from(cells).map(c => (c as HTMLElement).textContent?.trim());
      expect(texts).toContain('Alpha Co');
      expect(texts).toContain('Beta Co');
    });

    it('should populate companyNameMap from session context companies', () => {
      expect(component.companyNameMap['c1']).toBe('Alpha Co');
      expect(component.companyNameMap['c2']).toBe('Beta Co');
    });

    it('should narrow visible rows when company filter is changed', () => {
      mockEtaService.list.and.returnValue(of({
        items: [multiCompanyPage.items[0]],
        page: { page: 0, size: 20, total: 1 },
      }));

      component.selectedCompanyId = 'c1';
      component.onCompanyChange();
      fixture.detectChanges();

      expect(mockEtaService.list).toHaveBeenCalledWith('c1', 0, 20, undefined, false, 'c1');
    });

    it('should reset page index when company filter changes', () => {
      component.pageIndex = 3;
      component.selectedCompanyId = 'c2';
      component.onCompanyChange();
      expect(component.pageIndex).toBe(0);
    });
  });
});
