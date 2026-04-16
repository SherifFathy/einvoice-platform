import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatChipsModule } from '@angular/material/chips';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule, MatChipsModule],
  templateUrl: './status-badge.component.html',
  styleUrls: ['./status-badge.component.scss'],
})
export class StatusBadgeComponent {
  @Input() status = '';
  @Input() statusMap: Record<string, { label: string; color: string }> = {};

  get label(): string {
    return this.statusMap[this.status]?.label ?? this.status;
  }

  get color(): string {
    return this.statusMap[this.status]?.color ?? 'primary';
  }
}
