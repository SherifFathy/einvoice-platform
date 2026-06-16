import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { PlatformBrandingService } from '../../shared/services/platform-branding.service';
import { ToastNotificationService } from '../../shared/services/toast.service';

@Component({
  selector: 'app-branding',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  template: `
    <div class="branding-page">
      <div class="page-header">
        <h2>Branding</h2>
      </div>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Platform logo</mat-card-title>
          <mat-card-subtitle>PNG, JPEG, or WebP. Maximum size 1 MB.</mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          @if (busy) {
            <mat-progress-bar mode="indeterminate"></mat-progress-bar>
          }

          <div class="preview">
            @if (!logoFailed) {
              <img [src]="logoUrl" alt="Platform logo" (error)="logoFailed = true">
            } @else {
              <span>E-Invoice Platform</span>
            }
          </div>

          <div class="actions">
            <input
              #fileInput
              type="file"
              accept="image/png,image/jpeg,image/webp"
              hidden
              (change)="upload($event)"
            >
            <button mat-raised-button color="primary" (click)="fileInput.click()" [disabled]="busy">
              <mat-icon>upload</mat-icon>
              Upload
            </button>
            <button mat-button color="warn" (click)="remove()" [disabled]="busy">
              <mat-icon>delete</mat-icon>
              Remove
            </button>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .branding-page { padding: 16px; max-width: 720px; }
    .page-header { display: flex; align-items: center; margin-bottom: 16px; }
    mat-card-content { display: flex; flex-direction: column; gap: 20px; padding-top: 16px; }
    .preview {
      align-items: center;
      border: 1px dashed #bdbdbd;
      display: flex;
      min-height: 120px;
      justify-content: center;
      padding: 24px;
    }
    .preview img { max-height: 80px; max-width: 100%; object-fit: contain; }
    .preview span { color: #555; font-size: 20px; font-weight: 600; }
    .actions { display: flex; gap: 8px; }
  `],
})
export class BrandingComponent {
  private branding = inject(PlatformBrandingService);
  private toast = inject(ToastNotificationService);

  busy = false;
  logoVersion = Date.now();
  logoFailed = false;

  get logoUrl(): string {
    return this.branding.logoUrl(this.logoVersion);
  }

  upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    this.busy = true;
    this.branding.uploadLogo(file).subscribe({
      next: () => {
        this.logoFailed = false;
        this.logoVersion = Date.now();
        this.branding.refreshLogo();
        this.busy = false;
        this.toast.success('Platform logo updated');
      },
      error: (err) => {
        this.busy = false;
        this.toast.error(err?.error?.message || 'Logo upload failed');
      },
    });
  }

  remove(): void {
    this.busy = true;
    this.branding.deleteLogo().subscribe({
      next: () => {
        this.logoFailed = true;
        this.logoVersion = Date.now();
        this.branding.refreshLogo();
        this.busy = false;
        this.toast.success('Platform logo removed');
      },
      error: (err) => {
        this.busy = false;
        this.toast.error(err?.error?.message || 'Logo removal failed');
      },
    });
  }
}
