package xiaozhi.modules.knowledge.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.modules.knowledge.service.KnowledgeFilesService;

/**
 * Scheduled task that syncs knowledge base document status
 * 
 * Purpose:
 * 1. Automatically scan documents in the "RUNNING" (parsing) state
 * 2. Call the RAGFlow API to get the latest status
 * 3. When the status changes (RUNNING -> SUCCESS/FAIL), update the database accordingly
 * 4. [Key] On successful parsing, backfill the knowledge base statistics (TokenCount)
 */
@Component
@AllArgsConstructor
@Slf4j
public class DocumentStatusSyncTask {

    private final KnowledgeFilesService knowledgeFilesService;

    /**
     * Run the sync every 30 seconds
     * Uses fixedDelay so the next run starts 30 seconds after the previous one finishes, preventing backlog
     */
    @Scheduled(fixedDelay = 30000)
    public void syncRunningDocuments() {
        try {
            // log.debug("Starting document status sync task...");
            knowledgeFilesService.syncRunningDocuments();
        } catch (Exception e) {
            log.error("Document status sync task failed", e);
        }
    }
}
