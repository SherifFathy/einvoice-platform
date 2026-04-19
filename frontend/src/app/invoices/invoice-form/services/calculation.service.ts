import { Injectable } from '@angular/core';

export interface LineCalculation {
  lineNet: number;
  lineVat: number;
  lineTotal: number;
}

export interface VatBreakdownEntry {
  category: string;
  rate: number;
  taxable: number;
  tax: number;
}

export interface DocumentTotals {
  totalLineNet: number;
  totalAllowances: number;
  totalWithoutVat: number;
  totalVat: number;
  totalWithVat: number;
  amountDue: number;
}

export interface LineInput {
  unitPrice: number;
  quantity: number;
  discountAmount: number;
  vatCategory: string;
  vatRate: number;
}

@Injectable({ providedIn: 'root' })
export class CalculationService {
  private static readonly CALC_SCALE = 4;
  private static readonly FINAL_SCALE = 2;

  private round(value: number, scale: number): number {
    const factor = Math.pow(10, scale);
    const corrected = value + Math.sign(value) * Number.EPSILON * Math.abs(value);
    const shifted = corrected * factor;
    const rounded = Math.round(shifted);
    return rounded / factor;
  }

  calculateLine(input: LineInput): LineCalculation {
    const gross = this.round(
      input.unitPrice * input.quantity,
      CalculationService.CALC_SCALE,
    );
    const discount = input.discountAmount || 0;
    const lineNet = this.round(gross - discount, CalculationService.FINAL_SCALE);

    const vatRate = input.vatRate || 0;
    const lineVat = this.round(
      lineNet * (vatRate / 100),
      CalculationService.FINAL_SCALE,
    );

    const lineTotal = this.round(
      lineNet + lineVat,
      CalculationService.FINAL_SCALE,
    );

    return { lineNet, lineVat, lineTotal };
  }

  buildVatBreakdown(
    lines: LineInput[],
    totalAllowances: number,
  ): VatBreakdownEntry[] {
    const map = new Map<string, { category: string; rate: number; taxable: number }>();

    for (const line of lines) {
      const calc = this.calculateLine(line);
      const key = `${line.vatCategory}@${line.vatRate}`;
      const existing = map.get(key);
      if (existing) {
        existing.taxable += calc.lineNet;
      } else {
        map.set(key, {
          category: line.vatCategory,
          rate: line.vatRate || 0,
          taxable: calc.lineNet,
        });
      }
    }

    const result = Array.from(map.values());

    const totalLineNet = result.reduce((sum, bd) => sum + bd.taxable, 0);

    const hasAllowance =
      totalAllowances > 0 && totalLineNet > 0;

    if (hasAllowance) {
      let remainingAllowance = totalAllowances;
      for (let i = 0; i < result.length; i++) {
        const bd = result[i];
        let share: number;
        if (i === result.length - 1) {
          share = remainingAllowance;
        } else {
          share = this.round(
            (bd.taxable * totalAllowances) / totalLineNet,
            CalculationService.CALC_SCALE,
          );
          remainingAllowance -= share;
        }
        bd.taxable -= share;
      }
    }

    const breakdown: VatBreakdownEntry[] = result.map((bd) => {
      const taxable = this.round(bd.taxable, CalculationService.FINAL_SCALE);
      const tax = this.round(
        (taxable * bd.rate) / 100,
        CalculationService.FINAL_SCALE,
      );
      return {
        category: bd.category,
        rate: bd.rate,
        taxable,
        tax,
      };
    });

    return breakdown;
  }

  calculateTotals(
    lines: LineInput[],
    totalAllowances: number,
    prepaidAmount: number,
  ): DocumentTotals {
    if (!lines || lines.length === 0) {
      return {
        totalLineNet: 0,
        totalAllowances: 0,
        totalWithoutVat: 0,
        totalVat: 0,
        totalWithVat: 0,
        amountDue: 0,
      };
    }

    let totalLineNet = 0;
    for (const line of lines) {
      const calc = this.calculateLine(line);
      totalLineNet += calc.lineNet;
    }
    totalLineNet = this.round(totalLineNet, CalculationService.FINAL_SCALE);

    const allowances = totalAllowances || 0;
    const totalWithoutVat = this.round(
      totalLineNet - allowances,
      CalculationService.FINAL_SCALE,
    );

    const breakdown = this.buildVatBreakdown(lines, allowances);
    const totalVat = this.round(
      breakdown.reduce((sum, bd) => sum + bd.tax, 0),
      CalculationService.FINAL_SCALE,
    );

    const totalWithVat = this.round(
      totalWithoutVat + totalVat,
      CalculationService.FINAL_SCALE,
    );

    const prepaid = prepaidAmount || 0;
    const amountDue = this.round(
      totalWithVat - prepaid,
      CalculationService.FINAL_SCALE,
    );

    return {
      totalLineNet,
      totalAllowances: this.round(allowances, CalculationService.FINAL_SCALE),
      totalWithoutVat,
      totalVat,
      totalWithVat,
      amountDue,
    };
  }
}
