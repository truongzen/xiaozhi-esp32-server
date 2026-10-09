package xiaozhi.modules.agent.service;

import com.baomidou.mybatisplus.extension.repository.IRepository;

import xiaozhi.modules.agent.entity.AgentTemplateEntity;

/**
 * @author chenerlei
 * @description Database Service for table [ai_agent_template (agent configuration template table)]
 * @createDate 2025-03-22 11:48:18
 */
public interface AgentTemplateService extends IRepository<AgentTemplateEntity> {

    /**
     * Get the default template
     * 
     * @return default template entity
     */
    AgentTemplateEntity getDefaultTemplate();

    /**
     * Update the model ID in the default template
     * 
     * @param modelType model type
     * @param modelId   model ID
     */
    void updateDefaultTemplateModelId(String modelType, String modelId);

    /**
     * Re-sort the remaining templates after a template is deleted
     * 
     * @param deletedSort sort value of the deleted template
     */
    void reorderTemplatesAfterDelete(Integer deletedSort);

    /**
     * Get the next available sort number (the smallest unused number)
     * 
     * @return next available sort number
     */
    Integer getNextAvailableSort();
}
