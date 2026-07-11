package dev.talos.memory;

import dev.talos.memory.dto.MemorySearchResponse;
import dev.talos.memory.dto.MemorySearchResultResponse;
import dev.talos.memory.dto.MemoryDocumentRequest;
import dev.talos.memory.dto.MemoryDocumentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/projects")
public class InternalMemoryController {

	private final MemoryService memoryService;

	public InternalMemoryController(MemoryService memoryService) {
		this.memoryService = memoryService;
	}

	@GetMapping("/{projectId}/memory/search")
	public MemorySearchResponse search(@PathVariable UUID projectId, @RequestParam String query,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) Integer budgetChars) {
		return new MemorySearchResponse(memoryService.search(projectId, query, limit, budgetChars).stream()
				.map(MemorySearchResultResponse::from)
				.toList());
	}

	@PostMapping("/{projectId}/memory/documents")
	@ResponseStatus(HttpStatus.CREATED)
	public MemoryDocumentResponse ingest(@PathVariable UUID projectId,
			@Valid @RequestBody MemoryDocumentRequest request) {
		return MemoryDocumentResponse.from(memoryService.ingestDocument(projectId, request.sourceType(),
				request.sourceRef(), request.title(), request.content()));
	}
}
