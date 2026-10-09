package xiaozhi.modules.knowledge.dto.document;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;

/**
 * Aggregate DTO for document management
 */
@Schema(description = "Aggregate DTO for document management")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentDTO {

    /**
     * Upload document request parameters
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Upload document request parameters")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UploadReq implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "Knowledge base ID (owner must be specified)", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("dataset_id")
        @NotBlank(message = "Knowledge base ID cannot be empty")
        private String datasetId;

        @Schema(description = "File name (overrides the original file name if specified)")
        private String name;

        @Schema(description = "Chunking method")
        @JsonProperty("chunk_method")
        private DocumentDTO.InfoVO.ChunkMethod chunkMethod;

        @Schema(description = "Parser parameter configuration")
        @JsonProperty("parser_config")
        private DocumentDTO.InfoVO.ParserConfig parserConfig;

        @Schema(description = "Virtual folder path (default /)")
        @JsonProperty("parent_path")
        private String parentPath;

        @Schema(description = "Metadata fields")
        @JsonProperty("meta")
        private Map<String, Object> metaFields;

        @Schema(description = "File binary stream (supports PDF, DOCX, TXT, MD and other formats)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Upload file cannot be empty")
        private org.springframework.web.multipart.MultipartFile file;
    }

    /**
     * Update document request parameters
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Update document request parameters")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateReq implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "New document name (must include the file extension and cannot change the original type)")
        private String name;

        @Schema(description = "Enabled/disabled status (true: enabled, false: disabled; disabled documents are excluded from retrieval)")
        private Boolean enabled;

        @Schema(description = "New parsing method (changing it resets the parsing status)")
        @JsonProperty("chunk_method")
        private InfoVO.ChunkMethod chunkMethod;

        @Schema(description = "New detailed parser configuration (should be used together with chunk_method)")
        @JsonProperty("parser_config")
        private InfoVO.ParserConfig parserConfig;
    }

    /**
     * Get document list request parameters
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Get document list request parameters")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListReq implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "Page number (default: 1)")
        private Integer page;

        @Schema(description = "Page size (default: 30)")
        @JsonProperty("page_size")
        private Integer pageSize;

        @Schema(description = "Sort field (options: create_time, name, size; default: create_time)")
        private String orderby;

        @Schema(description = "Sort descending (true: newest/largest first; false: oldest/smallest first; default: true)")
        private Boolean desc;

        @Schema(description = "Exact filter: document ID")
        private String id;

        @Schema(description = "Exact filter: full document name (with extension)")
        private String name;

        @Schema(description = "Fuzzy search: document name keyword")
        private String keywords;

        @Schema(description = "Filter: file extension list (e.g. ['pdf', 'docx'])")
        private List<String> suffix;

        @Schema(description = "Filter: run status list")
        private List<InfoVO.RunStatus> run;

        @Schema(description = "Filter: creation time from (timestamp, ms)")
        @JsonProperty("create_time_from")
        private Long createTimeFrom;

        @Schema(description = "Filter: creation time to (timestamp, ms)")
        @JsonProperty("create_time_to")
        private Long createTimeTo;
    }

    /**
     * Batch document operation request parameters (for delete, parse, etc.)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Batch document operation request parameters")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BatchIdReq implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "Document ID list", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("ids") // document_ids could also be supported for compatibility, but here it is consistently called ids
        @JsonAlias("document_ids")
        @NotEmpty(message = "Document ID list cannot be empty")
        private List<String> ids;
    }

    /**
     * Knowledge base document information VO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Knowledge base document information")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InfoVO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "Document ID (unique identifier)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String id;

        @Schema(description = "Document thumbnail URL (Base64 or link)")
        private String thumbnail;

        @Schema(description = "Owning knowledge base ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("dataset_id")
        private String datasetId;

        @Schema(description = "Document parsing method (determines how the document is chunked)")
        @JsonProperty("chunk_method")
        private ChunkMethod chunkMethod;

        @Schema(description = "Associated ETL pipeline ID (if any)")
        @JsonProperty("pipeline_id")
        private String pipelineId;

        @Schema(description = "Detailed document parser configuration")
        @JsonProperty("parser_config")
        private ParserConfig parserConfig;

        @Schema(description = "Source type (e.g. local, s3, url)")
        @JsonProperty("source_type")
        private String sourceType;

        @Schema(description = "Document file type (e.g. pdf, docx, txt)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String type;

        @Schema(description = "Creator user ID")
        @JsonProperty("created_by")
        private String createdBy;

        @Schema(description = "Document name (with extension)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @Schema(description = "File storage path or location identifier")
        private String location;

        @Schema(description = "File size (bytes)")
        private Long size;

        @Schema(description = "Total number of tokens (counted after parsing)")
        @JsonProperty("token_count")
        private Long tokenCount;

        @Schema(description = "Total number of chunks")
        @JsonProperty("chunk_count")
        private Long chunkCount;

        @Schema(description = "Parsing progress (0.0 ~ 1.0, 1.0 means done)")
        private Double progress;

        @Schema(description = "Current progress description or error message")
        @JsonProperty("progress_msg")
        private String progressMsg;

        @Schema(description = "Processing start timestamp (RFC1123 format from RAGFlow)")
        @JsonProperty("process_begin_at")
        private String processBeginAt;

        @Schema(description = "Total processing duration (seconds)")
        @JsonProperty("process_duration")
        private Double processDuration;

        @Schema(description = "Custom metadata fields (key-value pairs)")
        @JsonProperty("meta_fields")
        private Map<String, Object> metaFields;

        @Schema(description = "File extension (without dot)")
        private String suffix;

        @Schema(description = "Document parsing run status")
        private RunStatus run;

        @Schema(description = "Document availability status (1: enabled/normal, 0: disabled/invalid)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String status;

        @Schema(description = "Creation time (timestamp, ms)", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("create_time")
        private Long createTime;

        @Schema(description = "Creation date (RFC1123 format from RAGFlow)")
        @JsonProperty("create_date")
        private String createDate;

        @Schema(description = "Last update time (timestamp, ms)")
        @JsonProperty("update_time")
        private Long updateTime;

        @Schema(description = "Last update date (RFC1123 format from RAGFlow)")
        @JsonProperty("update_date")
        private String updateDate;

        /**
         * Parsing method enum (ChunkMethod)
         */
        public enum ChunkMethod {
            @Schema(description = "General mode: suitable for most plain-text or mixed documents")
            @JsonProperty("naive")
            NAIVE,
            @Schema(description = "Manual mode: lets users edit chunks manually")
            @JsonProperty("manual")
            MANUAL,
            @Schema(description = "Q&A mode: optimized for Q&A-formatted documents")
            @JsonProperty("qa")
            QA,
            @Schema(description = "Table mode: optimized for tabular data such as Excel or CSV")
            @JsonProperty("table")
            TABLE,
            @Schema(description = "Paper mode: optimized for academic paper layouts")
            @JsonProperty("paper")
            PAPER,
            @Schema(description = "Book mode: optimized for book chapter structures")
            @JsonProperty("book")
            BOOK,
            @Schema(description = "Laws mode: optimized for legal text structures")
            @JsonProperty("laws")
            LAWS,
            @Schema(description = "Presentation mode: optimized for PPT and other presentation files")
            @JsonProperty("presentation")
            PRESENTATION,
            @Schema(description = "Picture mode: OCR and description of image content")
            @JsonProperty("picture")
            PICTURE,
            @Schema(description = "One mode: treat the entire document as a single chunk")
            @JsonProperty("one")
            ONE,
            @Schema(description = "Knowledge graph mode: extract entity relationships to build a graph")
            @JsonProperty("knowledge_graph")
            KNOWLEDGE_GRAPH,
            @Schema(description = "Email mode: optimized for email formats")
            @JsonProperty("email")
            EMAIL;
        }

        /**
         * Run status enum (RunStatus)
         */
        public enum RunStatus {
            @Schema(description = "Not started: waiting in the parsing queue")
            @JsonProperty("UNSTART")
            UNSTART,
            @Schema(description = "Running: parsing or indexing")
            @JsonProperty("RUNNING")
            RUNNING,
            @Schema(description = "Cancelled: cancelled manually by the user")
            @JsonProperty("CANCEL")
            CANCEL,
            @Schema(description = "Done: parsed successfully")
            @JsonProperty("DONE")
            DONE,
            @Schema(description = "Failed: an error occurred during parsing")
            @JsonProperty("FAIL")
            FAIL;
        }

        /**
         * Layout recognition model enum
         */
        public enum LayoutRecognize {
            @Schema(description = "Deep document understanding model: suited to complex layouts")
            @JsonProperty("DeepDOC")
            DeepDOC,
            @Schema(description = "Simple rule-based model: suited to plain text")
            @JsonProperty("Simple")
            Simple;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @Schema(description = "Document parser parameter configuration")
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ParserConfig implements Serializable {
            private static final long serialVersionUID = 1L;

            @Schema(description = "Maximum tokens per chunk (suggested: 512, 1024, 2048)")
            @JsonProperty("chunk_token_num")
            private Integer chunkTokenNum;

            @Schema(description = "Segment delimiter (supports escape characters, e.g. \\n)")
            private String delimiter;

            @Schema(description = "Layout recognition model (DeepDOC/Simple)")
            @JsonProperty("layout_recognize")
            private LayoutRecognize layoutRecognize;

            @Schema(description = "Whether to convert Excel to HTML tables")
            @JsonProperty("html4excel")
            private Boolean html4excel;

            @Schema(description = "Number of keywords to auto-extract (0 means none)")
            @JsonProperty("auto_keywords")
            private Integer autoKeywords;

            @Schema(description = "Number of questions to auto-generate (0 means none)")
            @JsonProperty("auto_questions")
            private Integer autoQuestions;

            @Schema(description = "Number of tags to auto-generate")
            @JsonProperty("topn_tags")
            private Integer topnTags;

            @Schema(description = "RAPTOR advanced index configuration")
            private RaptorConfig raptor;

            @Schema(description = "GraphRAG knowledge graph configuration")
            @JsonProperty("graphrag")
            private GraphRagConfig graphRag;

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            @Schema(description = "RAPTOR (recursive abstractive indexing) configuration")
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class RaptorConfig implements Serializable {
                private static final long serialVersionUID = 1L;
                @Schema(description = "Whether to enable RAPTOR indexing")
                @JsonProperty("use_raptor")
                private Boolean useRaptor;
            }

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            @Schema(description = "GraphRAG (graph-enhanced retrieval) configuration")
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class GraphRagConfig implements Serializable {
                private static final long serialVersionUID = 1L;
                @Schema(description = "Whether to enable GraphRAG indexing")
                @JsonProperty("use_graphrag")
                private Boolean useGraphRag;
            }
        }
    }
}
