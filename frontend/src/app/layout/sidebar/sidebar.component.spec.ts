import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { SessionContext } from '../../shared/services/auth.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { SidebarComponent } from './sidebar.component';

interface TestSidebarItem {
  label: string;
  route: string;
  moduleKey?: string;
}

function makePermissions() {
  return { view: true, create: true, edit: true, delete: true, cancel: true, transfer: true, refresh: true, submit: true };
}

function makeEtaContext(overrides: Partial<SessionContext> = {}): SessionContext {
  return {
    userId: 'u1',
    isSuperUser: false,
    mode: 'OPERATIONAL_MODE',
    loginContext: { authority: 'ETA', environment: 'PREPROD', authorityEnvironmentId: 2 },
    companies: [{
      companyId: 'c1',
      companyNameEn: 'Test Co',
      companyNameAr: 'شركة اختبار',
      isActive: true,
      modules: {
        invoice: { visible: true, permissions: makePermissions() },
        receipt: { visible: true, permissions: makePermissions() },
        customers: { visible: true, permissions: makePermissions() },
        items: { visible: true, permissions: makePermissions() },
        configuration: { visible: true, permissions: makePermissions() },
      },
    }],
    ...overrides,
    activeCompanyId: overrides.activeCompanyId ?? 'c1',
  };
}

function makeZatcaContext(overrides: Partial<SessionContext> = {}): SessionContext {
  return {
    userId: 'u1',
    isSuperUser: false,
    mode: 'OPERATIONAL_MODE',
    loginContext: { authority: 'ZATCA', environment: 'SANDBOX', authorityEnvironmentId: 5 },
    companies: [{
      companyId: 'c1',
      companyNameEn: 'Test Co',
      companyNameAr: 'شركة اختبار',
      isActive: true,
      modules: {
        standard: { visible: true, permissions: makePermissions() },
        simplified: { visible: true, permissions: makePermissions() },
        customers: { visible: true, permissions: makePermissions() },
        items: { visible: true, permissions: makePermissions() },
        configuration: { visible: true, permissions: makePermissions() },
      },
    }],
    ...overrides,
    activeCompanyId: overrides.activeCompanyId ?? 'c1',
  };
}

