import { Component, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { HeaderComponent } from '../header/header.component';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { SessionContextService } from '../../shared/services/session-context.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterModule, MatSidenavModule, MatToolbarModule, HeaderComponent, SidebarComponent],
  templateUrl: './shell.component.html',
  styleUrls: ['./shell.component.scss'],
})
export class ShellComponent {
  protected sessionCtx = inject(SessionContextService);
}
