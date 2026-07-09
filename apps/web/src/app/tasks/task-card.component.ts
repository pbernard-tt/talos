import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';

import { Task } from '../api';

@Component({
  selector: 'app-task-card',
  imports: [MatChipsModule],
  templateUrl: './task-card.component.html',
  styleUrl: './task-card.component.scss',
})
export class TaskCardComponent {
  @Input({ required: true }) task!: Task;
  @Output() readonly cardClick = new EventEmitter<Task>();

  onClick(): void {
    this.cardClick.emit(this.task);
  }
}
