package dev.talos.memory.dto;

import java.util.List;

public record MemorySearchResponse(List<MemorySearchResultResponse> results) {
}
