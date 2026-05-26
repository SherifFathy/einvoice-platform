import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, map, Observable, tap } from 'rxjs';
import type { SessionContext } from './auth.service';

@Injectable({ providedIn: 'root' })
export class SessionContextService {
  private http = inject(HttpClient);
  private readonly apiUrl = '/api/session/context';

  private contextSubject = new BehaviorSubject<SessionContext | null>(null);
  context$ = this.contextSubject.asObservable();

  get currentContext(): SessionContext | null {
    return this.contextSubject.value;
  }

  isSuperUser$: Observable<boolean> = this.context$.pipe(
    map((ctx) => ctx?.isSuperUser ?? false),
  );

  mode$: Observable<string | null> = this.context$.pipe(
    map((ctx) => ctx?.mode ?? null),
  );

  companies$: Observable<SessionContext['companies']> = this.context$.pipe(
    map((ctx) => ctx?.companies ?? []),
  );

  loadContext(): Observable<SessionContext> {
    return this.http.get<SessionContext>(this.apiUrl).pipe(
      tap((ctx) => this.contextSubject.next(ctx)),
    );
  }

  clear(): void {
    this.contextSubject.next(null);
  }
}
