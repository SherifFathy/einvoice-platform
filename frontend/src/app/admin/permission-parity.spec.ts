import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HasPermissionDirective } from '../shared/directives/has-permission.directive';
import { SessionContextService } from '../shared/services/session-context.service';
import { BehaviorSubject } from 'rxjs';
import { SessionContext } from '../shared/services/auth.service';

const DOC_ACTIONS = ['view', 'create', 'edit', 'delete', 'cancel', 'transfer', 'refresh', 'submit'] as const;
const MD_ACTIONS = ['view', 'create', 'edit', 'delete', 'refresh'] as const;
type Action = (typeof DOC_ACTIONS)[number] | (typeof MD_ACTIONS)[number];

type PermSet = {
    view: boolean; create: boolean; edit: boolean; delete: boolean;
    cancel: boolean; transfer: boolean; refresh: boolean; submit: boolean;
};

function allPerms(actions: readonly Action[], grant: boolean): PermSet {
    const result: PermSet = {
        view: false, create: false, edit: false, delete: false,
        cancel: false, transfer: false, refresh: false, submit: false,
    };
    for (const a of actions) {
        (result as Record<string, boolean>)[a] = grant;
    }
    return result;
}

function makeContext(
    moduleKey: string,
    permissions: PermSet,
    isSuperUser = false,
    mode = 'OPERATIONAL_MODE' as string,
): SessionContext {
    return {
        userId: 'u1',
        isSuperUser,
        mode,
        activeCompanyId: 'c1',
        loginContext: { authority: 'ETA', environment: 'PREPROD', authorityEnvironmentId: 2 },
        companies: [{
            companyId: 'c1',
            companyNameEn: 'Parity Co',
            companyNameAr: 'شركة التكافؤ',
            isActive: true,
            modules: {
                [moduleKey]: {
                    visible: permissions.view,
                    permissions,
                },
            },
        }],
    };
}

describe('Permission Parity — Invoices module (SC-004)', () => {
    const moduleKey = 'invoice';

    function createFixture(action: string, ctx: SessionContext): ComponentFixture<unknown> {
        @Component({
            template: `<button *appHasPermission="['${moduleKey}', '${action}']">${action}</button>`,
            imports: [HasPermissionDirective],
        })
        class Host {}
        const contextSubject = new BehaviorSubject<SessionContext | null>(ctx);
        const mockSessionCtx = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear'], {
            context$: contextSubject.asObservable(),
            get currentContext() { return contextSubject.value; },
        });
        TestBed.configureTestingModule({
            imports: [Host],
            providers: [{ provide: SessionContextService, useValue: mockSessionCtx }],
        });
        const f = TestBed.createComponent(Host);
        f.detectChanges();
        return f;
    }

    it('COMPANY_ADMIN — all 8 doc actions visible', () => {
        const perms = allPerms(DOC_ACTIONS, true);
        const ctx = makeContext(moduleKey, perms);
        for (const action of DOC_ACTIONS) {
            const fixture = createFixture(action.toUpperCase(), ctx);
            const btn = fixture.nativeElement.querySelector('button');
            expect(btn).withContext(`COMPANY_ADMIN should show ${action}`).not.toBeNull();
            TestBed.resetTestingModule();
        }
    });

    it('ACCOUNTANT — VIEW, CREATE, REFRESH, SUBMIT visible; EDIT, DELETE, CANCEL, TRANSFER hidden', () => {
        const perms: PermSet = {
            view: true, create: true, edit: false, delete: false,
            cancel: false, transfer: false, refresh: true, submit: true,
        };
        const ctx = makeContext(moduleKey, perms);
        const visible = ['VIEW', 'CREATE', 'REFRESH', 'SUBMIT'];
        const hidden = ['EDIT', 'DELETE', 'CANCEL', 'TRANSFER'];
        for (const action of visible) {
            const fixture = createFixture(action, ctx);
            expect(fixture.nativeElement.querySelector('button'))
                .withContext(`ACCOUNTANT should show ${action}`).not.toBeNull();
            TestBed.resetTestingModule();
        }
        for (const action of hidden) {
            const fixture = createFixture(action, ctx);
            expect(fixture.nativeElement.querySelector('button'))
                .withContext(`ACCOUNTANT should hide ${action}`).toBeNull();
            TestBed.resetTestingModule();
        }
    });

    it('VIEWER — only VIEW visible', () => {
        const perms: PermSet = {
            view: true, create: false, edit: false, delete: false,
            cancel: false, transfer: false, refresh: false, submit: false,
        };
        const ctx = makeContext(moduleKey, perms);
        for (const action of DOC_ACTIONS) {
            const fixture = createFixture(action.toUpperCase(), ctx);
            const btn = fixture.nativeElement.querySelector('button');
            if (action === 'view') {
                expect(btn).withContext(`VIEWER should show VIEW`).not.toBeNull();
            } else {
                expect(btn).withContext(`VIEWER should hide ${action}`).toBeNull();
            }
            TestBed.resetTestingModule();
        }
    });
});

