package com.example.rag.config;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final String SYSTEM_PROMPT = """
            You are an enterprise knowledge assistant. Answer questions using ONLY the
            context passages supplied with each question. Be concise and factual.
            When you use a passage, cite it inline as [filename#chunk].
            If the context does not contain the answer, say you could not find it in
            the indexed documents. Never invent facts, figures, or citations.
            """;

    private static final PromptTemplate RAG_TEMPLATE = new PromptTemplate("""
            Context passages (each starts with its source tag):
            ---------------------
            {context}
            ---------------------
            Using only the passages above, answer the question.
            Question: {query}
            """);

    private static final PromptTemplate EMPTY_CONTEXT_TEMPLATE = new PromptTemplate("""
            No relevant passages were found in the indexed documents for this question.
            Tell the user politely that you could not find the answer in their documents
            and suggest uploading a relevant document or rephrasing the question.
            """);

    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository, RagProperties props) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(props.memoryWindow())
                .build();
    }

    @Bean
    TokenTextSplitter tokenTextSplitter(RagProperties props) {
        return TokenTextSplitter.builder()
                .withChunkSize(props.chunkSize())
                .withMinChunkSizeChars(props.minChunkSizeChars())
                .withMinChunkLengthToEmbed(props.minChunkLengthToEmbed())
                .withMaxNumChunks(props.maxNumChunks())
                .withKeepSeparator(true)
                .build();
    }

    @Bean
    ChatClient ragChatClient(ChatClient.Builder builder,
                             VectorStore vectorStore,
                             ChatMemory chatMemory,
                             RagProperties props) {

        var retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(props.topK())
                .similarityThreshold(props.similarityThreshold())
                .build();

        var augmenter = ContextualQueryAugmenter.builder()
                .promptTemplate(RAG_TEMPLATE)
                .emptyContextPromptTemplate(EMPTY_CONTEXT_TEMPLATE)
                .allowEmptyContext(false)
                .documentFormatter(AiConfig::formatContext)
                .build();

        var ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(augmenter);

        if (props.queryRewrite()) {
            // One extra LLM call that turns chatty questions into precise search queries.
            ragAdvisor.queryTransformers(RewriteQueryTransformer.builder()
                    .chatClientBuilder(builder.clone())
                    .build());
        }

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        ragAdvisor.build(),
                        new SimpleLoggerAdvisor())
                .build();
    }

    private static String formatContext(List<Document> documents) {
        return documents.stream()
                .map(d -> {
                    Map<String, Object> md = d.getMetadata();
                    return "[%s#%s]%n%s".formatted(md.getOrDefault("filename", "unknown"),
                            md.getOrDefault("chunkIndex", "?"), d.getText());
                })
                .collect(Collectors.joining("\n\n"));
    }
}
