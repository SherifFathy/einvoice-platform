import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { SessionContext } from '../shared/services/auth.service';
import { SessionContextService } from '../shared/services/session-context.service';
import { AuditLogResponse, AuditLogService } from '../shared/services/audit-log.service';
import { LogsComponent } from './logs.component';

function makePermissions() {
  return { view: true, create: true, edit: true, delete: true, cancel: true, transfer: true, refresh: true, submit: true };
}

function makeCompany(id: string, name: string) {
  return {
    companyId: id,
    companyNameEn: name,
    companyNameAr: name,
    isActive: true,
    modules: { invoices: { visible: true, permissions: makePermissions() } },
  };
}

function makeContext(overrides: Partial<SessionContext> = {}): SessionContext {
  return {
    userId: 'u1',
    isSuperUser: false,
    mode: 'AUTHORITY_SCOPED',
    loginContext: { authority: 'ETA', environment: 'PREPROD', authorityEnvironmentId: 2 },
    companies: [makeCompany('c1', 'Co A'), makeCompany('c2', 'Co B')],
    ...overrides,
    activeCompanyId: overrides.activeCompanyId ?? null,
  };
}

function makeRow(overrides: Partial<AuditLogResponse> = {}): AuditLogResponse {
  return {
    id: 1,
    companyId: 'c1',
    companyName: 'Co A',
    userId: 'u1',
    action: 'create',
    entityType: 'Company',
    entityId: 'c1',
    payloadBefore: '{"placeholder":"before"}',
    payloadAfter: '{"placeholder":"after"}',
    ipAddress: '127.0.0.1',
    timestamp: '2026-06-08T09:00:00Z',
    ...overrides,
  };
}

describe('LogsComponent', () => {
  let fixture: ComponentFixture<LogsComponent>;
  let component: LogsComponent;
  let mockService: jasmine.SpyObj<AuditLogService>;

  function setup(rows: AuditLogResponse[], ctx: SessionContext | null): void {
    TestBed.resetTestingModule();
    mockService = jasmine.createSpyObj('AuditLogService', ['list']);
    mockService.list.and.returnValue(of({
      content: rows,
      totalElements: rows.length,
      totalPages: rows.length === 0 ? 0 : 1,
      number: 0,
      size: 20,
    }));
    const mockSessionCtx = {
      currentContext: ctx,
    };

    TestBed.configureTestingModule({
      imports: [LogsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuditLogService, useValue: mockService },
        { provide: SessionContextService, useValue: mockSessionCtx },
      ],
    });

    fixture = TestBed.createComponent(LogsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('shows the Company column only when more than one company is accessible', () => {
    setup([makeRow()], makeContext({ companies: [makeCompany('c1', 'Solo Co')] }));
    expect(component.showCompanyColumn).toBeFalse();
    expect(component.displayedColumns).not.toContain('company');

    setup(
      [
        makeRow({ id: 1, companyId: 'c1', companyName: 'Co A' }),
        makeRow({ id: 2, companyId: 'c2', companyName: 'Co B' }),
      ],
      makeContext(),
    );
    expect(component.showCompanyColumn).toBeTrue();
    expect(component.displayedColumns).toContain('company');
  });

  it('renders the owning company name for each row when cross-company', () => {
    setup(
      [
        makeRow({ id: 1, companyId: 'c1', companyName: 'Co A' }),
        makeRow({ id: 2, companyId: 'c2', companyName: 'Co B' }),
      ],
      makeContext(),
    );
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Co A');
    expect(text).toContain('Co B');
  });

  it('sends the companyId filter to the service when a company is selected', () => {
    setup([makeRow()], makeContext());
    component.companyFilter = 'c1';
    component.loadLogs();
    expect(mockService.list).toHaveBeenCalled();
    const lastCall = mockService.list.calls.mostRecent().args;
    expect(lastCall[6]).toBe('c1');
  });

  it('loads rows from the env-scoped endpoint into the table', () => {
    setup([makeRow({ id: 9, companyName: 'Co A' })], makeContext());
    expect(component.logs.length).toBe(1);
    expect(component.totalElements).toBe(1);
  });
});
