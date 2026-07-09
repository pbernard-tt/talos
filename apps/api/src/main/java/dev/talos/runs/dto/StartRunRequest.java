package dev.talos.runs.dto;

/** {@code agentKey}/{@code authMode} default from the project's active talos.yaml when omitted. */
public record StartRunRequest(String agentKey, String authMode) {
}
