import { TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterTestingModule } from '@angular/router/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AuthComponent } from './auth.component';
import { AuthService } from '../shared/services/auth.service';
import { of, throwError } from 'rxjs';
import { AuthResponse } from '../shared/services/auth.service';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

describe('AuthComponent', () => {
  let component: AuthComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    const spy = jasmine.createSpyObj('AuthService', ['login'], {
      isLoggedIn: () => false,
    });

    await TestBed.configureTestingModule({
      imports: [
        AuthComponent,
        ReactiveFormsModule,
        RouterTestingModule,
        NoopAnimationsModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        MatSelectModule,
        MatButtonModule,
        MatProgressSpinnerModule,
      ],
      providers: [{ provide: AuthService, useValue: spy }],
    }).compileComponents();

    const fixture = TestBed.createComponent(AuthComponent);
    component = fixture.componentInstance;
    authServiceSpy = spy as jasmine.SpyObj<AuthService>;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should start with all LOV controls null and form invalid', () => {
    expect(component.form.get('authority')!.value).toBeNull();
    expect(component.form.get('docType')!.value).toBeNull();
    expect(component.form.get('subEnvironment')!.value).toBeNull();
    expect(component.form.invalid).toBeTrue();
  });

  it('should start with empty docType and subEnv option lists', () => {
    expect(component.docTypeOptions).toEqual([]);
    expect(component.subEnvOptions).toEqual([]);
  });

  it('should populate docType and subEnv options when authority is set', () => {
    component.form.get('authority')!.setValue('ZATCA');
    expect(component.docTypeOptions.length).toBe(1);
    expect(component.docTypeOptions[0].value).toBe('INVOICE');
    expect(component.subEnvOptions.length).toBe(3);

    component.form.get('authority')!.setValue('ETA');
    expect(component.docTypeOptions.length).toBe(2);
    expect(component.subEnvOptions.length).toBe(2);
  });

  it('should clear docType and subEnv when authority changes', () => {
    component.form.get('authority')!.setValue('ZATCA');
    component.form.get('docType')!.setValue('INVOICE');
    component.form.get('subEnvironment')!.setValue('SANDBOX');

    component.form.get('authority')!.setValue('ETA');
    expect(component.form.get('docType')!.value).toBeNull();
    expect(component.form.get('subEnvironment')!.value).toBeNull();
  });

  it('should clear option lists when authority is set to null', () => {
    component.form.get('authority')!.setValue('ZATCA');
    expect(component.docTypeOptions.length).toBeGreaterThan(0);

    component.form.get('authority')!.setValue(null);
    expect(component.docTypeOptions).toEqual([]);
    expect(component.subEnvOptions).toEqual([]);
  });

  it('should keep login button disabled when LOVs are unselected', () => {
    component.form.get('email')!.setValue('test@example.com');
    component.form.get('password')!.setValue('password123');
    expect(component.form.invalid).toBeTrue();
    expect(component.authorityValue).toBeNull();
    expect(component.docTypeValue).toBeNull();
    expect(component.subEnvValue).toBeNull();
  });

  it('should have valid form when all fields are filled', () => {
    component.form.get('authority')!.setValue('ZATCA');
    component.form.get('docType')!.setValue('INVOICE');
    component.form.get('subEnvironment')!.setValue('SANDBOX');
    component.form.get('email')!.setValue('test@example.com');
    component.form.get('password')!.setValue('password123');
    expect(component.form.valid).toBeTrue();
  });

  it('should call authService.login on valid submit', () => {
    authServiceSpy.login.and.returnValue(of({} as AuthResponse));

    component.form.get('authority')!.setValue('ZATCA');
    component.form.get('docType')!.setValue('INVOICE');
    component.form.get('subEnvironment')!.setValue('SANDBOX');
    component.form.get('email')!.setValue('test@example.com');
    component.form.get('password')!.setValue('password123');

    component.onSubmit();
    expect(authServiceSpy.login).toHaveBeenCalledWith(
      'test@example.com', 'password123', 'ZATCA', 'INVOICE', 'SANDBOX'
    );
  });

  it('should not call login when form is invalid', () => {
    component.onSubmit();
    expect(authServiceSpy.login).not.toHaveBeenCalled();
  });

  it('should set errorMessage on login failure', () => {
    authServiceSpy.login.and.returnValue(throwError(() => ({
      error: { error: 'Invalid credentials' },
    })));

    component.form.get('authority')!.setValue('ZATCA');
    component.form.get('docType')!.setValue('INVOICE');
    component.form.get('subEnvironment')!.setValue('SANDBOX');
    component.form.get('email')!.setValue('test@example.com');
    component.form.get('password')!.setValue('password123');

    component.onSubmit();
    expect(component.errorMessage).toBe('Invalid credentials');
    expect(component.submitting).toBeFalse();
  });

  it('should unsubscribe from authority changes on destroy', () => {
    const sub = (component as unknown as { authoritySub: { unsubscribe: () => void } }).authoritySub;
    spyOn(sub, 'unsubscribe');
    component.ngOnDestroy();
    expect(sub.unsubscribe).toHaveBeenCalled();
  });
});
