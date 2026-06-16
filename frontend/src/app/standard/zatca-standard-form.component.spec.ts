import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ZatcaStandardFormComponent } from './zatca-standard-form.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

describe('ZatcaStandardFormComponent', () => {
  let component: ZatcaStandardFormComponent;
  let fixture: ComponentFixture<ZatcaStandardFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ZatcaStandardFormComponent, ReactiveFormsModule,
                NoopAnimationsModule,
        ],
      providers: [
        provideHttpClient(withFetch()),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ZatcaStandardFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have required form controls', () => {
    expect(component.form.contains('invoiceNumber')).toBeTrue();
    expect(component.form.contains('invoiceTypeCode')).toBeTrue();
    expect(component.form.contains('transactionTypeCode')).toBeTrue();
    expect(component.form.contains('issueDate')).toBeTrue();
    expect(component.form.contains('issueTime')).toBeTrue();
    expect(component.form.contains('currency')).toBeTrue();
    expect(component.form.contains('sellerData')).toBeTrue();
    expect(component.form.contains('buyerData')).toBeTrue();
    expect(component.form.contains('lines')).toBeTrue();
  });

  it('should have default currency SAR', () => {
    expect(component.form.get('currency')?.value).toBe('SAR');
  });

  it('should have default invoice type code 388', () => {
    expect(component.form.get('invoiceTypeCode')?.value).toBe('388');
  });

  it('should have default transaction type code 0100000', () => {
    expect(component.form.get('transactionTypeCode')?.value).toBe('0100000');
  });

  it('should not submit when form is invalid', () => {
    component.form.patchValue({ invoiceNumber: '' });
    spyOn(console, 'error');
    component.onSubmit();
    expect(component.form.invalid).toBeTrue();
  });
});
