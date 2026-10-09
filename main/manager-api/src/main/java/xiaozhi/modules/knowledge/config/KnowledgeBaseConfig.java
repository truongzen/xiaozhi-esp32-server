package xiaozhi.modules.knowledge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xiaozhi.modules.knowledge.rag.KnowledgeBaseAdapterFactory;

/**
 * Knowledge base configuration class
 * Configures knowledge-base-related beans
 */
@Configuration
public class KnowledgeBaseConfig {

    /**
     * Provides the KnowledgeBaseAdapterFactory bean instance
     * @return KnowledgeBaseAdapterFactory instance
     */
    @Bean
    public KnowledgeBaseAdapterFactory knowledgeBaseAdapterFactory() {
        return new KnowledgeBaseAdapterFactory();
    }
}