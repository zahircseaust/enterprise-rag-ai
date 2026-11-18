package com.example.rag.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChatServiceFilterTest {

    @Test
    void tenantOnly() {
        assertThat(ChatService.buildFilter("acme", null)).isEqualTo("tenantId == 'acme'");
    }

    @Test
    void tenantAndDocuments() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertThat(ChatService.buildFilter("acme", List.of(a)))
                .isEqualTo("tenantId == 'acme' && documentId in ['" + a + "']");
    }
}
