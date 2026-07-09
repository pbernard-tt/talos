package dev.talos.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** List endpoint envelope (Section 10.1): {"content": [...], "page": 0, "size": 20, "totalElements": n}. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
	}
}
