package dev.talos.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRejectedSenderRequest(@NotBlank String channel, @NotBlank String chatId) {
}
