import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';

import { AuthStore } from '../core/auth/auth.store';
import { ApprovalCardComponent } from './approval-card.component';
import { CommandCenterStore } from './command-center.store';
import { RunCardComponent } from './run-card.component';

/** Section 15 / plan line 961: CommandCenterPage at '/', cards for active runs, approvals waiting,
 * failed builds, recently completed tasks; the DLQ alert from Section 11. */
@Component({
  selector: 'app-command-center-page',
  imports: [RouterLink, MatButtonModule, MatProgressSpinnerModule, MatToolbarModule, ApprovalCardComponent, RunCardComponent],
  templateUrl: './command-center.page.html',
  styleUrl: './command-center.page.scss',
})
export class CommandCenterPage implements OnInit {
  protected readonly store = inject(CommandCenterStore);
  protected readonly authStore = inject(AuthStore);

  ngOnInit(): void {
    void this.store.load();
  }
}
