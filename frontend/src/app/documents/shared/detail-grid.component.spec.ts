import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DetailGridComponent } from './detail-grid.component';

describe('DetailGridComponent', () => {
  let fixture: ComponentFixture<DetailGridComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DetailGridComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DetailGridComponent);
  });

  it('skips null, undefined, and empty rows', () => {
    fixture.componentInstance.rows = [
      { label: 'Visible', value: 'INV-1' },
      { label: 'Null', value: null },
      { label: 'Undefined', value: undefined },
      { label: 'Blank', value: '   ' },
      { label: 'Zero', value: 0 },
    ];
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Visible');
    expect(text).toContain('INV-1');
    expect(text).toContain('Zero');
    expect(text).not.toContain('Null');
    expect(text).not.toContain('Undefined');
    expect(text).not.toContain('Blank');
  });
});

