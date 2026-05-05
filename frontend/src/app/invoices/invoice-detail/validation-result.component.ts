import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { ValidationItem } from '../../shared/services/invoice.service';

@Component({
  selector: 'app-validation-result',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    @if (errors.length > 0 || warnings.length > 0) {
      <div class="validation-container">
        @for (layer of layers; track layer) {
          @if (getItemsForLayer(layer).length > 0) {
            <div class="layer-section">
              <h5 class="layer-header">{{ layerLabel(layer) }}</h5>
              <div class="validation-items">
                @for (item of getItemsForLayer(layer); track trackItem($index, item)) {
                  <div class="validation-item"
                       [class.error-item]="item.severity === 'ERROR'"
                       [class.warning-item]="item.severity === 'WARNING'">
                    <mat-icon [class.error-icon]="item.severity === 'ERROR'"
                              [class.warning-icon]="item.severity === 'WARNING'">
                      {{ item.severity === 'ERROR' ? 'error' : 'warning' }}
                    </mat-icon>
                    <div class="item-content">
                      <span class="rule-id">{{ item.ruleId }}</span>
                      @if (item.field) {
                        <span class="field-name">{{ item.field }}</span>
                      }
                      <span class="message">{{ item.message }}</span>
                    </div>
                  </div>
                }
              </div>
            </div>
          }
        }
      </div>
    } @else if (checked) {
      <div class="validation-container success-container">
        <mat-icon class="success-icon">check_circle</mat-icon>
        <span>All validations passed</span>
      </div>
    }
  `,
  styles: `
    .validation-container { margin-top: 12px; border: 1px solid #e0e0e0; border-radius: 8px; padding: 12px; }
    .layer-section { margin-bottom: 12px; }
    .layer-section:last-child { margin-bottom: 0; }
    .layer-header { margin: 0 0 6px 0; font-size: 0.9em; color: #333; text-transform: uppercase; letter-spacing: 0.5px; }
    .validation-items { display: flex; flex-direction: column; gap: 4px; }
    .validation-item { display: flex; align-items: flex-start; gap: 6px; padding: 4px 8px; border-radius: 4px; font-size: 0.85em; }
    .error-item { background: #ffebee; }
    .warning-item { background: #fff8e1; }
    .error-icon { color: #d32f2f; font-size: 18px; width: 18px; height: 18px; }
    .warning-icon { color: #f57c00; font-size: 18px; width: 18px; height: 18px; }
    .success-icon { color: #388e3c; }
    .item-content { display: flex; gap: 8px; flex-wrap: wrap; align-items: baseline; }
    .rule-id { font-weight: 600; color: #555; min-width: 70px; }
    .field-name { font-family: monospace; background: rgba(0,0,0,0.06); padding: 1px 4px; border-radius: 3px; font-size: 0.9em; }
    .message { color: #333; }
    .success-container { display: flex; align-items: center; gap: 8px; color: #388e3c; font-weight: 500; }
  `,
})
export class ValidationResultComponent {
  @Input() errors: ValidationItem[] = [];
  @Input() warnings: ValidationItem[] = [];
  @Input() checked = false;

  layers = ['STRUCTURAL', 'ARITHMETIC', 'COMPLIANCE', 'READINESS'];

  getItemsForLayer(layer: string): ValidationItem[] {
    return [...this.errors, ...this.warnings]
      .filter(item => item.layer === layer)
      .sort((a) => (a.severity === 'ERROR' ? -1 : 1));
  }

  layerLabel(layer: string): string {
    switch (layer) {
      case 'STRUCTURAL': return 'Structural';
      case 'ARITHMETIC': return 'Arithmetic';
      case 'COMPLIANCE': return 'Compliance';
      case 'READINESS': return 'Readiness';
      default: return layer;
    }
  }

  trackItem(index: number, item: ValidationItem): string {
    return `${index}:${item.ruleId ?? 'no-rule'}:${item.field ?? 'no-field'}`;
  }
}
