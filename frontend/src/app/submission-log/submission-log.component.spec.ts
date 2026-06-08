import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject, of } from 'rxjs';
import { SessionContext } from '../shared/services/auth.service';
import { SessionContextService } from '../shared/services/session-context.service';
import { SubmissionLogComponent } from './submission-log.component';
import {
  SubmissionLogPage,
  SubmissionLogRow,
  SubmissionLogService,
} from './services/submission-log.service';

function makePermissions() {
  return { view: true, create: true, edit: true, delete: true, cancel: true, transfer: true, refresh: true, submit: true };
}

function makeCompany(id: string, name: string) {
  return {
    companyId: id,
    companyNameEn: name,
    companyNameAr: name,
    isActive: true,
    modules: { standard: { visible: true, permissions: makePermissions() } },
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

function makeRow(overrides: Partial<SubmissionLogRow> = {}): SubmissionLogRow {
  return {
    attemptId: 'a1',
    companyId: 'c1',
    companyName: 'Co A',
    transactionType: 'SIMPLIFIED',
    documentId: 'doc1',
    attemptNumber: 1,
    outcome: 'REJECTED',
    statusCode: 400,
    errorSummary: 'VAT category invalid',
    submittedAt: '2026-06-02T09:14:00Z',
    completedAt: '2026-06-02T09:14:03Z',
    submittedBy: 'u1',
    ...overrides,
  };
}

function makePage(rows: SubmissionLogRow[]): SubmissionLogPage {
  return { items: rows, page: 0, size: 20, totalElements: rows.length };
}

describe('SubmissionLogService route map', () => {
  let service: SubmissionLogService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SubmissionLogService);
  });

  it('maps each transaction type to its class-correct detail route', () => {
    expect(service.detailLink(makeRow({ transactionType: 'INVOICE', documentId: 'd' })))
      .toEqual(['/invoices/eta', 'd']);
    expect(service.detailLink(makeRow({ transactionType: 'RECEIPT', documentId: 'd' })))
      .toEqual(['/receipts/eta', 'd']);
    expect(service.detailLink(makeRow({ transactionType: 'STANDARD', documentId: 'd' })))
      .toEqual(['/standard', 'd']);
    expect(service.detailLink(makeRow({ transactionType: 'SIMPLIFIED', documentId: 'd' })))
      .toEqual(['/simplified', 'd']);
  });
});

describe('SubmissionLogComponent', () => {
  let fixture: ComponentFixture<SubmissionLogComponent>;
  let component: SubmissionLogComponent;
  let contextSubject: BehaviorSubject<SessionContext | null>;
  let mockSessionCtx: jasmine.SpyObj<SessionContextService>;
  let mockService: jasmine.SpyObj<SubmissionLogService>;

  function setup(rows: SubmissionLogRow[], ctx: SessionContext | null): void {
    TestBed.resetTestingModule();
    contextSubject = new BehaviorSubject<SessionContext | null>(ctx);
    mockSessionCtx = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear'], {
      context$: contextSubject.asObservable(),
    });
    Object.defineProperty(mockSessionCtx, 'currentContext', {
      get: () => contextSubject.value,
      configurable: true,
    });
    mockService = jasmine.createSpyObj('SubmissionLogService', ['list', 'detailLink']);
    mockService.list.and.returnValue(of(makePage(rows)));
    mockService.detailLink.and.callFake((row: SubmissionLogRow) =>
      ['/' + row.transactionType.toLowerCase(), row.documentId]);

    TestBed.configureTestingModule({
      imports: [SubmissionLogComponent],
      providers: [
        provideRouter([]),
        { provide: SessionContextService, useValue: mockSessionCtx },
        { provide: SubmissionLogService, useValue: mockService },
      ],
    });

    fixture = TestBed.createComponent(SubmissionLogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('renders the transaction-type indicator for each row', () => {
    setup([makeRow({ transactionType: 'SIMPLIFIED' }), makeRow({ attemptId: 'a2', transactionType: 'STANDARD' })],
      makeContext());

    expect(component.rows.length).toBe(2);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('SIMPLIFIED');
    expect(text).toContain('STANDARD');
  });

  it('links each row to its class-correct detail route', () => {
    setup([makeRow({ transactionType: 'INVOICE', documentId: 'doc-9' })], makeContext());

    expect(component.detailLink(component.rows[0])).toEqual(['/invoice', 'doc-9']);
    expect(mockService.detailLink).toHaveBeenCalled();
  });

  it('shows the Company column only when more than one company is accessible', () => {
    setup([makeRow()], makeContext({ companies: [makeCompany('c1', 'Solo Co')] }));
    expect(component.showCompanyColumn).toBeFalse();
    expect(component.columns).not.toContain('company');

    setup([makeRow()], makeContext());
    expect(component.showCompanyColumn).toBeTrue();
    expect(component.columns).toContain('company');
  });

  it('shows the empty state when no submissions are returned', () => {
    setup([], makeContext());
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('No submissions match the current filters.');
  });
});