describe('SidebarComponent', () => {
  let fixture: ComponentFixture<SidebarComponent>;
  let component: SidebarComponent;
  let contextSubject: BehaviorSubject<SessionContext | null>;
  let mockSessionCtx: jasmine.SpyObj<SessionContextService>;

  beforeEach(() => {
    contextSubject = new BehaviorSubject<SessionContext | null>(null);

    mockSessionCtx = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear'], {
      context$: contextSubject.asObservable(),
    });
    Object.defineProperty(mockSessionCtx, 'currentContext', {
      get: () => contextSubject.value,
      configurable: true,
    });

    TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        provideRouter([]),
        { provide: SessionContextService, useValue: mockSessionCtx },
      ],
    });

    fixture = TestBed.createComponent(SidebarComponent);
    component = fixture.componentInstance;
  });

  describe('ETA session (US4 acceptance #1)', () => {
    beforeEach(() => {
      contextSubject.next(makeEtaContext());
      fixture.detectChanges();
    });

    it('should show all ETA module labels when all modules are visible', () => {
      const labels = (component.items() as TestSidebarItem[]).map((i) => i.label);
      expect(labels).toEqual(['Dashboard', 'Invoices', 'Receipts', 'Submission Log', 'Customers', 'Items', 'Configuration', 'Logs']);
    });

    it('should show correct routes for ETA items', () => {
      const routes = (component.items() as TestSidebarItem[]).map((i) => i.route);
      expect(routes).toContain('/invoices/eta');
      expect(routes).toContain('/receipts/eta');
    });
  });

  describe('ZATCA session (US4 acceptance #2)', () => {
    beforeEach(() => {
      contextSubject.next(makeZatcaContext());
      fixture.detectChanges();
    });

    it('should show all ZATCA module labels when all modules are visible', () => {
      const labels = (component.items() as TestSidebarItem[]).map((i) => i.label);
      expect(labels).toEqual(['Dashboard', 'Standard', 'Simplified', 'Submission Log', 'Customers', 'Items', 'Configuration', 'Logs']);
    });

    it('should show correct routes for ZATCA items', () => {
      const routes = (component.items() as TestSidebarItem[]).map((i) => i.route);
      expect(routes).toContain('/standard');
      expect(routes).toContain('/simplified');
    });
  });

  describe('visibility filtering (FR-043)', () => {
    it('should show all ETA items regardless of module visibility (permission-gated in template)', () => {
      const ctx = makeEtaContext({
        companies: [{
          companyId: 'c1',
          companyNameEn: 'Test Co',
          companyNameAr: 'شركة اختبار',
          isActive: true,
          modules: {
            invoice: { visible: true, permissions: makePermissions() },
            receipt: { visible: false, permissions: makePermissions() },
            customers: { visible: true, permissions: makePermissions() },
            items: { visible: true, permissions: makePermissions() },
            configuration: { visible: true, permissions: makePermissions() },
          },
        }],
      });
      contextSubject.next(ctx);
      fixture.detectChanges();

      const labels = (component.items() as TestSidebarItem[]).map((i) => i.label);
      expect(labels).toContain('Invoices');
      expect(labels).toContain('Receipts');
      expect(labels).toContain('Dashboard');
      expect(labels).toContain('Logs');
    });

    it('should show module when at least one company has it visible (union logic)', () => {
      const ctx = makeEtaContext({
        companies: [
          {
            companyId: 'c1',
            companyNameEn: 'Co A',
            companyNameAr: 'شركة أ',
            isActive: true,
            modules: {
              invoice: { visible: true, permissions: makePermissions() },
              receipt: { visible: false, permissions: makePermissions() },
              customers: { visible: true, permissions: makePermissions() },
              items: { visible: true, permissions: makePermissions() },
              configuration: { visible: true, permissions: makePermissions() },
            },
          },
          {
            companyId: 'c2',
            companyNameEn: 'Co B',
            companyNameAr: 'شركة ب',
            isActive: true,
            modules: {
              invoice: { visible: false, permissions: makePermissions() },
              receipt: { visible: true, permissions: makePermissions() },
              customers: { visible: true, permissions: makePermissions() },
              items: { visible: true, permissions: makePermissions() },
              configuration: { visible: true, permissions: makePermissions() },
            },
          },
        ],
      });
      contextSubject.next(ctx);
      fixture.detectChanges();

      const labels = (component.items() as TestSidebarItem[]).map((i) => i.label);
      expect(labels).toContain('Invoices');
      expect(labels).toContain('Receipts');
    });
  });

  describe('Super User Admin entry (FR-044)', () => {
    it('should show admin management entries for Super User in Operational Mode', () => {
      contextSubject.next(makeEtaContext({ isSuperUser: true }));
      fixture.detectChanges();

      const labels = (component.items() as TestSidebarItem[]).map((i) => i.label);
      expect(labels).toContain('Companies');
      expect(labels).toContain('Branches');
      expect(labels).toContain('Users');
      expect(labels).toContain('Assignments');
    });

    it('should link the Companies entry to the admin company list', () => {
      contextSubject.next(makeEtaContext({ isSuperUser: true }));
      fixture.detectChanges();

      const companies = (component.items() as TestSidebarItem[]).find((i) => i.label === 'Companies');
      expect(companies?.route).toBe('/admin/companies');
    });

    it('should not show admin management entries for regular user', () => {
      contextSubject.next(makeEtaContext({ isSuperUser: false }));
      fixture.detectChanges();

      const labels = (component.items() as TestSidebarItem[]).map((i) => i.label);
      expect(labels).not.toContain('Companies');
      expect(labels).not.toContain('Assignments');
    });
  });

  describe('Admin Mode sidebar', () => {
    beforeEach(() => {
      contextSubject.next(makeEtaContext({
        isSuperUser: true,
        mode: 'ADMIN_MODE',
        companies: [],
      }));
      fixture.detectChanges();
    });

    it('should show admin sidebar items in Admin Mode', () => {
      const labels = (component.items() as TestSidebarItem[]).map((i) => i.label);
      expect(labels).toEqual(['Dashboard', 'Companies', 'Branches', 'Users', 'Assignments', 'Logs']);
    });

    it('should not show operational module items in Admin Mode', () => {
      const labels = (component.items() as TestSidebarItem[]).map((i) => i.label);
      expect(labels).not.toContain('Invoices');
      expect(labels).not.toContain('Standard');
    });
  });

  describe('no context', () => {
    it('should return empty items when context is null', () => {
      contextSubject.next(null);
      fixture.detectChanges();
      expect(component.items()).toEqual([]);
    });
  });

  describe('rendered labels in DOM', () => {
    it('should render ETA module labels in the template', () => {
      contextSubject.next(makeEtaContext());
      fixture.detectChanges();

      const linkTexts = Array.from(
        fixture.nativeElement.querySelectorAll('span[matListItemTitle]') as NodeListOf<HTMLElement>,
      ).map((el) => el.textContent?.trim() ?? '');
      expect(linkTexts).toContain('Invoices');
      expect(linkTexts).toContain('Receipts');
      expect(linkTexts).toContain('Customers');
    });

    it('should render ZATCA module labels in the template', () => {
      contextSubject.next(makeZatcaContext());
      fixture.detectChanges();

      const linkTexts = Array.from(
        fixture.nativeElement.querySelectorAll('span[matListItemTitle]') as NodeListOf<HTMLElement>,
      ).map((el) => el.textContent?.trim() ?? '');
      expect(linkTexts).toContain('Standard');
      expect(linkTexts).toContain('Simplified');
      expect(linkTexts).toContain('Customers');
    });
  });
});
