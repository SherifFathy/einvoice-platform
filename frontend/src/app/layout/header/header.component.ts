import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { AuthService } from '../../shared/services/auth.service';
import { PasswordDialogComponent } from './password-dialog.component';
import { EnvironmentSelectorComponent } from '../../shared/components/environment-selector/environment-selector.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatDialogModule,
    EnvironmentSelectorComponent,
  ],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent {
  protected authService = inject(AuthService);
  private router = inject(Router);
  private dialog = inject(MatDialog);

  get currentUser() {
    return this.authService.getCurrentUser();
  }

  get activeCompanyName(): string {
    const companies = this.currentUser?.availableCompanies ?? [];
    const activeId = this.currentUser?.activeCompanyId;
    return companies.find((c) => c.id === activeId)?.name ?? '';
  }

  get activeEnvironment(): string {
    return this.authService.getActiveEnvironment() ?? '';
  }

  get permittedEnvironments(): string[] {
    return this.currentUser?.permittedEnvironments ?? [];
  }

  switchCompany(companyId: number): void {
    const dialogRef = this.dialog.open(PasswordDialogComponent, {
      width: '360px',
      disableClose: true,
    });

    dialogRef.afterClosed().subscribe((password: string | undefined) => {
      if (!password) return;
      this.authService.switchCompany(companyId, password).subscribe({
        next: () => {
          const currentUrl = this.router.url;
          this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
            this.router.navigateByUrl(currentUrl);
          });
        },
        error: () => {},
      });
    });
  }

  onEnvironmentSelected(): void {
    const currentUrl = this.router.url;
    this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
      this.router.navigateByUrl(currentUrl);
    });
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/auth']),
      error: () => this.router.navigate(['/auth']),
    });
  }
}
