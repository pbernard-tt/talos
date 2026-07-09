package dev.talos.projects.dto;

import jakarta.validation.constraints.NotBlank;

public record SyncConfigRequest(@NotBlank String configYaml) {
}
