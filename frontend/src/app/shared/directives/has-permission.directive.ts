import {
  Directive,
  Input,
  TemplateRef,
  ViewContainerRef,
  inject,
  OnDestroy,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { AuthService } from '../services/auth.service';

@Directive({
  selector: '[appHasPermission]',
  standalone: true,
})
export class HasPermissionDirective implements OnDestroy {
  private templateRef = inject(TemplateRef<any>);
  private viewContainer = inject(ViewContainerRef);
  private authService = inject(AuthService);

  private hasView = false;
  private currentPermission = '';
  private sub: Subscription;

  constructor() {
    this.sub = this.authService.currentUser$.subscribe(() => {
      this.updateView(this.currentPermission);
    });
  }

  @Input() set appHasPermission(permission: string) {
    this.currentPermission = permission;
    this.updateView(permission);
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  private updateView(permission: string): void {
    if (!permission) return;
    const show = this.authService.hasPermission(permission);
    if (show && !this.hasView) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.hasView = true;
    } else if (!show && this.hasView) {
      this.viewContainer.clear();
      this.hasView = false;
    }
  }
}
