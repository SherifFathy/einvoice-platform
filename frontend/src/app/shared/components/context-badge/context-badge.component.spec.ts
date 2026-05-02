import { TestBed } from '@angular/core/testing';
import { ContextBadgeComponent } from './context-badge.component';
import { AuthService } from '../../services/auth.service';

describe('ContextBadgeComponent', () => {
  let component: ContextBadgeComponent;
  let authServiceMock: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    const mock = {
      isLoggedIn: jasmine.createSpy('isLoggedIn'),
      getCurrentUser: jasmine.createSpy('getCurrentUser'),
    };

    await TestBed.configureTestingModule({
      imports: [ContextBadgeComponent],
      providers: [{ provide: AuthService, useValue: mock }],
    }).compileComponents();

    const fixture = TestBed.createComponent(ContextBadgeComponent);
    component = fixture.componentInstance;
    authServiceMock = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should return null activeAuthority when no user', () => {
    authServiceMock.getCurrentUser.and.returnValue(null);
    expect(component.activeAuthority).toBeNull();
  });

  it('should return activeAuthority from current user', () => {
    authServiceMock.getCurrentUser.and.returnValue({ activeAuthority: 'ZATCA' });
    expect(component.activeAuthority).toBe('ZATCA');
  });

  it('should title-case activeDocType', () => {
    authServiceMock.getCurrentUser.and.returnValue({ activeDocType: 'INVOICE' });
    expect(component.activeDocType).toBe('Invoice');
  });

  it('should title-case activeSubEnv', () => {
    authServiceMock.getCurrentUser.and.returnValue({ activeSubEnv: 'SANDBOX' });
    expect(component.activeSubEnv).toBe('Sandbox');
  });

  it('should return null docType when user has no doc type', () => {
    authServiceMock.getCurrentUser.and.returnValue({ activeDocType: null });
    expect(component.activeDocType).toBeNull();
  });

  it('should return null subEnv when user has no sub env', () => {
    authServiceMock.getCurrentUser.and.returnValue({ activeSubEnv: null });
    expect(component.activeSubEnv).toBeNull();
  });

  it('should hide badge when not logged in', () => {
    authServiceMock.isLoggedIn.and.returnValue(false);
    authServiceMock.getCurrentUser.and.returnValue(null);
    expect(component.activeAuthority).toBeNull();
    expect(component.activeDocType).toBeNull();
    expect(component.activeSubEnv).toBeNull();
  });

  it('should show badge content when all LOV fields present', () => {
    authServiceMock.isLoggedIn.and.returnValue(true);
    authServiceMock.getCurrentUser.and.returnValue({
      activeAuthority: 'ETA',
      activeDocType: 'RECEIPT',
      activeSubEnv: 'PRODUCTION',
    });
    expect(component.activeAuthority).toBe('ETA');
    expect(component.activeDocType).toBe('Receipt');
    expect(component.activeSubEnv).toBe('Production');
  });
});
