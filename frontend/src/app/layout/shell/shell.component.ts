import { Component, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { HeaderComponent } from '../header/header.component';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterModule, MatSidenavModule, MatListModule, MatToolbarModule, MatIconModule, HeaderComponent],
  templateUrl: './shell.component.html',
  styleUrls: ['./shell.component.scss'],
})
export class ShellComponent {
  protected authService = inject(AuthService);
}
