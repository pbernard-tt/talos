package dev.talos.chat;

import dev.talos.audit.AuditService;
import dev.talos.auth.AuthenticatedUser;
import dev.talos.chat.dto.ChatRejectedSenderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Section 16 Phase 12 Track B: talos-api is the sole PostgreSQL writer, so a chat trigger adapter
 * (which has no database connection, only this JWT-authenticated REST client role) cannot itself
 * write the "message from any other chat ID ... produces ... an audit row" acceptance criterion.
 * This is that one write, exposed only to the same integration-scoped service accounts that create
 * tasks -- it records provenance, nothing else, and never identifies the actual disallowed sender's
 * message contents (chatId only).
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

	private final AuditService auditService;

	public ChatController(AuditService auditService) {
		this.auditService = auditService;
	}

	@PostMapping("/rejected-sender")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void rejectedSender(@Valid @RequestBody ChatRejectedSenderRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		auditService.record(principal.id(), "chat.rejected_sender", "chat_channel", null,
				Map.of("channel", request.channel(), "chatId", request.chatId()));
	}
}
