package xiaozhi.modules.knowledge.dto.document;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;

/**
 * Aggregate DTO for chunk management
 */
@Schema(description = "Aggregate DTO for chunk management")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChunkDTO {

    /**
     * Add chunk request parameters
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Add chunk request parameters")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AddReq implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "Chunk content", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Chunk content cannot be empty")
        private String content;

        @Schema(description = "Important keyword list")
        @JsonProperty("important_keywords")
        private List<String> importantKeywords;

        @Schema(description = "Preset question list")
        private List<String> questions;
    }

    /**
     * Update chunk request parameters
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Update chunk request parameters")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateReq implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "New chunk content")
        private String content;

        @Schema(description = "Updated keyword list (replaces the existing list)")
        @JsonProperty("important_keywords")
        private List<String> importantKeywords;

        @Schema(description = "Enable/disable (true: enabled, false: disabled)")
        private Boolean available;
    }

    /**
     * Get chunk list request parameters
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Get chunk list request parameters")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListReq implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "Page number (default 1)")
        private Integer page;

        @Schema(description = "Page size (default 30)")
        @JsonProperty("page_size")
        private Integer pageSize;

        @Schema(description = "Search keywords (full-text search)")
        private String keywords;

        @Schema(description = "Exact chunk ID")
        private String id;
    }

    /**
     * Batch delete chunks request parameters
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Batch delete chunks request parameters")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RemoveReq implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "Chunk ID list", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("chunk_ids")
        @NotEmpty(message = "Chunk ID list cannot be empty")
        private List<String> chunkIds;
    }

    /**
     * Document chunk information VO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Document chunk information")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InfoVO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "Chunk ID (usually document_id + index)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String id;

        @Schema(description = "Chunk text content (the main target of full-text search)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String content;

        @Schema(description = "Owning document ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("document_id")
        private String documentId;

        @Schema(description = "Document name / keywords")
        @JsonProperty("docnm_kwd")
        private String docnmKwd;

        @Schema(description = "Important keyword list (for keyword-enhanced retrieval)")
        @JsonProperty("important_keywords")
        private List<String> importantKeywords;

        @Schema(description = "Preset question list (for Q&A mode enhancement)")
        private List<String> questions;

        @Schema(description = "Associated image ID")
        @JsonProperty("image_id")
        private String imageId;

        @Schema(description = "Owning knowledge base ID")
        @JsonProperty("dataset_id")
        private String datasetId;

        @Schema(description = "Whether the chunk is available (true: used in retrieval, false: disabled)")
        private Boolean available;

        @Schema(description = "Position indexes of the chunk in the source (RAGFlow returns a nested array, e.g. [[start, end, filename]])")
        private List<List<Object>> positions;

        @Schema(description = "Token ID list")
        @JsonProperty("token")
        private List<Integer> token;
    }

    /**
     * Aggregated chunk list response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Aggregated chunk list response")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListVO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "Chunk information list")
        private List<InfoVO> chunks;

        @Schema(description = "Associated document details")
        private DocumentDTO.InfoVO doc;

        @Schema(description = "Total number of records")
        private Long total;
    }
}
