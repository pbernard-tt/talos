import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatToolbarModule } from '@angular/material/toolbar';

import { RunStore } from './run.store';

const CANCELLABLE_STATUSES = new Set([
  'QUEUED',
  'PREPARING_WORKSPACE',
  'RUNNING_AGENT',
  'RUNNING_TESTS',
  'REVIEWING',
  'WAITING_APPROVAL',
]);

@Component({
  selector: 'app-run-detail-page',
  imports: [
    RouterLink,
    MatButtonModule,
    MatChipsModule,
    MatListModule,
    MatProgressSpinnerModule,
    MatToolbarModule,
  ],
  templateUrl: './run-detail.page.html',
  styleUrl: './run-detail.page.scss',
})
export class RunDetailPage implements OnInit, OnDestroy {
  protected readonly store = inject(RunStore);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);

  private runId: string | null = null;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      return;
    }
    this.runId = id;
    void this.store.load(id);
    this.store.connectLiveUpdates(id);
  }

  ngOnDestroy(): void {
    this.store.clear();
  }

  isCancellable(status: string): boolean {
    return CANCELLABLE_STATUSES.has(status);
  }

  cancel(): void {
    if (!this.runId) {
      return;
    }
    this.store
      .cancel(this.runId)
      .then(() => this.snackBar.open('Run cancelled.', 'Dismiss', { duration: 4000 }))
      .catch(() => this.snackBar.open('Could not cancel the run.', 'Dismiss', { duration: 4000 }));
  }
}
