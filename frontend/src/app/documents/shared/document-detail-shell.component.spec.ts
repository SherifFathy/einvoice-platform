import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { DocumentDetailShellComponent } from './document-detail-shell.component';

describe('DocumentDetailShellComponent', () => {
  let fixture: ComponentFixture<DocumentDetailShellComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DocumentDetailShellComponent, NoopAnimationsModule],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(DocumentDetailShellComponent);
    fixture.componentInstance.authority = 'ZATCA';
    fixture.componentInstance.eyebrow = 'Standard Invoice';
    fixture.componentInstance.documentNumber = 'INV-42';
    fixture.componentInstance.status = 'ACCEPTED';
    fixture.componentInstance.secondaryStatus = 'CLEARED';
    fixture.componentInstance.secondaryStatusLabel = 'Clearance';
    fixture.componentInstance.subline = '2026-06-16 - SAR - ZATCA';
    fixture.componentInstance.backLink = ['/standard'];
    fixture.componentInstance.backLabel = 'Standard invoices';
    fixture.componentInstance.tiles = [
      { label: 'VAT', value: '15.00' },
      { label: 'Payable', value: '115.00', emphasis: true },
    ];
    fixture.detectChanges();
  });

  it('renders hero status and summary tiles', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Standard Invoice');
    expect(text).toContain('INV-42');
    expect(text).toContain('ACCEPTED');
    expect(text).toContain('CLEARED');
    expect(text).toContain('VAT');
    expect(text).toContain('115.00');
  });
});
