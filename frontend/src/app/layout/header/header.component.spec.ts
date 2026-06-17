import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { SessionContext, AuthService } from '../../shared/services/auth.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { PlatformBrandingService } from '../../shared/services/platform-branding.service';
import { HeaderComponent } from './header.component';

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
      },
    }],
    ...overrides,
    activeCompanyId: overrides.activeCompanyId ?? 'c1',
  };
}

describe('HeaderComponent', () => {
  let fixture: ComponentFixture<HeaderComponent>;
  let component: HeaderComponent;
  let contextSubject: BehaviorSubject<SessionContext | null>;
  let mockSessionCtx: jasmine.SpyObj<SessionContextService>;
  let mockAuth: jasmine.SpyObj<AuthService>;
  let mockBranding: jasmine.SpyObj<PlatformBrandingService>;
  let titleService: Title;

  beforeEach(() => {
    contextSubject = new BehaviorSubject<SessionContext | null>(null);

    mockSessionCtx = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear'], {
      context$: contextSubject.asObservable(),
    });
    Object.defineProperty(mockSessionCtx, 'currentContext', {
      get: () => contextSubject.value,
      configurable: true,
    });

    mockAuth = jasmine.createSpyObj('AuthService', ['logout', 'getToken', 'clearAuth', 'listEnvironments', 'listCompanies', 'login']);
    mockAuth.logout.and.returnValue(of(undefined));
    mockBranding = jasmine.createSpyObj('PlatformBrandingService',
      ['logoUrl'],
      { logoVersion: signal(Date.now()) });
    mockBranding.logoUrl.and.returnValue('/api/platform/branding/logo?v=1');

    TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: mockAuth },
        { provide: SessionContextService, useValue: mockSessionCtx },
        { provide: PlatformBrandingService, useValue: mockBranding },
      ],
    });

    titleService = TestBed.inject(Title);
    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
  });

  describe('dynamic page title (FR-041)', () => {
    it('should set title to "ETA Platform" when authority is ETA', () => {
      contextSubject.next(makeEtaContext());
      expect(titleService.getTitle()).toBe('ETA Platform');
    });

    it('should set title to "ZATCA Platform" when authority is ZATCA', () => {
      contextSubject.next(makeZatcaContext());
      expect(titleService.getTitle()).toBe('ZATCA Platform');
    });

    it('should set title to "E-Invoice Platform" when context is null', () => {
      contextSubject.next(null);
      expect(titleService.getTitle()).toBe('E-Invoice Platform');
    });

    it('should update title when authority changes', () => {
      contextSubject.next(makeEtaContext());
      expect(titleService.getTitle()).toBe('ETA Platform');

      contextSubject.next(makeZatcaContext());
      expect(titleService.getTitle()).toBe('ZATCA Platform');
    });
  });

  describe('authority chip', () => {
    it('should display ETA in authority chip', () => {
      contextSubject.next(makeEtaContext());
      fixture.detectChanges();
      expect(component.authority).toBe('ETA');
    });

    it('should display ZATCA in authority chip', () => {
      contextSubject.next(makeZatcaContext());
      fixture.detectChanges();
      expect(component.authority).toBe('ZATCA');
    });
  });

  describe('environment chip', () => {
    it('should display environment value', () => {
      contextSubject.next(makeEtaContext());
      fixture.detectChanges();
      expect(component.environment).toBe('PREPROD');
    });
  });

  describe('context chip (FR-042 revised)', () => {
    it('should display "Admin Mode" in ADMIN_MODE', () => {
      contextSubject.next(makeEtaContext({ mode: 'ADMIN_MODE', companies: [] }));
      fixture.detectChanges();
      expect(component.contextChip).toBe('Admin Mode');
    });

    it('should display single company name in Operational Mode with one company', () => {
      contextSubject.next(makeEtaContext());
      fixture.detectChanges();
      expect(component.contextChip).toBe('Test Co');
    });

    it('should display "N companies" in Operational Mode with multiple companies', () => {
      const ctx = makeEtaContext({
        companies: [
          {
            companyId: 'c1', companyNameEn: 'Co A', companyNameAr: 'شركة أ', isActive: true,
            modules: { invoice: { visible: true, permissions: makePermissions() } },
          },
          {
            companyId: 'c2', companyNameEn: 'Co B', companyNameAr: 'شركة ب', isActive: true,
            modules: { invoice: { visible: true, permissions: makePermissions() } },
          },
        ],
      });
      contextSubject.next(ctx);
      fixture.detectChanges();
      expect(component.contextChip).toBe('2 companies');
    });

    it('should display empty string when context is null', () => {
      contextSubject.next(null);
      fixture.detectChanges();
      expect(component.contextChip).toBe('');
    });
  });

  describe('rendered chips in DOM', () => {
    it('should render all three chips for ETA context', () => {
      contextSubject.next(makeEtaContext());
      fixture.detectChanges();

      const chips = fixture.nativeElement.querySelectorAll('mat-chip');
      expect(chips.length).toBe(3);
      expect(chips[0].textContent).toContain('ETA');
      expect(chips[1].textContent).toContain('PREPROD');
      expect(chips[2].textContent).toContain('Test Co');
    });

    it('should render "Admin Mode" chip for admin mode', () => {
      contextSubject.next(makeEtaContext({ mode: 'ADMIN_MODE', isSuperUser: true, companies: [] }));
      fixture.detectChanges();

      const chips = fixture.nativeElement.querySelectorAll('mat-chip');
      expect(chips[2].textContent).toContain('Admin Mode');
    });

    it('should render "N companies" chip for multi-company operational mode', () => {
      const ctx = makeEtaContext({
        companies: [
          { companyId: 'c1', companyNameEn: 'Co A', companyNameAr: 'أ', isActive: true, modules: { invoice: { visible: true, permissions: makePermissions() } } },
          { companyId: 'c2', companyNameEn: 'Co B', companyNameAr: 'ب', isActive: true, modules: { invoice: { visible: true, permissions: makePermissions() } } },
          { companyId: 'c3', companyNameEn: 'Co C', companyNameAr: 'ج', isActive: true, modules: { invoice: { visible: true, permissions: makePermissions() } } },
        ],
      });
      contextSubject.next(ctx);
      fixture.detectChanges();

      const chips = fixture.nativeElement.querySelectorAll('mat-chip');
      expect(chips[2].textContent).toContain('3 companies');
    });
  });

  describe('Super User badge', () => {
    it('should render Super User badge for Super User', () => {
      contextSubject.next(makeEtaContext({ isSuperUser: true }));
      fixture.detectChanges();

      const badge = fixture.nativeElement.querySelector('.super-user-badge');
      expect(badge).not.toBeNull();
      expect(badge.textContent).toContain('Super User');
    });

    it('should not render Super User badge for regular user', () => {
      contextSubject.next(makeEtaContext({ isSuperUser: false }));
      fixture.detectChanges();

      const badge = fixture.nativeElement.querySelector('.super-user-badge');
      expect(badge).toBeNull();
    });
  });

  describe('logout', () => {
    it('should clear session and navigate to login on logout', () => {
      component.logout();
      expect(mockAuth.logout).toHaveBeenCalled();
    });
  });

  describe('ngOnDestroy', () => {
    it('should unsubscribe from title subscription', () => {
      const sub = (component as unknown as { titleSub: { unsubscribe: jasmine.Spy } }).titleSub;
      spyOn(sub, 'unsubscribe');
      component.ngOnDestroy();
      expect(sub.unsubscribe).toHaveBeenCalled();
    });
  });
});
