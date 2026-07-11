package dev.talos.memory;

import dev.talos.common.ApiException;
import dev.talos.common.UuidV7;
import dev.talos.projects.ProjectConfig;
import dev.talos.projects.ProjectConfigRepository;
import dev.talos.projects.ProjectRepository;
import dev.talos.runs.AgentRun;
import dev.talos.runs.GitChangeRepository;
import dev.talos.tasks.Task;
import dev.talos.tasks.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MemoryService {

	private static final int DEFAULT_SEARCH_LIMIT = 8;
	private static final int DEFAULT_BUDGET_CHARS = 4000;
	private static final int MAX_DIFF_DIGEST_CHARS = 12000;

	private final MemoryDocumentRepository memoryDocumentRepository;
	private final ProjectRepository projectRepository;
	private final ProjectConfigRepository projectConfigRepository;
	private final TaskRepository taskRepository;
	private final GitChangeRepository gitChangeRepository;
	private final JdbcTemplate jdbcTemplate;
	private final MemoryChunker chunker;
	private final EmbeddingProvider embeddingProvider;
	private final MemorySecretMasker secretMasker;

	public MemoryService(MemoryDocumentRepository memoryDocumentRepository, ProjectRepository projectRepository,
			ProjectConfigRepository projectConfigRepository, TaskRepository taskRepository,
			GitChangeRepository gitChangeRepository, JdbcTemplate jdbcTemplate, MemoryChunker chunker,
			EmbeddingProvider embeddingProvider, MemorySecretMasker secretMasker) {
		this.memoryDocumentRepository = memoryDocumentRepository;
		this.projectRepository = projectRepository;
		this.projectConfigRepository = projectConfigRepository;
		this.taskRepository = taskRepository;
		this.gitChangeRepository = gitChangeRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.chunker = chunker;
		this.embeddingProvider = embeddingProvider;
		this.secretMasker = secretMasker;
	}

	@Transactional
	public MemoryDocument ingestDocument(UUID projectId, MemorySourceType sourceType, String sourceRef, String title,
			String content) {
		projectRepository.findById(projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found"));
		String masked = secretMasker.mask(content);
		if (masked.isBlank()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "EMPTY_MEMORY_DOCUMENT",
					"Memory document content cannot be blank");
		}
		String normalizedSourceRef = sourceRef == null || sourceRef.isBlank() ? sourceType.name().toLowerCase() : sourceRef;
		String normalizedTitle = title == null || title.isBlank() ? normalizedSourceRef : title;
		String contentHash = sha256(masked);
		return memoryDocumentRepository
				.findByProjectIdAndSourceTypeAndSourceRefAndContentHash(projectId, sourceType,
						normalizedSourceRef, contentHash)
				.orElseGet(() -> createDocument(projectId, sourceType, normalizedSourceRef, normalizedTitle, masked,
						contentHash));
	}

	@Transactional
	public void ingestCompletedRun(AgentRun run) {
		if (!memoryEnabled(run.getProjectId())) {
			return;
		}
		Task task = taskRepository.findById(run.getTaskId()).orElse(null);
		String title = task == null ? "Completed run %s".formatted(run.getId()) : task.getTitle();
		StringBuilder content = new StringBuilder();
		content.append("Completed Talos run ").append(run.getId()).append('\n');
		if (task != null) {
			content.append("Task: ").append(task.getTitle()).append('\n');
			if (task.getDescription() != null && !task.getDescription().isBlank()) {
				content.append("Task description:\n").append(task.getDescription()).append('\n');
			}
		}
		if (run.getSummary() != null && !run.getSummary().isBlank()) {
			content.append("Run summary: ").append(run.getSummary()).append('\n');
		}
		var changes = gitChangeRepository.findByRunId(run.getId());
		if (!changes.isEmpty()) {
			content.append("Changed files:\n");
			for (var change : changes) {
				content.append("- ").append(change.getFilePath())
						.append(" (").append(change.getChangeType()).append(", +")
						.append(change.getAdditions()).append("/-").append(change.getDeletions()).append(")\n");
			}
		}
		if (run.getDiffPatch() != null && !run.getDiffPatch().isBlank()) {
			content.append("Diff digest:\n")
					.append(run.getDiffPatch(), 0, Math.min(run.getDiffPatch().length(), MAX_DIFF_DIGEST_CHARS));
		}
		ingestDocument(run.getProjectId(), MemorySourceType.RUN_SUMMARY, run.getId().toString(), title,
				content.toString());
	}

	public List<MemorySearchResult> search(UUID projectId, String query, Integer limit, Integer budgetChars) {
		projectRepository.findById(projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found"));
		if (!memoryEnabled(projectId) || query == null || query.isBlank()) {
			return List.of();
		}
		int effectiveLimit = Math.max(1, Math.min(limit == null ? DEFAULT_SEARCH_LIMIT : limit, 20));
		int effectiveBudget = Math.max(0, Math.min(budgetChars == null ? promptBudgetChars(projectId) : budgetChars,
				20000));
		if (effectiveBudget == 0) {
			return List.of();
		}

		String vector = toVectorLiteral(embeddingProvider.embed(query));
		String sql = """
				SELECT c.id AS chunk_id, d.id AS document_id, d.source_type, c.source_ref, d.title, c.content,
				       1 - (c.embedding <=> ?::vector) AS score
				FROM memory_chunks c
				JOIN memory_documents d ON d.id = c.document_id
				WHERE c.project_id = ?
				ORDER BY c.embedding <=> ?::vector
				LIMIT ?
				""";
		List<MemorySearchResult> raw = jdbcTemplate.query(sql,
				(ps) -> {
					ps.setString(1, vector);
					ps.setObject(2, projectId);
					ps.setString(3, vector);
					ps.setInt(4, effectiveLimit);
				},
				(rs, rowNum) -> new MemorySearchResult(
						rs.getObject("chunk_id", UUID.class),
						rs.getObject("document_id", UUID.class),
						MemorySourceType.valueOf(rs.getString("source_type")),
						rs.getString("source_ref"),
						rs.getString("title"),
						rs.getString("content"),
						rs.getDouble("score")));

		List<MemorySearchResult> withinBudget = new ArrayList<>();
		int used = 0;
		for (MemorySearchResult result : raw) {
			if (used >= effectiveBudget) {
				break;
			}
			String content = result.content();
			if (used + content.length() > effectiveBudget) {
				content = content.substring(0, Math.max(0, effectiveBudget - used));
			}
			if (!content.isBlank()) {
				withinBudget.add(new MemorySearchResult(result.chunkId(), result.documentId(), result.sourceType(),
						result.sourceRef(), result.title(), content, result.score()));
				used += content.length();
			}
		}
		return withinBudget;
	}

	public boolean memoryEnabled(UUID projectId) {
		Map<String, Object> memory = memoryConfig(projectId);
		Object enabled = memory.get("enabled");
		return !(enabled instanceof Boolean bool) || bool;
	}

	public int promptBudgetChars(UUID projectId) {
		Map<String, Object> memory = memoryConfig(projectId);
		Object budget = memory.get("prompt_budget_chars");
		if (budget instanceof Number number) {
			return Math.max(0, Math.min(number.intValue(), 20000));
		}
		return DEFAULT_BUDGET_CHARS;
	}

	private Map<String, Object> memoryConfig(UUID projectId) {
		Object memory = projectConfigRepository.findByProjectIdAndActiveTrue(projectId)
				.map(ProjectConfig::getParsedJson)
				.map(parsed -> parsed.get("memory"))
				.orElse(null);
		if (!(memory instanceof Map<?, ?> raw)) {
			return Map.of();
		}
		Map<String, Object> normalized = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : raw.entrySet()) {
			if (entry.getKey() instanceof String key) {
				normalized.put(key, entry.getValue());
			}
		}
		return normalized;
	}

	private MemoryDocument createDocument(UUID projectId, MemorySourceType sourceType, String sourceRef, String title,
			String content, String contentHash) {
		MemoryDocument document = memoryDocumentRepository.saveAndFlush(
				new MemoryDocument(projectId, sourceType, sourceRef, title, content, contentHash));
		List<String> chunks = chunker.chunk(content);
		for (int i = 0; i < chunks.size(); i++) {
			insertChunk(document, i, chunks.get(i));
		}
		return document;
	}

	private void insertChunk(MemoryDocument document, int chunkIndex, String content) {
		jdbcTemplate.update("""
				INSERT INTO memory_chunks (id, document_id, project_id, chunk_index, content, embedding, source_ref)
				VALUES (?, ?, ?, ?, ?, ?::vector, ?)
				""",
				UuidV7.generate(), document.getId(), document.getProjectId(), chunkIndex, content,
				toVectorLiteral(embeddingProvider.embed(content)), document.getSourceRef());
	}

	static String toVectorLiteral(float[] embedding) {
		StringBuilder builder = new StringBuilder("[");
		for (int i = 0; i < embedding.length; i++) {
			if (i > 0) {
				builder.append(',');
			}
			builder.append(Float.toString(embedding[i]));
		}
		return builder.append(']').toString();
	}

	private String sha256(String content) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by the JDK", e);
		}
	}
}
