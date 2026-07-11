import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';

import { ApprovalCardComponent } from '../dashboard/approval-card.component';
import { ApprovalStore } from './approval.store';

/** Section 10.2's GET /approvals had no UI consumer (review gap #6) -- reviewers previously had to
 * know a run id to reach /review/:runId. Defaults to PENDING so the common case needs no filtering. */
@Component({
  selector: 'app-approval-inbox-page',
  imports: [RouterLink, MatButtonModule, MatProgressSpinnerModule, MatToolbarModule, ApprovalCardComponent],
  templateUrl: './approval-inbox.page.html',
  styleUrl: './approval-inbox.page.scss',
})
export class ApprovalInboxPage implements OnInit {
  protected readonly store = inject(ApprovalStore);
  readonly showAll = signal(false);

  ngOnInit(): void {
    void this.store.list('PENDING');
  }

  togglePendingOnly(): void {
    this.showAll.update((current) => !current);
    void this.store.list(this.showAll() ? undefined : 'PENDING');
  }
}
