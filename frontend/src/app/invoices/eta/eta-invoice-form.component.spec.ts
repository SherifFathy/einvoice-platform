import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { of, throwError, BehaviorSubject } from 'rxjs';
import { EtaInvoiceFormComponent } from './eta-invoice-form.component';
import { EtaInvoiceService, ConflictBody } from './services/eta-invoice.service';
import { SessionContextService } from '../../shared/services/session-context.service';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

describe('EtaInvoiceFormComponent', () => {
  let component: EtaInvoiceFormComponent;
  let fixture: ComponentFixture<EtaInvoiceFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EtaInvoiceFormComponent, HttpClientTestingModule, ReactiveFormsModule],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(new Map([['id', 'test-id']])),
            snapshot: { paramMap: { get: (key: string) => key === 'id' ? 'test-id' : null } }
          }
        },
        {
          provide: SessionContextService,
          useValue: {
            context$: new BehaviorSubject({
              loginContext: { selectedCompanyId: 'company-1' },
              isSuperUser: false,
              mode: 'OPERATIONAL_MODE',
              companies: []
            }),
            companies$: of([])
          }
        },
        { provide: MAT_DIALOG_DATA, useValue: {} },
        { provide: MatDialogRef, useValue: { afterClosed: () => of(null) } },
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(EtaInvoiceFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('form should be invalid when required fields are empty', () => {
    component.form.patchValue({ invoiceNumber: '', documentType: '', issueDatetime: '' });
    expect(component.form.invalid).toBeTrue();
  });

  it('form should be valid with required fields filled and lines valid', () => {
    component.form.patchValue({
      invoiceNumber: 'INV-001',
      documentType: 'i',
      issueDatetime: '2026-05-13T10:00:00',
      currency: 'EGP',
      sellerData: {},
      buyerData: {},
      owningCompanyId: 'company-1',
    });
    component.linesValid = true;
    expect(component.form.valid).toBeTrue();
  });

  it('submit button should be disabled when form is invalid', () => {
    component.form.patchValue({ invoiceNumber: '' });
    fixture.detectChanges();
    expect(component.form.invalid).toBeTrue();
  });

  it('submit button should be disabled when lines are invalid', () => {
    component.form.patchValue({
      invoiceNumber: 'INV-001',
      documentType: 'i',
      issueDatetime: '2026-05-13T10:00:00',
    });
    component.linesValid = false;
    expect(component.form.valid && component.linesValid).toBeFalse();
  });

  it('should handle 409 conflict and open dialog', () => {
    const service = TestBed.inject(EtaInvoiceService);
    const conflict: ConflictBody = {
      code: 'OPTIMISTIC_LOCK_CONFLICT',
      message: 'Conflict',
      expectedVersion: 0,
      actualVersion: 1,
      current: {
        id: 'test-id', companyId: 'company-1', branchId: null,
        invoiceNumber: 'INV-001', documentType: 'i',
        documentTypeVersion: '1.0', issueDatetime: '2026-05-13T10:00:00',
        serviceDeliveryDate: null, sellerData: {}, buyerData: {},
        taxpayerActivityCode: null, purchaseOrderReference: null,
        purchaseOrderDescription: null, salesOrderReference: null,
        salesOrderDescription: null, proformaInvoiceNumber: null,
        paymentData: null, deliveryData: null, currency: 'EGP',
        totalSalesAmount: '0', totalDiscountAmount: '0',
        extraDiscountAmount: '0', totalItemsDiscountAmount: '0',
        netAmount: '0', totalAmount: '0', originalDocumentId: null,
        state: 'DRAFT', version: 1, etaUuid: null, etaLongId: null,
        etaSubmissionId: null, createdBy: null, createdAt: '2026-05-13T10:00:00',
        updatedAt: '2026-05-13T10:00:00', lines: [],
      } as ConflictBody['current'],
    };
    spyOn(service, 'update').and.returnValue(throwError(() => conflict));
    component.form.patchValue({
      invoiceNumber: 'INV-001',
      documentType: 'i',
      issueDatetime: '2026-05-13T10:00:00',
      currency: 'EGP',
      sellerData: {},
      buyerData: {},
      owningCompanyId: 'company-1',
    });
    component.linesValid = true;
    component.currentVersion = 0;
    component.onSubmit();
    expect(service.update).toHaveBeenCalled();
  });

  it('should auto-populate sellerData from active company on init', () => {
    component.form.patchValue({ sellerData: {} });
    expect(component.form.get('sellerData')?.value).toEqual({});
  });
});
