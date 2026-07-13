// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatChipsModule } from '@angular/material/chips';

import { RunSummary } from '../api';

@Component({
  selector: 'app-run-card',
  imports: [RouterLink, MatChipsModule],
  templateUrl: './run-card.component.html',
  styleUrl: './run-card.component.scss',
})
export class RunCardComponent {
  @Input({ required: true }) run!: RunSummary;
}
