import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject, of } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { EtaItemListComponent } from './eta-item-list.component';
import { EtaItemService, EtaItemPage } from '../services/eta-item.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { ToastNotificationService } from '../../shared/services/toast.service';
import type { SessionContext } from '../../shared/services/auth.service';

describe('EtaItemListComponent', () => {
  let fixture: ComponentFixture<EtaItemListComponent>;
  let component: EtaItemListComponent;
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

  const viewerContext: SessionContext = {
    ...mockContext,
    companies: [{
      ...mockContext.companies[0],
      modules: {
        items: {
          visible: true,
          permissions: { view: true, create: false, edit: false, delete: false, cancel: false, transfer: false, refresh: false, submit: false },
        },
      },
    }],
  };

  const mockPage: EtaItemPage = {
    items: [
      {
        id: 'item-001', companyId: 'c1',
        internalCode: 'SKU-001', itemType: 'EGS', itemCode: 'EG-001',
        nameEn: 'Widget', nameAr: 'أداة', unitType: 'Each', unitPrice: 100,
        taxType: 'T1', taxSubtype: 'V009', taxRate: 14,
        isActive: true,
        createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'item-002', companyId: 'c1',
        internalCode: 'SKU-002', itemType: 'GS1', itemCode: 'EG-002',
        nameEn: 'Gadget', nameAr: null, unitType: null, unitPrice: null,
        taxType: null, taxSubtype: null, taxRate: null,
        isActive: false,
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

    mockEtaService = jasmine.createSpyObj('EtaItemService', ['list', 'get', 'create', 'update', 'delete']);
    mockEtaService.list.and.returnValue(of(mockPage));

    mockToast = jasmine.createSpyObj('ToastNotificationService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      imports: [EtaItemListComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionContextService, useValue: mockSessionCtx },
        { provide: EtaItemService, useValue: mockEtaService },
        { provide: ToastNotificationService, useValue: mockToast },
        { provide: ActivatedRoute, useValue: {} },
      ],
    });

    fixture = TestBed.createComponent(EtaItemListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render correct column headers in English', () => {
    const headers = fixture.nativeElement.querySelectorAll('th.mat-mdc-header-cell');
    const headerTexts = Array.from(headers).map(h => (h as HTMLElement).textContent?.trim());
    expect(headerTexts).toContain('Internal Code');
    expect(headerTexts).toContain('Item Type');
    expect(headerTexts).toContain('Item Code');
    expect(headerTexts).toContain('Name (EN)');
    expect(headerTexts).toContain('Name (AR)');
    expect(headerTexts).toContain('Unit Price');
    expect(headerTexts).toContain('Tax Type');
    expect(headerTexts).toContain('Tax Subtype');
    expect(headerTexts).toContain('Tax Rate');
    expect(headerTexts).toContain('Active');
    expect(headerTexts).toContain('Actions');
  });

  it('should render item data rows', () => {
    const rows = fixture.nativeElement.querySelectorAll('tr.mat-mdc-row');
    expect(rows.length).toBe(2);
  });

  it('should display Arabic name with dir="rtl" while row stays LTR', () => {
    const arabicCell = fixture.nativeElement.querySelector('td.mat-mdc-cell span[dir="rtl"]');
    expect(arabicCell).not.toBeNull();
    expect(arabicCell.textContent).toContain('أداة');
    expect(arabicCell.getAttribute('dir')).toBe('rtl');

    const row = arabicCell.closest('tr');
    expect(row?.getAttribute('dir')).toBeFalsy();
  });

  it('should call list with correct parameters on load', () => {
    expect(mockEtaService.list).toHaveBeenCalledWith('c1', 0, 20, undefined, false, undefined);
  });

  it('should pass search term to list after debounce', (done) => {
    component.searchValue = 'Widget';
    component.onSearch();
    setTimeout(() => {
      expect(mockEtaService.list).toHaveBeenCalledWith('c1', 0, 20, 'Widget', false, undefined);
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

  it('should hide "New Item" button when user lacks ITEMS/CREATE', () => {
    contextSubject.next(viewerContext);
    fixture.detectChanges();

    const newBtn = fixture.nativeElement.querySelector('button[mat-raised-button]');
    expect(newBtn).toBeNull();
  });

  it('should display "Search" label in English', () => {
    const label = fixture.nativeElement.querySelector('mat-label');
    expect(label.textContent).toContain('Search');
  });

  it('should display "Show inactive" toggle in English', () => {
    const toggle = fixture.nativeElement.querySelector('mat-slide-toggle');
    expect(toggle.textContent).toContain('Show inactive');
  });
});