describe('Permission Parity — Customers module (SC-004)', () => {
    const moduleKey = 'customers';

    function createFixture(action: string, ctx: SessionContext): ComponentFixture<unknown> {
        @Component({
            template: `<button *appHasPermission="['${moduleKey}', '${action}']">${action}</button>`,
            imports: [HasPermissionDirective],
        })
        class Host {}
        const contextSubject = new BehaviorSubject<SessionContext | null>(ctx);
        const mockSessionCtx = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear'], {
            context$: contextSubject.asObservable(),
            get currentContext() { return contextSubject.value; },
        });
        TestBed.configureTestingModule({
            imports: [Host],
            providers: [{ provide: SessionContextService, useValue: mockSessionCtx }],
        });
        const f = TestBed.createComponent(Host);
        f.detectChanges();
        return f;
    }

    it('COMPANY_ADMIN — all 5 master-data actions visible', () => {
        const perms = allPerms(MD_ACTIONS, true);
        const ctx = makeContext(moduleKey, perms);
        for (const action of MD_ACTIONS) {
            const fixture = createFixture(action.toUpperCase(), ctx);
            expect(fixture.nativeElement.querySelector('button'))
                .withContext(`COMPANY_ADMIN should show ${action}`).not.toBeNull();
            TestBed.resetTestingModule();
        }
    });

    it('ACCOUNTANT — VIEW, CREATE, REFRESH visible; EDIT, DELETE hidden', () => {
        const perms: PermSet = {
            view: true, create: true, edit: false, delete: false,
            cancel: false, transfer: false, refresh: true, submit: false,
        };
        const ctx = makeContext(moduleKey, perms);
        const visible = ['VIEW', 'CREATE', 'REFRESH'];
        const hidden = ['EDIT', 'DELETE'];
        for (const action of visible) {
            const fixture = createFixture(action, ctx);
            expect(fixture.nativeElement.querySelector('button'))
                .withContext(`ACCOUNTANT should show ${action}`).not.toBeNull();
            TestBed.resetTestingModule();
        }
        for (const action of hidden) {
            const fixture = createFixture(action, ctx);
            expect(fixture.nativeElement.querySelector('button'))
                .withContext(`ACCOUNTANT should hide ${action}`).toBeNull();
            TestBed.resetTestingModule();
        }
    });

    it('no VIEW permission — module invisible, all actions hidden', () => {
        const perms: PermSet = {
            view: false, create: false, edit: false, delete: false,
            cancel: false, transfer: false, refresh: false, submit: false,
        };
        const ctx = makeContext(moduleKey, perms);
        for (const action of MD_ACTIONS) {
            const fixture = createFixture(action.toUpperCase(), ctx);
            expect(fixture.nativeElement.querySelector('button'))
                .withContext(`no VIEW should hide ${action}`).toBeNull();
            TestBed.resetTestingModule();
        }
    });
});

describe('Permission Parity — Super User bypass (SC-004)', () => {
    function createFixture(moduleKey: string, action: string, isSuperUser: boolean, mode: string): ComponentFixture<unknown> {
        @Component({
            template: `<button *appHasPermission="['${moduleKey}', '${action}']">${action}</button>`,
            imports: [HasPermissionDirective],
        })
        class Host {}
        const ctx: SessionContext = {
            userId: 'u1',
            isSuperUser,
            mode,
            activeCompanyId: null,
            loginContext: { authority: 'ETA', environment: 'PREPROD', authorityEnvironmentId: 2 },
            companies: [],
        };
        const contextSubject = new BehaviorSubject<SessionContext | null>(ctx);
        const mockSessionCtx = jasmine.createSpyObj('SessionContextService', ['loadContext', 'clear'], {
            context$: contextSubject.asObservable(),
            get currentContext() { return contextSubject.value; },
        });
        TestBed.configureTestingModule({
            imports: [Host],
            providers: [{ provide: SessionContextService, useValue: mockSessionCtx }],
        });
        const f = TestBed.createComponent(Host);
        f.detectChanges();
        return f;
    }

    it('Super User OPERATIONAL_MODE — all actions visible regardless of companies', () => {
        for (const action of DOC_ACTIONS) {
            const fixture = createFixture('invoice', action.toUpperCase(), true, 'OPERATIONAL_MODE');
            expect(fixture.nativeElement.querySelector('button'))
                .withContext(`SU OPERATIONAL should show ${action}`).not.toBeNull();
            TestBed.resetTestingModule();
        }
    });

    it('Super User ADMIN_MODE — actions hidden (no company context)', () => {
        const fixture = createFixture('invoice', 'CREATE', true, 'ADMIN_MODE');
        expect(fixture.nativeElement.querySelector('button'))
            .withContext(`SU ADMIN_MODE should hide actions`).toBeNull();
        TestBed.resetTestingModule();
    });
});
