import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';

import { ProjectDetail } from '../api';
import { ProjectStore } from './project.store';

@Component({
  selector: 'app-config-panel',
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './config-panel.component.html',
  styleUrl: './config-panel.component.scss',
})
export class ConfigPanelComponent {
  readonly project = input.required<ProjectDetail>();

  private readonly store = inject(ProjectStore);
  private readonly snackBar = inject(MatSnackBar);

  readonly configYaml = signal('');
  readonly syncing = signal(false);
  readonly fieldErrors = signal<Record<string, string> | null>(null);

  readonly configHistory = computed(() => this.project().configHistory ?? []);

  async sync(): Promise<void> {
    const yaml = this.configYaml();
    if (!yaml.trim() || this.syncing()) {
      return;
    }
    this.syncing.set(true);
    this.fieldErrors.set(null);
    try {
      await this.store.syncConfig(this.project().id, { configYaml: yaml });
      this.snackBar.open('talos.yaml synced.', 'Dismiss', { duration: 4000 });
    } catch (err) {
      if (err instanceof HttpErrorResponse && err.status === 422) {
        this.fieldErrors.set(err.error?.error?.details ?? {});
      } else {
        this.snackBar.open('Could not sync talos.yaml.', 'Dismiss', { duration: 4000 });
      }
    } finally {
      this.syncing.set(false);
    }
  }

  readonly fieldErrorEntries = computed(() => Object.entries(this.fieldErrors() ?? {}));
}
