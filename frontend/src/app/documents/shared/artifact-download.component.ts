import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';

const ARTIFACT_EXTENSIONS: Record<string, string> = {
  SIGNED_JSON: 'json',
  SIGNED_XML: 'xml',
  CLEARED_XML: 'xml',
  QR_CODE: 'png',
  ETA_RESPONSE: 'json',
  ZATCA_RESPONSE: 'json',
};

@Component({
  selector: 'app-artifact-download',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatCardModule],
  template: `
    <mat-card *ngIf="artifactTypes.length" class="artifact-card">
      <mat-card-header>
        <mat-card-title>Artifacts</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <div class="artifact-buttons">
          <button *ngFor="let type of artifactTypes"
                  mat-stroked-button (click)="download(type)">
            <mat-icon>download</mat-icon>
            {{ type }}
          </button>
        </div>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .artifact-card { margin-top: 16px; }
    .artifact-buttons { display: flex; gap: 12px; flex-wrap: wrap; }
    .artifact-buttons button { text-transform: none; }
  `]
})
export class ArtifactDownloadComponent {
  @Input() artifactTypes: string[] = [];
  @Input() companyId = '';
  @Input() docId = '';
  @Input() getArtifactUrl!: (type: string) => string;

  download(type: string): void {
    const url = this.getArtifactUrl ? this.getArtifactUrl(type) :
        `/api/companies/${this.companyId}/eta/invoices/${this.docId}/artifacts/${type}`;
    const ext = ARTIFACT_EXTENSIONS[type] ?? 'bin';
    const link = document.createElement('a');
    link.href = url;
    link.download = `${type.toLowerCase()}.${ext}`;
    link.click();
  }
}
