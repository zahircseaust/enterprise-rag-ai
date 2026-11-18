package com.example.rag.chat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.rag.web.NotFoundException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ConversationRepository conversations;

    public ChatService(ChatClient ragChatClient, ChatMemory chatMemory, ConversationRepository conversations) {
        this.chatClient = ragChatClient;
        this.chatMemory = chatMemory;
        this.conversations = conversations;
    }

    public ChatAnswer ask(String tenantId, ChatRequest request) {
        UUID conversationId = resolveConversation(tenantId, request.conversationId());

        ChatClientResponse response = prompt(tenantId, conversationId, request)
                .call()
                .chatClientResponse();

        String answer = response.chatResponse() == null ? ""
                : response.chatResponse().getResult().getOutput().getText();

        return new ChatAnswer(conversationId, answer, sources(response.context()));
    }

    public Flux<String> stream(String tenantId, UUID conversationId, ChatRequest request) {
        return prompt(tenantId, conversationId, request).stream().content();
    }

    /** Validates ownership of an existing conversation, or creates a new one. */
    public UUID resolveConversation(String tenantId, UUID requested) {
        if (requested == null) {
            return conversations.create(tenantId);
        }
        String owner = conversations.findTenant(requested)
                .orElseThrow(() -> new NotFoundException("Conversation " + requested + " not found"));
        if (!owner.equals(tenantId)) {
            // Don't reveal that the conversation exists for another tenant.
            throw new NotFoundException("Conversation " + requested + " not found");
        }
        return requested;
    }

    public void deleteConversation(String tenantId, UUID conversationId) {
        resolveConversation(tenantId, conversationId);
        chatMemory.clear(conversationId.toString());
        conversations.delete(conversationId);
    }

    private ChatClient.ChatClientRequestSpec prompt(String tenantId, UUID conversationId, ChatRequest request) {
        String filter = buildFilter(tenantId, request.documentIds());
        return chatClient.prompt()
                .user(request.question())
                .advisors(a -> a
                        .param(ChatMemory.CONVERSATION_ID, conversationId.toString())
                        .param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filter));
    }

    /**
     * Tenant isolation happens here: every similarity search is restricted to the
     * caller's tenant. Inputs are pre-validated (tenant regex, UUID types), so the
     * string is injection-safe.
     */
    static String buildFilter(String tenantId, List<UUID> documentIds) {
        String filter = "tenantId == '" + tenantId + "'";
        if (documentIds != null && !documentIds.isEmpty()) {
            String ids = documentIds.stream()
                    .filter(Objects::nonNull)
                    .map(id -> "'" + id + "'")
                    .collect(Collectors.joining(", "));
            filter += " && documentId in [" + ids + "]";
        }
        return filter;
    }

    @SuppressWarnings("unchecked")
    private static List<ChatAnswer.Source> sources(Map<String, Object> context) {
        Object raw = context.get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return ((List<Document>) list).stream()
                .map(d -> new ChatAnswer.Source(
                        String.valueOf(d.getMetadata().get("documentId")),
                        String.valueOf(d.getMetadata().get("filename")),
                        d.getMetadata().get("chunkIndex"),
                        d.getScore(),
                        excerpt(d.getText())))
                .toList();
    }

    private static String excerpt(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= 300 ? clean : clean.substring(0, 300) + "…";
    }
}
