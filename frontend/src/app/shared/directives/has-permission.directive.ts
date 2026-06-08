import {
  Directive,
  Input,
  TemplateRef,
  ViewContainerRef,
  inject,
  OnDestroy,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { SessionContextService } from '../services/session-context.service';

// Document modules whose VIEW (read) access is open to every authenticated user in
// the company-less redesign. Writes (CREATE/EDIT/...) stay per-company.
const DOC_MODULES = new Set(['invoice', 'receipt', 'standard', 'simplified']);

@Directive({
  selector: '[appHasPermission]',
  standalone: true,
})
export class HasPermissionDirective implements OnDestroy {
  private templateRef = inject<TemplateRef<unknown>>(TemplateRef);
  private viewContainer = inject(ViewContainerRef);
  private sessionCtx = inject(SessionContextService);

  private hasView = false;
  private currentModule = '';
  private currentAction = '';
  private sub: Subscription;

  constructor() {
    this.sub = this.sessionCtx.context$.subscribe(() => {
      this.updateView();
    });
  }

  @Input() set appHasPermission(value: string | [string, string]) {
    if (Array.isArray(value)) {
      this.currentModule = value[0] ?? '';
      this.currentAction = value[1] ?? '';
    } else if (typeof value === 'string') {
      if (value.includes('/')) {
        const [mod, action] = value.split('/');
        this.currentModule = mod ?? '';
        this.currentAction = action ?? '';
      } else {
        this.currentAction = value;
        this.currentModule = '';
      }
    }
    this.updateView();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  private updateView(): void {
    if (!this.currentAction) return;

    const ctx = this.sessionCtx.currentContext;
    if (!ctx) {
      if (this.hasView) {
        this.viewContainer.clear();
        this.hasView = false;
      }
      return;
    }

    let show = false;

    if (ctx.isSuperUser
        && (ctx.mode === 'OPERATIONAL_MODE' || ctx.mode === 'AUTHORITY_SCOPED')) {
      show = true;
    } else if (ctx.mode === 'AUTHORITY_SCOPED'
        && this.currentAction.toLowerCase() === 'view'
        && DOC_MODULES.has(this.currentModule.toLowerCase())) {
      show = true;
    } else if (this.currentModule) {
      const moduleKey = this.currentModule.toLowerCase();
      for (const company of ctx.companies) {
        const mod = company.modules[moduleKey];
        if (mod && mod.visible) {
          const key = this.currentAction.toLowerCase() as keyof typeof mod.permissions;
          if (mod.permissions[key]) {
            show = true;
            break;
          }
        }
      }
    } else {
      for (const company of ctx.companies) {
        for (const mod of Object.values(company.modules)) {
          if (mod.visible) {
            const key = this.currentAction.toLowerCase() as keyof typeof mod.permissions;
            if (mod.permissions[key]) {
              show = true;
              break;
            }
          }
        }
        if (show) break;
      }
    }

    if (show && !this.hasView) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.hasView = true;
    } else if (!show && this.hasView) {
      this.viewContainer.clear();
      this.hasView = false;
    }
  }
}
