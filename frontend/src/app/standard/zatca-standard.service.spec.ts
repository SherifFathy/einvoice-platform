import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ZatcaStandardService } from './services/zatca-standard.service';
import { HttpTestingController } from '@angular/common/http/testing';

describe('ZatcaStandardService', () => {
  let service: ZatcaStandardService;
  let httpMock: HttpTestingController;
  const companyId = '00000000-0000-0000-0000-000000000001';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withFetch()),
        provideHttpClientTesting(),
        ZatcaStandardService,
      ],
    });
    service = TestBed.inject(ZatcaStandardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should list standard documents', () => {
    const mockResult = { items: [], page: 0, size: 50, totalElements: 0 };
    service.list(companyId, { page: 0, size: 50 }).subscribe(result => {
      expect(result).toEqual(mockResult);
    });
    const req = httpMock.expectOne(r => r.url.includes('/zatca/standard'));
    expect(req.request.method).toBe('GET');
    req.flush(mockResult);
  });

  it('should create a standard document', () => {
    const mockBody = { invoiceNumber: 'STD-001', invoiceTypeCode: '388' };
    service.create(companyId, mockBody).subscribe();
    const req = httpMock.expectOne(`/api/companies/${companyId}/zatca/standard`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(mockBody);
    req.flush({ id: '123' }, { headers: { ETag: '"0"' } });
  });

  it('should update with If-Match header', () => {
    const docId = '00000000-0000-0000-0000-000000000002';
    service.update(companyId, docId, { invoiceNumber: 'STD-002' }, '3').subscribe();
    const req = httpMock.expectOne(`/api/companies/${companyId}/zatca/standard/${docId}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('If-Match')).toBe('"3"');
    req.flush({ id: docId }, { headers: { ETag: '"4"' } });
  });

  it('should delete a standard document', () => {
    const docId = '00000000-0000-0000-0000-000000000003';
    service.delete(companyId, docId).subscribe();
    const req = httpMock.expectOne(`/api/companies/${companyId}/zatca/standard/${docId}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should submit a standard document', () => {
    const docId = '00000000-0000-0000-0000-000000000004';
    service.submit(companyId, docId).subscribe();
    const req = httpMock.expectOne(`/api/companies/${companyId}/zatca/standard/${docId}/submit`);
    expect(req.request.method).toBe('POST');
    req.flush({ state: 'ACCEPTED', clearanceStatus: 'CLEARED' });
  });

  it('should build correct artifact URL', () => {
    const docId = '00000000-0000-0000-0000-000000000005';
    const url = service.getArtifactUrl(companyId, docId, 'SIGNED_UBL_XML');
    expect(url).toBe(`/api/companies/${companyId}/zatca/standard/${docId}/artifacts/SIGNED_UBL_XML`);
  });
});
