package dev.talos.runs.dto;

import java.util.List;

/** Section 10.2: GET /api/v1/runs/{id}/diff -> {files, diff}. */
public record DiffResponse(List<GitChangeResponse> files, String diff) {
}
