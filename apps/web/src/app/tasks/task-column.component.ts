import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import { Component, EventEmitter, Input, Output } from '@angular/core';

import { Task } from '../api';
import { TaskCardComponent } from './task-card.component';

@Component({
  selector: 'app-task-column',
  imports: [CdkDrag, CdkDropList, TaskCardComponent],
  templateUrl: './task-column.component.html',
  styleUrl: './task-column.component.scss',
})
export class TaskColumnComponent {
  @Input({ required: true }) title!: string;
  @Input({ required: true }) listId!: string;
  @Input({ required: true }) connectedTo: string[] = [];
  @Input({ required: true }) tasks: Task[] = [];
  @Output() readonly taskDropped = new EventEmitter<CdkDragDrop<Task[]>>();
  @Output() readonly taskClick = new EventEmitter<Task>();
}
