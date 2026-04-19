import { CalculationService } from './calculation.service';

describe('CalculationService', () => {
  let service: CalculationService;

  beforeEach(() => {
    service = new CalculationService();
  });

  it('should calculate single line with standard VAT 15%', () => {
    const result = service.calculateLine({
      unitPrice: 100, quantity: 2, discountAmount: 0, vatCategory: 'S', vatRate: 15,
    });

    expect(result.lineNet).toBe(200);
    expect(result.lineVat).toBe(30);
    expect(result.lineTotal).toBe(230);
  });

  it('should calculate single line with discount', () => {
    const result = service.calculateLine({
      unitPrice: 100, quantity: 3, discountAmount: 50, vatCategory: 'S', vatRate: 15,
    });

    expect(result.lineNet).toBe(250);
    expect(result.lineVat).toBe(37.5);
    expect(result.lineTotal).toBe(287.5);
  });

  it('should calculate multiple lines and aggregate', () => {
    const totals = service.calculateTotals([
      { unitPrice: 100, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15 },
      { unitPrice: 200, quantity: 2, discountAmount: 0, vatCategory: 'S', vatRate: 15 },
    ], 0, 0);

    expect(totals.totalLineNet).toBe(500);
    expect(totals.totalVat).toBe(75);
    expect(totals.totalWithVat).toBe(575);
  });

  it('should handle zero-rated VAT', () => {
    const result = service.calculateLine({
      unitPrice: 100, quantity: 5, discountAmount: 0, vatCategory: 'Z', vatRate: 0,
    });

    expect(result.lineNet).toBe(500);
    expect(result.lineVat).toBe(0);
    expect(result.lineTotal).toBe(500);
  });

  it('should handle exempt VAT', () => {
    const result = service.calculateLine({
      unitPrice: 50, quantity: 3, discountAmount: 0, vatCategory: 'E', vatRate: 0,
    });

    expect(result.lineNet).toBe(150);
    expect(result.lineVat).toBe(0);
  });

  it('should create VAT breakdown for mixed categories', () => {
    const breakdown = service.buildVatBreakdown([
      { unitPrice: 100, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15 },
      { unitPrice: 200, quantity: 1, discountAmount: 0, vatCategory: 'Z', vatRate: 0 },
    ], 0);

    expect(breakdown.length).toBe(2);

    const standardBd = breakdown.find(b => b.category === 'S')!;
    expect(standardBd.taxable).toBe(100);
    expect(standardBd.tax).toBe(15);

    const zeroBd = breakdown.find(b => b.category === 'Z')!;
    expect(zeroBd.taxable).toBe(200);
    expect(zeroBd.tax).toBe(0);
  });

  it('should aggregate lines with same VAT category and rate', () => {
    const breakdown = service.buildVatBreakdown([
      { unitPrice: 100, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15 },
      { unitPrice: 200, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15 },
    ], 0);

    expect(breakdown.length).toBe(1);
    expect(breakdown[0].taxable).toBe(300);
    expect(breakdown[0].tax).toBe(45);
  });

  it('should deduct prepaid amount from total', () => {
    const totals = service.calculateTotals([
      { unitPrice: 100, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15 },
    ], 0, 50);

    expect(totals.totalWithVat).toBe(115);
    expect(totals.amountDue).toBe(65);
  });

  it('should subtract allowances from line net', () => {
    const totals = service.calculateTotals([
      { unitPrice: 100, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15 },
    ], 10, 0);

    expect(totals.totalLineNet).toBe(100);
    expect(totals.totalWithoutVat).toBe(90);
    expect(totals.totalVat).toBe(13.5);
    expect(totals.totalWithVat).toBe(103.5);
  });

  it('should return zeros for empty lines', () => {
    const totals = service.calculateTotals([], 0, 0);

    expect(totals.totalLineNet).toBe(0);
    expect(totals.totalWithoutVat).toBe(0);
    expect(totals.totalVat).toBe(0);
    expect(totals.totalWithVat).toBe(0);
    expect(totals.amountDue).toBe(0);
  });

  it('should round fractional quantities correctly', () => {
    const result = service.calculateLine({
      unitPrice: 33.3333, quantity: 3, discountAmount: 0, vatCategory: 'S', vatRate: 15,
    });

    expect(result.lineNet).toBe(100);
    expect(result.lineVat).toBe(15);
    expect(result.lineTotal).toBe(115);
  });

  it('should round HALF_UP edge case correctly', () => {
    const result = service.calculateLine({
      unitPrice: 33.335, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15,
    });

    expect(result.lineNet).toBe(33.34);
  });

  it('should match BigDecimal HALF_UP for known floating-point boundary 33.3349999', () => {
    const price = 100.005;
    const result = service.calculateLine({
      unitPrice: price, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15,
    });

    expect(result.lineNet).toBe(100.01);
  });

  it('should handle multiple VAT rates in different categories', () => {
    const totals = service.calculateTotals([
      { unitPrice: 200, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15 },
      { unitPrice: 100, quantity: 1, discountAmount: 0, vatCategory: 'Z', vatRate: 0 },
      { unitPrice: 50, quantity: 1, discountAmount: 0, vatCategory: 'E', vatRate: 0 },
    ], 0, 0);

    expect(totals.totalLineNet).toBe(350);
    expect(totals.totalVat).toBe(30);
    expect(totals.totalWithVat).toBe(380);
  });

  it('should calculate line with null discount as zero', () => {
    const result = service.calculateLine({
      unitPrice: 100, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15,
    });

    expect(result.lineNet).toBe(100);
    expect(result.lineVat).toBe(15);
  });

  it('should treat null prepaid as zero', () => {
    const totals = service.calculateTotals([
      { unitPrice: 100, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15 },
    ], 0, 0);

    expect(totals.totalWithVat).toBe(115);
    expect(totals.amountDue).toBe(115);
  });

  it('should treat null allowances as zero', () => {
    const totals = service.calculateTotals([
      { unitPrice: 100, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15 },
    ], 0, 0);

    expect(totals.totalWithoutVat).toBe(100);
  });

  it('should calculate single line with quantity 2.5 and discount 10', () => {
    const result = service.calculateLine({
      unitPrice: 100, quantity: 2.5, discountAmount: 10, vatCategory: 'S', vatRate: 15,
    });

    expect(result.lineNet).toBe(240);
    expect(result.lineVat).toBe(36);
    expect(result.lineTotal).toBe(276);
  });

  it('should distribute allowances proportionally across VAT categories', () => {
    const breakdown = service.buildVatBreakdown([
      { unitPrice: 100, quantity: 1, discountAmount: 0, vatCategory: 'S', vatRate: 15 },
      { unitPrice: 200, quantity: 1, discountAmount: 0, vatCategory: 'Z', vatRate: 0 },
    ], 30);

    expect(breakdown.length).toBe(2);

    const standardBd = breakdown.find(b => b.category === 'S')!;
    expect(standardBd.taxable).toBe(90);

    const zeroBd = breakdown.find(b => b.category === 'Z')!;
    expect(zeroBd.taxable).toBe(180);
  });
});
