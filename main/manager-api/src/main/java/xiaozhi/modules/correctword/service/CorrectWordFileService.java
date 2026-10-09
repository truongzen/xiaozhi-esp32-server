package xiaozhi.modules.correctword.service;

import java.util.List;
import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.correctword.dto.CorrectWordFileCreateDTO;
import xiaozhi.modules.correctword.vo.CorrectWordFileVO;
import xiaozhi.modules.correctword.vo.CorrectWordSimpleVO;

public interface CorrectWordFileService {

    /**
     * Create a replacement word file
     *
     * @param dto creation parameters
     * @return file VO
     */
    CorrectWordFileVO createFile(CorrectWordFileCreateDTO dto);

    /**
     * Update a replacement word file (replaces all entries)
     *
     * @param fileId file ID
     * @param dto    update parameters
     */
    void updateFile(String fileId, CorrectWordFileCreateDTO dto);

    /**
     * Get the current user's replacement word file list
     *
     * @param params pagination parameters
     * @return paginated data
     */
    PageData<CorrectWordFileVO> listFiles(Map<String, Object> params);

    /**
     * Get the current user's replacement word file list (unpaginated, for dropdown selection)
     *
     * @return file list
     */
    List<CorrectWordFileVO> listAllFiles();

    /**
     * Get the original file content (for download)
     *
     * @param fileId file ID
     * @return file entity
     */
    CorrectWordFileVO getFileContent(String fileId);

    /**
     * Delete a replacement word file with all its entries and association records
     *
     * @param fileId file ID
     */
    void deleteFile(String fileId);

    /**
     * Delete the agent's replacement word file associations (does not delete the files themselves)
     *
     * @param agentId agent ID
     */
    void deleteMappingsByAgentId(String agentId);

    /**
     * Get all replacement word entries of an agent (compact version, for devices)
     *
     * @param agentId agent ID
     * @return replacement word list
     */
    List<CorrectWordSimpleVO> getAllItemsByAgentId(String agentId);

    /**
     * Get the replacement word file ID list associated with an agent
     *
     * @param agentId agent ID
     * @return file ID list
     */
    List<String> getAgentCorrectWordFileIds(String agentId);

    /**
     * Save the replacement word files associated with an agent (full replacement)
     *
     * @param agentId agent ID
     * @param fileIds file ID list
     */
    void saveAgentCorrectWords(String agentId, List<String> fileIds);

    /**
     * Batch delete replacement word files
     *
     * @param fileIds file ID list
     */
    void batchDeleteFiles(List<String> fileIds);
}
