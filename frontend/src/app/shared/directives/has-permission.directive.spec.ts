import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HasPermissionDirective } from './has-permission.directive';
import { SessionContextService } from '../services/session-context.service';
import { BehaviorSubject } from 'rxjs';
import { SessionContext } from '../services/auth.service';

@Component({
  template: `<div *appHasPermission="['invoice', 'CREATE']">Visible Content</div>`,
  imports: [HasPermissionDirective],
})
class TestHostComponent {}

describe('HasPermissionDirective', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let contextSubject: BehaviorSubject<SessionContext | null>;
  let mockSessionCtx: jasmine.SpyObj<SessionContextService>;

  const contextWithPermission: SessionContext = {
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
        invoice: {
          visible: true,
          permissions: { view: true, create: true, edit: false, delete: false, cancel: false, transfer: false, refresh: false, submit: false }
        }
      }
    }]
  };

  const contextWithoutPermission: SessionContext = {
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
        invoice: {
          visible: true,
          permissions: { view: true, create: false, edit: false, delete: false, cancel: false, transfer: false, refresh: false, submit: false }
        }
      }
    }]
  };

  beforeEach(() => {
    contextSubject = new BehaviorSubject<SessionContext | null>(contextWithPermission);

    mockSessionCtx = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear'], {
      context$: contextSubject.asObservable(),
    });
    Object.defineProperty(mockSessionCtx, 'currentContext', {
      get: () => contextSubject.value,
      configurable: true,
    });

    TestBed.configureTestingModule({
      imports: [TestHostComponent],
      providers: [
        { provide: SessionContextService, useValue: mockSessionCtx },
      ],
    });

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
  });

  it('should render element when permission is present', () => {
    const el = fixture.nativeElement.querySelector('div');
    expect(el).not.toBeNull();
    expect(el.textContent).toContain('Visible Content');
  });

  it('should remove element when permission is absent', () => {
    contextSubject.next(contextWithoutPermission);
    fixture.detectChanges();

    const el = fixture.nativeElement.querySelector('div');
    expect(el).toBeNull();
  });

  it('should render element for super user in operational mode', () => {
    const suContext: SessionContext = {
      userId: 'u1',
      isSuperUser: true,
      mode: 'OPERATIONAL_MODE',
      loginContext: { authority: 'ETA', environment: 'PREPROD', authorityEnvironmentId: 2 },
      companies: []
    };
    contextSubject.next(suContext);
    fixture.detectChanges();

    const el = fixture.nativeElement.querySelector('div');
    expect(el).not.toBeNull();
  });
});
