package xiaozhi.modules.knowledge.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import org.springframework.util.CollectionUtils;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.knowledge.dto.KnowledgeFilesDTO;
import xiaozhi.modules.knowledge.dto.document.ChunkDTO;
import xiaozhi.modules.knowledge.dto.document.RetrievalDTO;
import xiaozhi.modules.knowledge.dto.document.DocumentDTO;
import xiaozhi.common.page.PageData;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.knowledge.dao.DocumentDao;
import xiaozhi.modules.knowledge.entity.DocumentEntity;
import xiaozhi.modules.knowledge.rag.KnowledgeBaseAdapter;
import xiaozhi.modules.knowledge.rag.KnowledgeBaseAdapterFactory;
import xiaozhi.modules.knowledge.service.KnowledgeBaseService;
import xiaozhi.modules.knowledge.service.KnowledgeFilesService;

@Service
@Slf4j
public class KnowledgeFilesServiceImpl extends BaseServiceImpl<DocumentDao, DocumentEntity>
        implements KnowledgeFilesService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentDao documentDao;
    private final ObjectMapper objectMapper;
    private final RedisUtils redisUtils;

    public KnowledgeFilesServiceImpl(KnowledgeBaseService knowledgeBaseService,
            DocumentDao documentDao,
            ObjectMapper objectMapper,
            RedisUtils redisUtils) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentDao = documentDao;
        this.objectMapper = objectMapper;
        this.redisUtils = redisUtils;
    }

    @Lazy
    @Autowired
    private KnowledgeFilesService self;

    @Override
    public Map<String, Object> getRAGConfig(String ragModelId) {
        return knowledgeBaseService.getRAGConfig(ragModelId);
    }

    @Override
    public PageData<KnowledgeFilesDTO> getPageList(KnowledgeFilesDTO knowledgeFilesDTO, Integer page, Integer limit) {
        log.info("=== Getting knowledge base document list (Local-First optimized) ===");
        String datasetId = knowledgeFilesDTO.getDatasetId();
        if (StringUtils.isBlank(datasetId)) {
            throw new RenException(ErrorCode.RAG_DATASET_ID_AND_MODEL_ID_NOT_NULL);
        }

        // Full reconciliation: pull remote documents from RAGFlow so remote changes are picked up immediately
        try {
            self.syncDocumentsFromRAG(datasetId);
        } catch (Exception e) {
            log.warn("Full document sync from RAGFlow failed (local query unaffected): datasetId={}, error={}", datasetId, e.getMessage());
        }

        // 1. Get data from the local shadow table (MyBatis-Plus paging)
        Page<DocumentEntity> pageParams = new Page<>(page, limit);
        QueryWrapper<DocumentEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dataset_id", datasetId);
        if (StringUtils.isNotBlank(knowledgeFilesDTO.getName())) {
            queryWrapper.like("name", knowledgeFilesDTO.getName());
        }
        if (StringUtils.isNotBlank(knowledgeFilesDTO.getRun())) {
            queryWrapper.eq("run", knowledgeFilesDTO.getRun());
        }
        if (StringUtils.isNotBlank(knowledgeFilesDTO.getStatus())) {
            queryWrapper.eq("status", knowledgeFilesDTO.getStatus());
        }
        queryWrapper.orderByDesc("created_at");

        // 2. Run the local query
        Page<DocumentEntity> iPage = documentDao.selectPage(pageParams, queryWrapper);

        // 3. Convert to DTOs manually
        List<KnowledgeFilesDTO> dtoList = new ArrayList<>();
        for (DocumentEntity entity : iPage.getRecords()) {
            dtoList.add(convertEntityToDTO(entity));
        }
        PageData<KnowledgeFilesDTO> pageData = new PageData<>(dtoList, iPage.getTotal());

        // 4. Dynamic status sync (with rate limiting and protection)
        // [Bug Fix] P1: widen the sync whitelist; CANCEL/FAIL are also synced at low frequency to allow self-healing
        if (pageData.getList() != null && !pageData.getList().isEmpty()) {
            KnowledgeBaseAdapter adapter = null;
            for (KnowledgeFilesDTO dto : pageData.getList()) {
                String runStatus = dto.getRun();
                // High-priority sync: RUNNING/UNSTART (5-second cooldown)
                boolean isActiveSync = "RUNNING".equals(runStatus) || "UNSTART".equals(runStatus);
                // Low-frequency self-healing sync: CANCEL/FAIL (60-second cooldown), so error states do not stay locked forever
                boolean isRecoverySync = "CANCEL".equals(runStatus) || "FAIL".equals(runStatus);
                boolean needSync = isActiveSync || isRecoverySync;

                if (needSync) {
                    // Rate limiting: 5-second cooldown for active states, 60-second cooldown for self-healing states
                    long cooldownMs = isActiveSync ? 5000 : 60000;
                    DocumentEntity localEntity = documentDao.selectOne(new QueryWrapper<DocumentEntity>()
                            .eq("document_id", dto.getDocumentId()));
                    if (localEntity != null && localEntity.getLastSyncAt() != null) {
                        long diff = System.currentTimeMillis() - localEntity.getLastSyncAt().getTime();
                        if (diff < cooldownMs) {
                            continue;
                        }
                    }

                    // Lazily initialize the adapter, only when a sync is actually needed
                    if (adapter == null) {
                        try {
                            Map<String, Object> ragConfig = knowledgeBaseService.getRAGConfigByDatasetId(datasetId);
                            adapter = KnowledgeBaseAdapterFactory.getAdapter(extractAdapterType(ragConfig), ragConfig);
                        } catch (Exception e) {
                            log.warn("Sync aborted: unable to initialize adapter, {}", e.getMessage());
                            break;
                        }
                    }
                    // [Key fix] Record the token count before sync to compute the delta
                    Long oldTokenCount = dto.getTokenCount() != null ? dto.getTokenCount() : 0L;

                    syncDocumentStatusWithRAG(dto, adapter);

                    // Compute the delta and update knowledge base stats (consistent with the scheduled task)
                    Long newTokenCount = dto.getTokenCount() != null ? dto.getTokenCount() : 0L;
                    Long tokenDelta = newTokenCount - oldTokenCount;
                    if (tokenDelta != 0) {
                        knowledgeBaseService.updateStatistics(datasetId, 0, 0L, tokenDelta);
                        log.info("Lazy sync: corrected knowledge base stats, docId={}, tokenDelta={}", dto.getDocumentId(), tokenDelta);
                    }
                }
            }
        }

        log.info("Got document list, total: {}", pageData.getTotal());
        return pageData;
    }

    /**
     * Convert the local record entity to a DTO, manually aligning mismatched fields (size -> fileSize, type -> fileType)
     */
    private KnowledgeFilesDTO convertEntityToDTO(DocumentEntity entity) {
        if (entity == null) {
            return null;
        }
        KnowledgeFilesDTO dto = new KnowledgeFilesDTO();
        // 1. Copy basic fields
        BeanUtils.copyProperties(entity, dto);

        // Issue 2: fix ID semantics. The frontend uses id as the primary key for operations.
        // In this module, always map the remote documentId to the DTO id so the frontend uses a consistent ID for details/delete etc.
        dto.setId(entity.getDocumentId());

        // 2. Convert the local record entity to a DTO, manually aligning mismatched fields (size -> fileSize, type -> fileType)
        dto.setFileSize(entity.getSize());
        dto.setFileType(entity.getType());
        dto.setRun(entity.getRun());
        dto.setChunkCount(entity.getChunkCount());
        dto.setTokenCount(entity.getTokenCount());
        dto.setError(entity.getError());

        // 3. Deserialize custom metadata JSON (Issue 3)
        if (StringUtils.isNotBlank(entity.getMetaFields())) {
            try {
                dto.setMetaFields(objectMapper.readValue(entity.getMetaFields(),
                        new TypeReference<Map<String, Object>>() {
                        }));
            } catch (Exception e) {
                log.warn("Failed to deserialize MetaFields, entityId: {}, error: {}", entity.getId(), e.getMessage());
            }
        }

        // 4. Deserialize the parser config JSON
        if (StringUtils.isNotBlank(entity.getParserConfig())) {
            try {
                dto.setParserConfig(objectMapper.readValue(entity.getParserConfig(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        }));
            } catch (Exception e) {
                log.warn("Failed to deserialize ParserConfig, entityId: {}, error: {}", entity.getId(), e.getMessage());
            }
        }
        return dto;

    }

    /**
     * Sync document status with the actual RAG status
     * Improves the status sync logic so the parsing state is displayed correctly
     * Only marks the document as done once it has chunks and parsing has taken more than 30 seconds
     */
    /**
     * Sync document status with the actual RAG status (enhanced: accepts an externally provided adapter)
     */
    private void syncDocumentStatusWithRAG(KnowledgeFilesDTO dto, KnowledgeBaseAdapter adapter) {
        if (dto == null || StringUtils.isBlank(dto.getDocumentId()) || adapter == null) {
            return;
        }

        String documentId = dto.getDocumentId();
        String datasetId = dto.getDatasetId();

        try {
            // Use a strongly typed ListReq with an ID filter to get the status
            DocumentDTO.ListReq listReq = DocumentDTO.ListReq.builder()
                    .id(documentId)
                    .page(1)
                    .pageSize(1)
                    .build();

            PageData<KnowledgeFilesDTO> remoteList = adapter.getDocumentList(datasetId, listReq);

            if (remoteList != null && remoteList.getList() != null && !remoteList.getList().isEmpty()) {
                KnowledgeFilesDTO remoteDto = remoteList.getList().get(0);
                String remoteStatus = remoteDto.getStatus();

                // Core status alignment logic
                boolean statusChanged = remoteStatus != null && !remoteStatus.equals(dto.getStatus());
                boolean runChanged = remoteDto.getRun() != null && !remoteDto.getRun().equals(dto.getRun());
                boolean isProcessing = "RUNNING".equals(remoteDto.getRun()) || "UNSTART".equals(remoteDto.getRun());

                // Sync whenever the status or run status changes, or the file is still parsing (to refresh progress live)
                if (statusChanged || runChanged || isProcessing) {
                    log.info("Shadow sync: statusChanged={}, parsing={}, document={}, latestStatus={}, progress={}",
                            statusChanged, isProcessing, documentId, remoteStatus, remoteDto.getProgress());

                    // 1. Sync the in-memory DTO
                    dto.setStatus(remoteStatus);
                    dto.setRun(remoteDto.getRun());
                    dto.setProgress(remoteDto.getProgress());
                    dto.setChunkCount(remoteDto.getChunkCount());
                    dto.setTokenCount(remoteDto.getTokenCount());
                    dto.setError(remoteDto.getError());
                    dto.setProcessDuration(remoteDto.getProcessDuration());
                    dto.setThumbnail(remoteDto.getThumbnail());

                    // 2. Sync the local shadow table
                    UpdateWrapper<DocumentEntity> updateWrapper = new UpdateWrapper<DocumentEntity>()
                            .set("status", remoteStatus)
                            .set("run", remoteDto.getRun())
                            .set("progress", remoteDto.getProgress())
                            .set("chunk_count", remoteDto.getChunkCount())
                            .set("token_count", remoteDto.getTokenCount())
                            .set("error", remoteDto.getError())
                            .set("process_duration", remoteDto.getProcessDuration())
                            .set("thumbnail", remoteDto.getThumbnail())
                            .eq("document_id", documentId)
                            .eq("dataset_id", datasetId);

                    // Serialize metadata for sync
                    if (remoteDto.getMetaFields() != null) {
                        try {
                            updateWrapper.set("meta_fields",
                                    objectMapper.writeValueAsString(remoteDto.getMetaFields()));
                        } catch (Exception e) {
                            log.warn("Failed to serialize metadata for sync: {}", e.getMessage());
                        }
                    }

                    // Prefer the RAG-side update time so local syncs do not overwrite the business modification time
                    Date lastUpdate = remoteDto.getUpdatedAt() != null ? remoteDto.getUpdatedAt() : new Date();
                    updateWrapper.set("updated_at", lastUpdate);
                    updateWrapper.set("last_sync_at", new Date()); // Record the shadow DB sync time

                    documentDao.update(null, updateWrapper);
                }
            } else {
                // Issue 6: the remote list is empty; the document may have been deleted, or the adapter call may have failed
                // [Bug Fix] P2: only mark CANCEL when the remote really returned a valid empty list
                // Also update last_sync_at so the P1 cooldown prevents frequent false positives
                log.warn("Remote sync: RAGFlow returned an empty document list, docId={}, current local status={}",
                        documentId, dto.getRun());
                dto.setRun("CANCEL");
                dto.setError("The document has been deleted from the remote service");

                documentDao.update(null, new UpdateWrapper<DocumentEntity>()
                        .set("run", "CANCEL")
                        .set("error", "The document has been deleted from the remote service")
                        .set("updated_at", new Date())
                        .set("last_sync_at", new Date())
                        .eq("document_id", documentId));
            }
        } catch (Exception e) {
            // [Bug Fix] P2: do not mark CANCEL when the adapter call fails, to avoid false positives from network/deserialization issues
            // Only log it and retry in the next sync cycle
            log.warn("Adapter call failed while syncing document status (not marking CANCEL), documentId: {}, error: {}",
                    documentId, e.getMessage());
        }
    }

    @Override
    public DocumentDTO.InfoVO getByDocumentId(String documentId, String datasetId) {
        if (StringUtils.isBlank(documentId) || StringUtils.isBlank(datasetId)) {
            throw new RenException(ErrorCode.RAG_DATASET_ID_AND_MODEL_ID_NOT_NULL);
        }

        log.info("=== Getting document by documentId ===");
        log.info("documentId: {}, datasetId: {}", documentId, datasetId);

        try {
            // Get the RAG configuration
            Map<String, Object> ragConfig = knowledgeBaseService.getRAGConfigByDatasetId(datasetId);

            // Extract the adapter type
            String adapterType = extractAdapterType(ragConfig);

            // Get the adapter instance from the adapter factory
            KnowledgeBaseAdapter adapter = KnowledgeBaseAdapterFactory.getAdapter(adapterType, ragConfig);

            // Get the document details through the adapter
            DocumentDTO.InfoVO info = adapter.getDocumentById(datasetId, documentId);

            if (info != null) {
                log.info("Got document details, documentId: {}", documentId);
                return info;
            } else {
                throw new RenException(ErrorCode.Knowledge_Base_RECORD_NOT_EXISTS);
            }

        } catch (Exception e) {
            log.error("Failed to get document by documentId: {}", e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "null";
            if (e instanceof RenException) {
                throw (RenException) e;
            }
            throw new RenException(ErrorCode.RAG_API_ERROR, errorMessage);
        } finally {
            log.info("=== Finished getting document by documentId ===");
        }
    }

    @Override
    public KnowledgeFilesDTO uploadDocument(String datasetId, MultipartFile file, String name,
            Map<String, Object> metaFields, String chunkMethod,
            Map<String, Object> parserConfig) {
        if (StringUtils.isBlank(datasetId) || file == null || file.isEmpty()) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }

        log.info("=== Starting document upload (strong consistency) ===");

        // 1. Preparation (non-transactional)
        String fileName = StringUtils.isNotBlank(name) ? name : file.getOriginalFilename();
        if (StringUtils.isBlank(fileName)) {
            throw new RenException(ErrorCode.RAG_FILE_NAME_NOT_NULL);
        }

        log.info("1. Starting remote upload: datasetId={}, fileName={}", datasetId, fileName);

        // Get the adapter (non-transactional)
        Map<String, Object> ragConfig = knowledgeBaseService.getRAGConfigByDatasetId(datasetId);
        KnowledgeBaseAdapter adapter = KnowledgeBaseAdapterFactory.getAdapter(extractAdapterType(ragConfig), ragConfig);

        // Build the strongly typed request DTO
        DocumentDTO.UploadReq uploadReq = DocumentDTO.UploadReq.builder()
                .datasetId(datasetId)
                .file(file)
                .name(fileName)
                .metaFields(metaFields)
                .build();

        // Convert the chunking method (String -> Enum)
        if (StringUtils.isNotBlank(chunkMethod)) {
            try {
                uploadReq.setChunkMethod(DocumentDTO.InfoVO.ChunkMethod.valueOf(chunkMethod.toUpperCase()));
            } catch (Exception e) {
                log.warn("Invalid chunking method: {}, using the backend default", chunkMethod);
            }
        }

        // Convert the parser config (Map -> DTO)
        if (parserConfig != null && !parserConfig.isEmpty()) {
            uploadReq.setParserConfig(objectMapper.convertValue(parserConfig, DocumentDTO.InfoVO.ParserConfig.class));
        }

        // Perform the remote upload (slow IO, outside the transaction)
        KnowledgeFilesDTO result = adapter.uploadDocument(uploadReq);

        if (result == null || StringUtils.isBlank(result.getDocumentId())) {
            throw new RenException(ErrorCode.RAG_API_ERROR, "Remote upload succeeded but returned no valid DocumentID");
        }

        // 2. Persist locally (called via self to activate the @Transactional proxy)
        log.info("2. Saving local shadow record: documentId={}", result.getDocumentId());
        self.saveDocumentShadow(datasetId, result, fileName, chunkMethod, parserConfig);

        log.info("=== Document uploaded and shadow record saved ===");
        return result;
    }

    /**
     * Atomically save the shadow record (upsert semantics)
     * Updates if document_id already exists, otherwise inserts, to avoid UNIQUE constraint conflicts
     *
     * @return true = newly inserted, false = existing record updated
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean saveDocumentShadow(String datasetId, KnowledgeFilesDTO result, String originalName, String chunkMethod,
            Map<String, Object> parserConfig) {
        DocumentEntity entity = new DocumentEntity();
        entity.setDatasetId(datasetId);
        entity.setDocumentId(result.getDocumentId());
        entity.setName(StringUtils.isNotBlank(result.getName()) ? result.getName() : originalName);
        entity.setSize(result.getFileSize());
        entity.setType(getFileType(entity.getName()));
        entity.setChunkMethod(chunkMethod);

        if (parserConfig != null) {
            try {
                entity.setParserConfig(objectMapper.writeValueAsString(parserConfig));
            } catch (Exception e) {
                log.warn("Failed to serialize parser config: {}", e.getMessage());
            }
        }

        entity.setStatus(result.getStatus() != null ? result.getStatus() : "1");
        entity.setRun(result.getRun());
        entity.setProgress(result.getProgress());
        entity.setThumbnail(result.getThumbnail());
        entity.setProcessDuration(result.getProcessDuration());
        entity.setSourceType(result.getSourceType());
        entity.setError(result.getError());
        entity.setChunkCount(result.getChunkCount());
        entity.setTokenCount(result.getTokenCount());
        entity.setEnabled(1);

        // Persist metadata
        if (result.getMetaFields() != null) {
            try {
                entity.setMetaFields(objectMapper.writeValueAsString(result.getMetaFields()));
            } catch (Exception e) {
                log.warn("Failed to persist shadow metadata: {}", e.getMessage());
            }
        }

        // Prefer the RAG-side timestamp, otherwise use local time
        entity.setCreatedAt(result.getCreatedAt() != null ? result.getCreatedAt() : new Date());
        entity.setUpdatedAt(result.getUpdatedAt() != null ? result.getUpdatedAt() : new Date());

        // Upsert: check whether document_id exists; update if so, otherwise insert
        DocumentEntity existing = documentDao.selectOne(
                new QueryWrapper<DocumentEntity>().eq("document_id", entity.getDocumentId()));

        if (existing != null) {
            entity.setId(existing.getId());
            entity.setCreatedAt(existing.getCreatedAt()); // Keep the original creation time
            documentDao.updateById(entity);
            log.info("Shadow record updated: documentId={}", entity.getDocumentId());
            return false;
        } else {
            documentDao.insert(entity);
            // Increment the dataset document count when inserting a new record
            knowledgeBaseService.updateStatistics(datasetId, 1, 0L, 0L);
            log.info("Shadow record inserted: documentId={}, datasetId={}", entity.getDocumentId(), datasetId);
            return true;
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteDocuments(String datasetId, DocumentDTO.BatchIdReq req) {
        if (StringUtils.isBlank(datasetId) || req == null || req.getIds() == null || req.getIds().isEmpty()) {
            throw new RenException(ErrorCode.RAG_DATASET_ID_AND_MODEL_ID_NOT_NULL);
        }

        List<String> documentIds = req.getIds();
        log.info("=== Starting batch document delete: datasetId={}, count={} ===", datasetId, documentIds.size());

        // 1. Batch permission and status pre-check
        List<DocumentEntity> entities = documentDao.selectList(
                new QueryWrapper<DocumentEntity>()
                        .eq("dataset_id", datasetId)
                        .in("document_id", documentIds));

        if (entities.size() != documentIds.size()) {
            log.warn("Some documents do not exist or have invalid ownership: expected={}, actual={}", documentIds.size(), entities.size());
            throw new RenException(ErrorCode.NO_PERMISSION);
        }

        long totalChunkDelta = 0;
        long totalTokenDelta = 0;

        for (DocumentEntity entity : entities) {
            // Block delete requests for documents that are being parsed
            // [Bug Fix] Use the run field (RUNNING) to detect parsing, not the status field
            // status="1" means "enabled/normal", not "parsing"
            if ("RUNNING".equals(entity.getRun())) {
                log.warn("Blocked delete request for a file being parsed: docId={}", entity.getDocumentId());
                throw new RenException(ErrorCode.RAG_DOCUMENT_PARSING_DELETE_ERROR);
            }
            totalChunkDelta += entity.getChunkCount() != null ? entity.getChunkCount() : 0L;
            totalTokenDelta += entity.getTokenCount() != null ? entity.getTokenCount() : 0L;
        }

        // 2. Get the adapter (non-transactional)
        Map<String, Object> ragConfig = knowledgeBaseService.getRAGConfigByDatasetId(datasetId);
        KnowledgeBaseAdapter adapter = KnowledgeBaseAdapterFactory.getAdapter(extractAdapterType(ragConfig), ragConfig);

        // 3. Perform the remote delete
        try {
            adapter.deleteDocument(datasetId, req);
            log.info("Remote batch delete request succeeded");
        } catch (Exception e) {
            log.error("Remote delete request failed, aborting local cleanup to avoid inconsistency: {}", e.getMessage());
            throw new RenException(e.getMessage());
        }

        // 4. Atomically clean up local shadow records and sync statistics
        self.deleteDocumentShadows(documentIds, datasetId, totalChunkDelta, totalTokenDelta);

        // 5. Clear the cache
        try {
            String cacheKey = RedisKeys.getKnowledgeBaseCacheKey(datasetId);
            redisUtils.delete(cacheKey);
            log.info("Evicted dataset cache: {}", cacheKey);
        } catch (Exception e) {
            log.warn("Failed to evict Redis cache: {}", e.getMessage());
        }

        log.info("=== Batch document cleanup completed ===");
    }

    /**
     * Atomically batch delete shadow records and sync parent table statistics
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocumentShadows(List<String> documentIds, String datasetId, Long chunkDelta, Long tokenDelta) {
        // 1. Physically delete the records
        int deleted = documentDao.delete(
                new QueryWrapper<DocumentEntity>()
                        .eq("dataset_id", datasetId)
                        .in("document_id", documentIds));

        if (deleted > 0) {
            // 2. Update the dataset statistics
            knowledgeBaseService.updateStatistics(datasetId, -documentIds.size(), -chunkDelta, -tokenDelta);
            log.info("Deducted dataset stats: datasetId={}, chunks={}, tokens={}", datasetId, chunkDelta, tokenDelta);
        }
    }

    /**
     * Get the file type - supports the four RAG document format types
     */
    private String getFileType(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            log.warn("File name is empty, returning unknown type");
            return "unknown";
        }

        try {
            int lastDotIndex = fileName.lastIndexOf('.');
            if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
                String extension = fileName.substring(lastDotIndex + 1).toLowerCase();

                // Document format types
                String[] documentTypes = { "pdf", "doc", "docx", "txt", "md", "mdx" };
                String[] spreadsheetTypes = { "csv", "xls", "xlsx" };
                String[] presentationTypes = { "ppt", "pptx" };

                // Check document types
                for (String type : documentTypes) {
                    if (type.equals(extension)) {
                        return "document";
                    }
                }

                // Check spreadsheet types
                for (String type : spreadsheetTypes) {
                    if (type.equals(extension)) {
                        return "spreadsheet";
                    }
                }
                // Check slide types
                for (String type : presentationTypes) {
                    if (type.equals(extension)) {
                        return "presentation";
                    }
                }
                // Return the original extension as the file type
                return extension;
            }
            return "unknown";
        } catch (Exception e) {
            log.error("Failed to get file type: ", e);
            return "unknown";
        }
    }

    /**
     * Extract the adapter type from the RAG configuration
     */
    private String extractAdapterType(Map<String, Object> config) {
        if (config == null) {
            throw new RenException(ErrorCode.RAG_CONFIG_NOT_FOUND);
        }

        // Extract the type field from the configuration
        String adapterType = (String) config.get("type");
        if (StringUtils.isBlank(adapterType)) {
            throw new RenException(ErrorCode.RAG_ADAPTER_TYPE_NOT_FOUND);
        }

        // Verify that the adapter type is registered
        if (!KnowledgeBaseAdapterFactory.isAdapterTypeRegistered(adapterType)) {
            throw new RenException(ErrorCode.RAG_ADAPTER_TYPE_NOT_SUPPORTED, "Adapter type is not registered: " + adapterType);
        }

        return adapterType;
    }

    @Override
    public boolean parseDocuments(String datasetId, List<String> documentIds) {
        if (StringUtils.isBlank(datasetId) || documentIds == null || documentIds.isEmpty()) {
            throw new RenException(ErrorCode.RAG_DATASET_ID_AND_MODEL_ID_NOT_NULL);
        }

        log.info("=== Starting document parsing (chunking) ===");
        log.info("datasetId: {}, documentIds: {}", datasetId, documentIds);

        try {
            // Get the RAG configuration
            Map<String, Object> ragConfig = knowledgeBaseService.getRAGConfigByDatasetId(datasetId);

            // Extract the adapter type
            String adapterType = extractAdapterType(ragConfig);

            // Get the knowledge base adapter
            KnowledgeBaseAdapter adapter = KnowledgeBaseAdapterFactory.getAdapter(adapterType, ragConfig);

            log.debug("Parse document params: documentIds: {}", documentIds);

            // Call the adapter to parse the documents
            boolean result = adapter.parseDocuments(datasetId, documentIds);

            if (result) {
                log.info("Document parse command sent, syncing local shadow status, datasetId: {}, documentIds: {}", datasetId, documentIds);
                // After the command succeeds, immediately set the local shadow status to RUNNING and parsing (1) so the Local-First list reflects it right away
                documentDao.update(null, new UpdateWrapper<DocumentEntity>()
                        .set("run", "RUNNING")
                        .set("status", "1")
                        .set("updated_at", new Date())
                        .eq("dataset_id", datasetId)
                        .in("document_id", documentIds));

                log.info("Local document status updated to RUNNING");
            } else {
                log.error("Document parsing failed, datasetId: {}, documentIds: {}", datasetId, documentIds);
                throw new RenException(ErrorCode.RAG_API_ERROR, "Document parsing failed");
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to parse documents: {}", e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "null";
            if (e instanceof RenException) {
                throw (RenException) e;
            }
            throw new RenException(ErrorCode.RAG_API_ERROR, errorMessage);
        } finally {
            log.info("=== Document parsing finished ===");
        }
    }

    @Override
    public ChunkDTO.ListVO listChunks(String datasetId, String documentId, ChunkDTO.ListReq req) {
        if (StringUtils.isBlank(datasetId) || StringUtils.isBlank(documentId)) {
            throw new RenException(ErrorCode.RAG_DATASET_ID_AND_MODEL_ID_NOT_NULL);
        }

        log.info("=== Listing chunks: datasetId={}, documentId={}, req={} ===", datasetId, documentId, req);

        try {
            Map<String, Object> ragConfig = knowledgeBaseService.getRAGConfigByDatasetId(datasetId);
            KnowledgeBaseAdapter adapter = KnowledgeBaseAdapterFactory.getAdapter(extractAdapterType(ragConfig),
                    ragConfig);

            ChunkDTO.ListVO result = adapter.listChunks(datasetId, documentId, req);
            log.info("Got chunk list: datasetId={}, total={}", datasetId, result.getTotal());
            return result;
        } catch (Exception e) {
            log.error("Failed to list chunks: {}", e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "null";
            if (e instanceof RenException) {
                throw (RenException) e;
            }
            throw new RenException(ErrorCode.RAG_API_ERROR, errorMessage);
        } finally {
            log.info("=== Listing chunks finished ===");
        }
    }

    @Override
    public RetrievalDTO.ResultVO retrievalTest(RetrievalDTO.TestReq req) {
        if (CollectionUtils.isEmpty(req.getDatasetIds())) {
            throw new RenException("No knowledge base specified for the retrieval test");
        }

        log.info("=== Starting retrieval test: req={} ===", req);

        try {
            Map<String, Object> ragConfig = knowledgeBaseService.getRAGConfigByDatasetId(req.getDatasetIds().get(0));
            KnowledgeBaseAdapter adapter = KnowledgeBaseAdapterFactory.getAdapter(extractAdapterType(ragConfig),
                    ragConfig);

            RetrievalDTO.ResultVO result = adapter.retrievalTest(req);
            log.info("Retrieval test succeeded: total={}", result != null ? result.getTotal() : 0);
            return result;
        } catch (Exception e) {
            log.error("Retrieval test failed: {}", e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "null";
            if (e instanceof RenException) {
                throw (RenException) e;
            }
            throw new RenException(ErrorCode.RAG_API_ERROR, errorMessage);
        } finally {
            log.info("=== Retrieval test finished ===");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocumentsByDatasetId(String datasetId) {
        log.info("Cascade cleanup of dataset documents: datasetId={}", datasetId);
        List<DocumentEntity> list = documentDao
                .selectList(new QueryWrapper<DocumentEntity>().eq("dataset_id", datasetId));
        if (list == null || list.isEmpty())
            return;

        List<String> docIds = list.stream().map(DocumentEntity::getDocumentId).toList();

        // Wrap the existing delete logic (including physical delete on RAG)
        DocumentDTO.BatchIdReq req = DocumentDTO.BatchIdReq.builder().ids(docIds).build();
        this.deleteDocuments(datasetId, req);
    }

    @Override
    public int syncDocumentsFromRAG(String datasetId) {
        log.info("=== Starting full document sync from RAGFlow to local shadow table: datasetId={} ===", datasetId);

        // 1. Get the adapter
        Map<String, Object> ragConfig = knowledgeBaseService.getRAGConfigByDatasetId(datasetId);
        KnowledgeBaseAdapter adapter = KnowledgeBaseAdapterFactory.getAdapter(extractAdapterType(ragConfig), ragConfig);

        // 2. Page through all remote documents
        List<KnowledgeFilesDTO> allRemoteDocs = new ArrayList<>();
        int pageNum = 1;
        int pageSize = 100;
        long totalRemote = Long.MAX_VALUE;

        while ((long) (pageNum - 1) * pageSize < totalRemote) {
            DocumentDTO.ListReq req = DocumentDTO.ListReq.builder()
                    .page(pageNum)
                    .pageSize(pageSize)
                    .build();
            PageData<KnowledgeFilesDTO> remotePage = adapter.getDocumentList(datasetId, req);
            if (remotePage == null || remotePage.getList() == null || remotePage.getList().isEmpty()) {
                break;
            }
            allRemoteDocs.addAll(remotePage.getList());
            totalRemote = remotePage.getTotal();
            pageNum++;
        }

        // 3. Get existing local documents
        List<DocumentEntity> localDocs = documentDao.selectList(
                new QueryWrapper<DocumentEntity>().eq("dataset_id", datasetId));
        Set<String> localDocIds = localDocs.stream()
                .map(DocumentEntity::getDocumentId)
                .collect(Collectors.toSet());

        // 4. Set of remote document IDs
        Set<String> remoteDocIds = allRemoteDocs.stream()
                .map(KnowledgeFilesDTO::getDocumentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // 5. Backfill: insert documents that exist remotely but are missing locally
        List<KnowledgeFilesDTO> newDocs = allRemoteDocs.stream()
                .filter(doc -> doc.getDocumentId() != null && !localDocIds.contains(doc.getDocumentId()))
                .collect(Collectors.toList());

        int syncCount = 0;
        if (!newDocs.isEmpty()) {
            for (KnowledgeFilesDTO doc : newDocs) {
                try {
                    self.saveDocumentShadow(datasetId, doc, doc.getName(), doc.getChunkMethod(), doc.getParserConfig());
                    // Sync the existing remote token/chunk stats
                    Long tokenCount = doc.getTokenCount() != null ? doc.getTokenCount() : 0L;
                    long chunkCount = doc.getChunkCount() != null ? doc.getChunkCount().longValue() : 0L;
                    if (tokenCount > 0 || chunkCount > 0) {
                        knowledgeBaseService.updateStatistics(datasetId, 0, chunkCount, tokenCount);
                    }
                    syncCount++;
                } catch (Exception e) {
                    log.warn("Failed to sync a document shadow record: docId={}, error={}", doc.getDocumentId(), e.getMessage());
                }
            }
            log.info("Synced {} new document shadow records from RAGFlow, datasetId={}", syncCount, datasetId);
        }

        // 6. Cleanup: delete local shadow records that no longer exist remotely
        List<DocumentEntity> deletedDocs = localDocs.stream()
                .filter(entity -> !remoteDocIds.contains(entity.getDocumentId()))
                .collect(Collectors.toList());

        if (!deletedDocs.isEmpty()) {
            List<String> deletedDocIds = new ArrayList<>();
            long totalChunkDelta = 0;
            long totalTokenDelta = 0;

            for (DocumentEntity entity : deletedDocs) {
                deletedDocIds.add(entity.getDocumentId());
                totalChunkDelta += entity.getChunkCount() != null ? entity.getChunkCount() : 0L;
                totalTokenDelta += entity.getTokenCount() != null ? entity.getTokenCount() : 0L;
            }
            try {
                self.deleteDocumentShadows(deletedDocIds, datasetId, totalChunkDelta, totalTokenDelta);
                log.info("Cleaned up {} shadow records deleted remotely, datasetId={}", deletedDocs.size(), datasetId);
            } catch (Exception e) {
                log.warn("Failed to clean up shadow records deleted remotely: datasetId={}, error={}", datasetId, e.getMessage());
            }
        }

        // 7. Full update: for documents that exist both remotely and locally, sync all fields from the remote
        // Handles cases such as RAGFlow reusing a documentId on re-upload, or metadata changing after a remote edit
        Map<String, KnowledgeFilesDTO> remoteDocMap = allRemoteDocs.stream()
                .filter(doc -> doc.getDocumentId() != null)
                .collect(Collectors.toMap(KnowledgeFilesDTO::getDocumentId, doc -> doc, (a, b) -> b));

        Map<String, DocumentEntity> localDocMap = localDocs.stream()
                .collect(Collectors.toMap(DocumentEntity::getDocumentId, e -> e, (a, b) -> b));

        int updateCount = 0;
        for (Map.Entry<String, KnowledgeFilesDTO> entry : remoteDocMap.entrySet()) {
            String docId = entry.getKey();
            DocumentEntity local = localDocMap.get(docId);
            if (local == null) {
                continue; // Not local; handled by step 5
            }
            KnowledgeFilesDTO remote = entry.getValue();

            // Full field update (remote wins) so local data matches RAGFlow exactly
            UpdateWrapper<DocumentEntity> updateWrapper = new UpdateWrapper<DocumentEntity>()
                    .set("run", remote.getRun())
                    .set("status", remote.getStatus() != null ? remote.getStatus() : local.getStatus())
                    .set("progress", remote.getProgress())
                    .set("chunk_count", remote.getChunkCount())
                    .set("token_count", remote.getTokenCount())
                    .set("size", remote.getFileSize())
                    .set("error", remote.getError())
                    .set("process_duration", remote.getProcessDuration())
                    .set("updated_at", new Date())
                    .set("last_sync_at", new Date())
                    .eq("document_id", docId)
                    .eq("dataset_id", datasetId);

            if (remote.getName() != null) {
                updateWrapper.set("name", remote.getName());
            }
            if (remote.getThumbnail() != null) {
                updateWrapper.set("thumbnail", remote.getThumbnail());
            }
            if (remote.getMetaFields() != null) {
                try {
                    updateWrapper.set("meta_fields", objectMapper.writeValueAsString(remote.getMetaFields()));
                } catch (Exception e) {
                    log.warn("Failed to serialize metadata during sync update: docId={}, error={}", docId, e.getMessage());
                }
            }

            documentDao.update(null, updateWrapper);

            // Sync stat differences (correct the parent table when chunk/token counts change)
            Long remoteTokenCount = remote.getTokenCount() != null ? remote.getTokenCount() : 0L;
            Long localTokenCount = local.getTokenCount() != null ? local.getTokenCount() : 0L;
            long remoteChunkCount = remote.getChunkCount() != null ? remote.getChunkCount().longValue() : 0L;
            long localChunkCount = local.getChunkCount() != null ? local.getChunkCount().longValue() : 0L;
            long tokenDelta = remoteTokenCount - localTokenCount;
            long chunkDelta = remoteChunkCount - localChunkCount;
            if (tokenDelta != 0 || chunkDelta != 0) {
                knowledgeBaseService.updateStatistics(datasetId, 0, chunkDelta, tokenDelta);
                log.info("Shadow update: corrected knowledge base stats, docId={}, chunkDelta={}, tokenDelta={}", docId, chunkDelta, tokenDelta);
            }

            updateCount++;
        }

        if (syncCount == 0 && deletedDocs.isEmpty() && updateCount == 0) {
            log.info("Local shadow table fully in sync with RAGFlow, datasetId={}", datasetId);
        } else {
            log.info("Sync completed: added={}, removed={}, updated={}, datasetId={}", syncCount, deletedDocs.size(), updateCount, datasetId);
        }

        return syncCount;
    }

    @Override
    public void syncRunningDocuments() {
        // 1. Query all documents in the RUNNING state
        List<DocumentEntity> runningDocs = documentDao.selectList(
                new QueryWrapper<DocumentEntity>()
                        .eq("run", "RUNNING")
                        .eq("status", "1") // Only sync enabled documents
        );

        if (runningDocs == null || runningDocs.isEmpty()) {
            return;
        }

        log.info("Scheduled task: found {} documents being parsed, starting sync...", runningDocs.size());

        // 2. Group by DatasetID to reuse the adapter
        Map<String, List<DocumentEntity>> groupedDocs = runningDocs.stream()
                .collect(Collectors.groupingBy(DocumentEntity::getDatasetId));

        groupedDocs.forEach((datasetId, docs) -> {
            KnowledgeBaseAdapter adapter = null;
            try {
                // Initialize the adapter (once per dataset)
                Map<String, Object> ragConfig = knowledgeBaseService.getRAGConfigByDatasetId(datasetId);
                adapter = KnowledgeBaseAdapterFactory.getAdapter(extractAdapterType(ragConfig), ragConfig);
            } catch (Exception e) {
                log.warn("Unable to initialize adapter for dataset {}, skipping sync: {}", datasetId, e.getMessage());
                return;
            }

            for (DocumentEntity doc : docs) {
                try {
                    // Build a temporary DTO to pass to the sync method
                    KnowledgeFilesDTO dto = convertEntityToDTO(doc);
                    // Record the token count before sync
                    Long oldTokenCount = dto.getTokenCount() != null ? dto.getTokenCount() : 0L;

                    syncDocumentStatusWithRAG(dto, adapter);

                    // 3. [Key fix] Compute the delta and update knowledge base stats
                    Long newTokenCount = dto.getTokenCount() != null ? dto.getTokenCount() : 0L;
                    Long tokenDelta = newTokenCount - oldTokenCount;

                    // Only update stats when the status becomes SUCCESS and the token count changed
                    if (tokenDelta != 0) {
                        knowledgeBaseService.updateStatistics(datasetId, 0, 0L, tokenDelta);
                        log.info("Scheduled task: corrected knowledge base stats, docId={}, tokenDelta={}", dto.getDocumentId(), tokenDelta);
                    }
                } catch (Exception e) {
                    log.error("Failed to sync document {}: {}", doc.getDocumentId(), e.getMessage());
                }
            }
        });
    }
}