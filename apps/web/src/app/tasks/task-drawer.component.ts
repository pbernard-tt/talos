import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';

import { TaskDetail } from '../api';

@Component({
  selector: 'app-task-drawer',
  imports: [MatButtonModule, MatIconModule, MatListModule],
  templateUrl: './task-drawer.component.html',
  styleUrl: './task-drawer.component.scss',
})
export class TaskDrawerComponent {
  @Input() task: TaskDetail | null = null;
  @Output() readonly close = new EventEmitter<void>();
}
