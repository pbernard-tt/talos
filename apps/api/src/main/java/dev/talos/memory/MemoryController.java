package dev.talos.memory;

import dev.talos.memory.dto.MemoryDocumentRequest;
import dev.talos.memory.dto.MemoryDocumentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/memory")
public class MemoryController {

	private final MemoryService memoryService;

	public MemoryController(MemoryService memoryService) {
		this.memoryService = memoryService;
	}

	@PostMapping("/documents")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('MAINTAINER')")
	public MemoryDocumentResponse ingest(@PathVariable UUID projectId,
			@Valid @RequestBody MemoryDocumentRequest request) {
		return MemoryDocumentResponse.from(memoryService.ingestDocument(projectId, request.sourceType(),
				request.sourceRef(), request.title(), request.content()));
	}
}
