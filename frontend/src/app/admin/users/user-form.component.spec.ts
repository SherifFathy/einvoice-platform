import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { AdminService, UserResponse } from '../../shared/services/admin.service';
import { UserFormComponent, UserFormDialogData } from './user-form.component';

describe('UserFormComponent', () => {
  let component: UserFormComponent;
  let fixture: ComponentFixture<UserFormComponent>;
  let adminService: jasmine.SpyObj<AdminService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<UserFormComponent>>;

  const createData: UserFormDialogData = { mode: 'create' };
  const editData: UserFormDialogData = {
    mode: 'edit',
    user: {
      id: 'user-1',
      name: 'Test User',
      email: 'test@example.com',
      isSuperUser: false,
      isActive: true,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
  };

  function configure(data: UserFormDialogData) {
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    adminService = jasmine.createSpyObj('AdminService', ['createUser', 'updateUser']);

    TestBed.configureTestingModule({
      imports: [UserFormComponent, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: AdminService, useValue: adminService },
      ],
    });

    fixture = TestBed.createComponent(UserFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  describe('create mode', () => {
    beforeEach(() => configure(createData));

    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should have empty form fields', () => {
      expect(component.form.get('name')?.value).toBe('');
      expect(component.form.get('email')?.value).toBe('');
      expect(component.form.get('password')?.value).toBe('');
      expect(component.form.get('isSuperUser')?.value).toBe(false);
    });

    it('should require password in create mode', () => {
      expect(component.form.get('password')?.hasError('required')).toBe(true);
    });

    it('should display EMAIL_ALREADY_EXISTS error inline', () => {
      adminService.createUser.and.returnValue(throwError(() => ({
        error: { code: 'EMAIL_ALREADY_EXISTS', message: 'Email already exists' },
      })));

      component.form.patchValue({
        name: 'Test', email: 'dup@test.com', password: 'pass', isSuperUser: false,
      });
      component.save();

      expect(component.errorMessage).toContain('already exists');
    });

    it('should display LAST_SUPER_USER_PROTECTED error inline', () => {
      adminService.createUser.and.returnValue(throwError(() => ({
        error: { code: 'LAST_SUPER_USER_PROTECTED', message: 'Last super user' },
      })));

      component.form.patchValue({
        name: 'Test', email: 'test@test.com', password: 'pass', isSuperUser: true,
      });
      component.save();

      expect(component.errorMessage).toContain('last super user');
    });

    it('should call createUser and close dialog on success', () => {
      adminService.createUser.and.returnValue(of({} as unknown as UserResponse));

      component.form.patchValue({
        name: 'New', email: 'new@test.com', password: 'pass', isSuperUser: false,
      });
      component.save();

      expect(adminService.createUser).toHaveBeenCalled();
      expect(dialogRef.close).toHaveBeenCalledWith(true);
    });
  });

  describe('edit mode', () => {
    beforeEach(() => configure(editData));

    it('should pre-fill form with user data', () => {
      expect(component.form.get('name')?.value).toBe('Test User');
      expect(component.form.get('email')?.value).toBe('test@example.com');
    });

    it('should not require password in edit mode', () => {
      expect(component.form.get('password')?.hasError('required')).toBeFalse();
    });

    it('should call updateUser with only changed fields', () => {
      adminService.updateUser.and.returnValue(of({} as unknown as UserResponse));

      component.form.patchValue({ name: 'Updated Name' });
      component.save();

      expect(adminService.updateUser).toHaveBeenCalledWith('user-1', jasmine.objectContaining({
        name: 'Updated Name',
      }));
      expect(dialogRef.close).toHaveBeenCalledWith(true);
    });

    it('should display LAST_SUPER_USER_PROTECTED error on edit', () => {
      adminService.updateUser.and.returnValue(throwError(() => ({
        error: { code: 'LAST_SUPER_USER_PROTECTED', message: 'Protected' },
      })));

      component.form.patchValue({ isSuperUser: false });
      component.save();

      expect(component.errorMessage).toContain('last super user');
    });
  });
});
