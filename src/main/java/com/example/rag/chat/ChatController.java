package com.example.rag.chat;

import java.util.Map;
import java.util.UUID;

import com.example.rag.config.Tenants;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatAnswer ask(@RequestHeader(value = Tenants.HEADER, defaultValue = "default") String tenant,
                          @Valid @RequestBody ChatRequest request) {
        return chatService.ask(Tenants.require(tenant), request);
    }

    /**
     * Server-Sent Events: one "meta" event with the conversation id, then "token"
     * events ({"t": "..."}), then a final "done" event.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(
            @RequestHeader(value = Tenants.HEADER, defaultValue = "default") String tenant,
            @Valid @RequestBody ChatRequest request) {
        String tenantId = Tenants.require(tenant);
        UUID conversationId = chatService.resolveConversation(tenantId, request.conversationId());

        Flux<ServerSentEvent<Object>> meta = Flux.just(ServerSentEvent.builder()
                .event("meta").data(Map.of("conversationId", conversationId)).build());

        Flux<ServerSentEvent<Object>> tokens = chatService.stream(tenantId, conversationId, request)
                .map(t -> ServerSentEvent.builder().event("token").data(Map.of("t", t)).build());

        Flux<ServerSentEvent<Object>> done = Flux.just(ServerSentEvent.builder()
                .event("done").data(Map.of()).build());

        return Flux.concat(meta, tokens, done)
                .onErrorResume(e -> Flux.just(ServerSentEvent.builder()
                        .event("error").data(Map.of("message", "Generation failed")).build()));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(
            @RequestHeader(value = Tenants.HEADER, defaultValue = "default") String tenant,
            @PathVariable UUID id) {
        chatService.deleteConversation(Tenants.require(tenant), id);
        return ResponseEntity.noContent().build();
    }
}
