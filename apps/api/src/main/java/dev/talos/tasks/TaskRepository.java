package dev.talos.tasks;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
	List<Task> findByProjectIdAndStatus(UUID projectId, TaskStatus status);

	Page<Task> findByProjectId(UUID projectId, Pageable pageable);

	Page<Task> findByStatus(TaskStatus status, Pageable pageable);

	Page<Task> findByProjectIdAndStatus(UUID projectId, TaskStatus status, Pageable pageable);
}
