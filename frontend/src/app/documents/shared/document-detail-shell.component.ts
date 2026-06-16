import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { ArtifactDownloadComponent } from './artifact-download.component';
import { SubmissionAttemptView, SubmissionHistoryComponent } from './submission-history.component';
import { StatusBadgeComponent, STATUS_COLORS } from '../../shared/components/status-badge/status-badge.component';

export interface DocumentDetailTile {
  label: string;
  value: string | number | null | undefined;
  emphasis?: boolean;
}

@Component({
  selector: 'app-document-detail-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatTabsModule,
    ArtifactDownloadComponent,
    SubmissionHistoryComponent,
    StatusBadgeComponent,
  ],
  template: `
    <section class="dd-shell" [class.dd-shell--eta]="authority === 'ETA'" [class.dd-shell--zatca]="authority === 'ZATCA'">
      <a mat-button class="dd-back-link" [routerLink]="backLink">
        <mat-icon>arrow_back</mat-icon>
        Back to {{ backLabel }}
      </a>

      <mat-card class="dd-hero">
        <div class="dd-hero__accent"></div>
        <div class="dd-hero__main">
          <div class="dd-hero__copy">
            <div class="dd-eyebrow">{{ eyebrow }}</div>
            <div class="dd-title-row">
              <h1>{{ documentNumber }}</h1>
              <app-status-badge [status]="status"></app-status-badge>
              <span *ngIf="secondaryStatus" class="dd-secondary-status">
                <span>{{ secondaryStatusLabel }}</span>
                <app-status-badge [status]="secondaryStatus"></app-status-badge>
              </span>
            </div>
            <div class="dd-subline">{{ subline }}</div>
          </div>

          <div class="dd-actions" aria-label="Document actions">
            <ng-content select="[actions]"></ng-content>
          </div>
        </div>
        <div class="dd-status-rule" [ngStyle]="statusRuleStyle"></div>
      </mat-card>

      <div class="dd-tiles" *ngIf="visibleTiles.length">
        <article *ngFor="let tile of visibleTiles" class="dd-tile" [class.dd-tile--emphasis]="tile.emphasis">
          <span>{{ tile.label }}</span>
          <strong>{{ tile.value }}</strong>
        </article>
      </div>

      <mat-card class="dd-body-card">
        <mat-tab-group animationDuration="0ms" mat-stretch-tabs="false">
          <mat-tab label="Overview">
            <div class="dd-tab-panel">
              <ng-content select="[tab-overview]"></ng-content>
            </div>
          </mat-tab>
          <mat-tab [label]="'Line Items (' + lineCount + ')'">
            <div class="dd-tab-panel">
              <ng-content select="[tab-lines]"></ng-content>
            </div>
          </mat-tab>
          <mat-tab [label]="'Submission History (' + submissions.length + ')'">
            <div class="dd-tab-panel">
              <app-submission-history [attempts]="submissions"></app-submission-history>
              <div *ngIf="!submissions.length" class="dd-empty-state">
                <mat-icon>history</mat-icon>
                <span>No submission attempts yet.</span>
              </div>
            </div>
          </mat-tab>
          <mat-tab label="Artifacts">
            <div class="dd-tab-panel">
              <app-artifact-download
                [artifactTypes]="artifactTypes"
                [companyId]="companyId"
                [docId]="docId"
                [getArtifactUrl]="getArtifactUrl || emptyArtifactUrl">
              </app-artifact-download>
            </div>
          </mat-tab>
        </mat-tab-group>
      </mat-card>
    </section>
  `,
  styleUrls: ['./document-detail-shell.component.scss'],
})
export class DocumentDetailShellComponent {
  @Input() authority: 'ETA' | 'ZATCA' = 'ETA';
  @Input() eyebrow = '';
  @Input() documentNumber = '';
  @Input() status = '';
  @Input() secondaryStatus?: string | null;
  @Input() secondaryStatusLabel = '';
  @Input() subline = '';
  @Input() backLink: unknown[] | string = [];
  @Input() backLabel = '';
  @Input() tiles: DocumentDetailTile[] = [];
  @Input() lineCount = 0;
  @Input() submissions: SubmissionAttemptView[] = [];
  @Input() artifactTypes: string[] = [];
  @Input() companyId = '';
  @Input() docId = '';
  @Input() getArtifactUrl: ((type: string) => string) | null = null;

  emptyArtifactUrl = (): string => '';

  get visibleTiles(): DocumentDetailTile[] {
    return this.tiles.filter(tile => tile.value !== null
        && tile.value !== undefined
        && String(tile.value).trim() !== '');
  }

  get statusRuleStyle(): Record<string, string> {
    const colors = STATUS_COLORS[this.status] ?? STATUS_COLORS['DRAFT'];
    return { 'background-color': colors.text };
  }
}
