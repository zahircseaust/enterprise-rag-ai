package com.example.rag.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        @DefaultValue("800") @Min(100) int chunkSize,
        @DefaultValue("350") @Min(1) int minChunkSizeChars,
        @DefaultValue("5") @Min(1) int minChunkLengthToEmbed,
        @DefaultValue("10000") @Min(1) int maxNumChunks,
        @DefaultValue("6") @Min(1) int topK,
        @DefaultValue("0.45") @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold,
        @DefaultValue("false") boolean queryRewrite,
        @DefaultValue("20") @Min(2) int memoryWindow) {
}
