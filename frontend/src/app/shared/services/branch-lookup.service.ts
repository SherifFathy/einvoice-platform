import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BranchLookup {
  id: string;
  companyId: string;
  companyNameEn: string;
  nameEn: string;
  nameAr: string;
  branchCode: string | null;
  isActive: boolean;
}

export interface BranchGroup {
  companyId: string;
  companyNameEn: string;
  branches: BranchLookup[];
}

@Injectable({ providedIn: 'root' })
export class BranchLookupService {
  private http = inject(HttpClient);

  list(): Observable<BranchLookup[]> {
    return this.http.get<BranchLookup[]>('/api/branches');
  }
}

export function groupBranchesByCompany(branches: BranchLookup[]): BranchGroup[] {
  const groups = new Map<string, BranchGroup>();
  for (const branch of branches) {
    const existing = groups.get(branch.companyId);
    if (existing) {
      existing.branches.push(branch);
    } else {
      groups.set(branch.companyId, {
        companyId: branch.companyId,
        companyNameEn: branch.companyNameEn,
        branches: [branch],
      });
    }
  }
  return Array.from(groups.values());
}
