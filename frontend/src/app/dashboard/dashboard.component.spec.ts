import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { SessionContext } from '../shared/services/auth.service';
import { SessionContextService } from '../shared/services/session-context.service';
import { DashboardComponent } from './dashboard.component';
import {
  DashboardService,
  DashboardSummary,
  RecentActivity,
} from './services/dashboard.service';

function makePermissions() {
  return { view: true, create: true, edit: true, delete: true, cancel: true, transfer: true, refresh: true, submit: true };
}

function makeContext(overrides: Partial<SessionContext> = {}): SessionContext {
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

function makeSummary(overrides: Partial<DashboardSummary> = {}): DashboardSummary {
  return {
    cards: [
      {
        companyId: 'c1', nameEn: 'Acme LLC', nameAr: 'أكمي', taxNumber: '123',
        active: true, pendingCount: 4, failedCount: 1,
        certificate: { daysRemaining: 12, expiringSoon: true, expired: false },
      },
      {
        companyId: 'c2', nameEn: 'Beta Co', nameAr: 'بيتا', taxNumber: '987',
        active: true, pendingCount: 0, failedCount: 0, certificate: null,
      },
    ],
    kpi: {
      today: { total: 5, byStatus: { SUBMITTING: 1, SUBMITTED: 3, REJECTED: 1 } },
      thisMonth: { total: 40, byStatus: { ACCEPTED: 40 } },
    },
    ...overrides,
  };
}

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let contextSubject: BehaviorSubject<SessionContext | null>;
  let mockSessionCtx: jasmine.SpyObj<SessionContextService>;
  let mockDashboard: jasmine.SpyObj<DashboardService>;

  function setup(): void {
    contextSubject = new BehaviorSubject<SessionContext | null>(null);
    mockSessionCtx = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear'], {
      context$: contextSubject.asObservable(),
    });
    Object.defineProperty(mockSessionCtx, 'currentContext', {
      get: () => contextSubject.value,
      configurable: true,
    });
    mockDashboard = jasmine.createSpyObj('DashboardService', ['summary', 'recentActivity']);

    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: SessionContextService, useValue: mockSessionCtx },
        { provide: DashboardService, useValue: mockDashboard },
      ],
    });

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
  }

  it('renders per-company pending/failed stats from the summary endpoint', () => {
    setup();
    mockDashboard.summary.and.returnValue(of(makeSummary()));
    mockDashboard.recentActivity.and.returnValue(of({ entries: [] } as RecentActivity));

    contextSubject.next(makeContext());
    fixture.detectChanges();

    expect(component.cards.length).toBe(2);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Acme LLC');
    expect(text).toContain('Pending');
    expect(text).toContain('Failed');
    expect(text).toContain('Certificate expires in 12 day(s)');
    expect(component.loading).toBeFalse();
    expect(component.error).toBeFalse();
  });

  it('shows the empty state when no companies are returned', () => {
    setup();
    mockDashboard.summary.and.returnValue(of(makeSummary({
      cards: [],
      kpi: { today: { total: 0, byStatus: {} }, thisMonth: { total: 0, byStatus: {} } },
    })));
    mockDashboard.recentActivity.and.returnValue(of({ entries: [] } as RecentActivity));

    contextSubject.next(makeContext());
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('No companies are registered in this environment yet.');
  });

  it('does not call the operational endpoints in Admin Mode', () => {
    setup();
    contextSubject.next(makeContext({ isSuperUser: true, mode: 'ADMIN_MODE', companies: [] }));
    fixture.detectChanges();

    expect(mockDashboard.summary).not.toHaveBeenCalled();
    expect(mockDashboard.recentActivity).not.toHaveBeenCalled();
    expect(component.isAdminMode).toBeTrue();
  });
});
