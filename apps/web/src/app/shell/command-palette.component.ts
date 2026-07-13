// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { Router } from '@angular/router';

import { ProjectStore } from '../projects/project.store';
import { IconComponent } from '../shared/icon/icon.component';
import { NavItem } from './nav-items';

interface PaletteItem {
  title: string;
  subtitle?: string;
  iconChar: string;
  navigate: () => void;
}

interface PaletteGroup {
  label: string;
  items: PaletteItem[];
}

/** Cmd/Ctrl+K overlay (Talos.dc.html's Command Palette). Searches real, already-loaded data --
 * nav sections and projects -- rather than a fabricated global search index, since no
 * cross-entity search endpoint exists in the API. */
@Component({
  selector: 'app-command-palette',
  imports: [IconComponent],
  templateUrl: './command-palette.component.html',
  styleUrl: './command-palette.component.scss',
})
export class CommandPaletteComponent implements OnInit {
  private readonly router = inject(Router);
  protected readonly projectStore = inject(ProjectStore);

  readonly navItems = input<NavItem[]>([]);
  readonly closed = output<void>();

  protected readonly query = signal('');

  protected readonly groups = computed<PaletteGroup[]>(() => {
    const q = this.query().trim().toLowerCase();
    const matches = (text: string) => !q || text.toLowerCase().includes(q);

    const screens: PaletteItem[] = this.navItems()
      .filter((item) => matches(item.label))
      .map((item) => ({
        title: item.label,
        iconChar: item.label.charAt(0),
        navigate: () => this.go(item.path),
      }));

    const projects: PaletteItem[] = this.projectStore
      .projects()
      .filter((project) => matches(project.name))
      .map((project) => ({
        title: project.name,
        subtitle: project.repoUrl,
        iconChar: 'P',
        navigate: () => this.go(`/projects/${project.id}`),
      }));

    const groups: PaletteGroup[] = [];
    if (screens.length > 0) {
      groups.push({ label: 'Go to', items: screens });
    }
    if (projects.length > 0) {
      groups.push({ label: 'Projects', items: projects });
    }
    return groups;
  });

  protected readonly isEmpty = computed(() => this.groups().every((g) => g.items.length === 0));

  ngOnInit(): void {
    if (this.projectStore.projects().length === 0) {
      void this.projectStore.load();
    }
  }

  setQuery(value: string): void {
    this.query.set(value);
  }

  close(): void {
    this.closed.emit();
  }

  private go(path: string): void {
    void this.router.navigateByUrl(path);
    this.close();
  }
}
