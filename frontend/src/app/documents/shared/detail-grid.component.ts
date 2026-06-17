import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface DetailGridRow {
  label: string;
  value: string | number | null | undefined;
  mono?: boolean;
}

@Component({
  selector: 'app-detail-grid',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <dl class="detail-grid">
      <ng-container *ngFor="let row of visibleRows">
        <div class="detail-grid__row" [class.detail-grid__row--mono]="row.mono">
          <dt>{{ row.label }}</dt>
          <dd>
            <span>{{ row.value }}</span>
            <button *ngIf="row.mono"
                    mat-icon-button
                    type="button"
                    class="detail-grid__copy"
                    matTooltip="Copy"
                    [attr.aria-label]="'Copy ' + row.label"
                    (click)="copy(row.value)">
              <mat-icon>content_copy</mat-icon>
            </button>
          </dd>
        </div>
      </ng-container>
    </dl>
  `,
  styleUrls: ['./detail-grid.component.scss'],
})
export class DetailGridComponent {
  @Input() rows: DetailGridRow[] = [];

  get visibleRows(): DetailGridRow[] {
    return this.rows.filter(row => row.value !== null
        && row.value !== undefined
        && String(row.value).trim() !== '');
  }

  copy(value: string | number | null | undefined): void {
    if (value === null || value === undefined || !navigator?.clipboard) return;
    void navigator.clipboard.writeText(String(value));
  }
}

