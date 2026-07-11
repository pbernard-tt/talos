import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatChipsModule } from '@angular/material/chips';

import { Approval } from '../api';

@Component({
  selector: 'app-approval-card',
  imports: [RouterLink, MatChipsModule],
  templateUrl: './approval-card.component.html',
  styleUrl: './approval-card.component.scss',
})
export class ApprovalCardComponent {
  @Input({ required: true }) approval!: Approval;
}
