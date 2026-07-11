import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatToolbarModule } from '@angular/material/toolbar';

import { Integration, IntegrationCreateRequest } from '../api';
import { AuthStore } from '../core/auth/auth.store';
import { IntegrationFormDialogComponent } from './integration-form-dialog.component';
import { IntegrationStore } from './integration.store';

/** Section 10.2's Integrations endpoints had no UI consumer (review gap #6). OWNER-only end to
 * end -- IntegrationController is class-level @PreAuthorize("hasRole('OWNER')"), so this page
 * doesn't even attempt the load for lower roles. */
@Component({
  selector: 'app-integrations-page',
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatToolbarModule,
  ],
  templateUrl: './integrations.page.html',
  styleUrl: './integrations.page.scss',
})
export class IntegrationsPage implements OnInit {
  protected readonly store = inject(IntegrationStore);
  protected readonly authStore = inject(AuthStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly displayedColumns = ['name', 'type', 'enabled', 'createdAt', 'actions'];

  ngOnInit(): void {
    if (this.authStore.hasRole('OWNER')) {
      void this.store.load();
    }
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open<IntegrationFormDialogComponent, unknown, IntegrationCreateRequest>(
      IntegrationFormDialogComponent,
    );
    dialogRef.afterClosed().subscribe((request) => {
      if (!request) {
        return;
      }
      this.store
        .create(request)
        .then(() => this.snackBar.open(`Integration "${request.name}" created.`, 'Dismiss', { duration: 4000 }))
        .catch(() => this.snackBar.open('Could not create the integration.', 'Dismiss', { duration: 4000 }));
    });
  }

  test(integration: Integration): void {
    this.store
      .test(integration.id)
      .then((result) => this.snackBar.open(result.message, 'Dismiss', { duration: 4000 }))
      .catch(() => this.snackBar.open('Could not test the integration.', 'Dismiss', { duration: 4000 }));
  }
}
