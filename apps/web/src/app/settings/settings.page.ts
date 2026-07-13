// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthStore } from '../core/auth/auth.store';

/** Talos.dc.html's "Settings" screen. The profile card uses real AuthStore data (JWT-derived
 * email/role, Section 15). The prototype's notification toggles are omitted -- there's no
 * per-user preference storage in the API to persist them to, and a toggle that resets on every
 * reload would be misleading rather than useful. */
@Component({
  selector: 'app-settings-page',
  imports: [RouterLink],
  templateUrl: './settings.page.html',
  styleUrl: './settings.page.scss',
})
export class SettingsPage {
  protected readonly authStore = inject(AuthStore);

  protected readonly initials = computed(() => {
    const email = this.authStore.email();
    return email ? email.slice(0, 2).toUpperCase() : '--';
  });
}
