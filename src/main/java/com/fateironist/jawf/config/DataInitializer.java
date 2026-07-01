package com.fateironist.jawf.config;

import com.fateironist.jawf.service.AgentService;
import com.fateironist.jawf.service.ConversationService;
import com.fateironist.jawf.service.GraphExecutionService;
import com.fateironist.jawf.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据初始化配置。
 * <p>
 * 在应用启动时自动创建数据库表。
 */
@Slf4j
@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(AgentService agentService,
                                   ConversationService conversationService,
                                   MessageService messageService,
                                   GraphExecutionService graphExecutionService) {
        return args -> {
            log.info("初始化数据库表...");
            try {
                agentService.initTable();
                log.info("✓ agents 表初始化完成");
            } catch (Exception e) {
                log.error("✗ agents 表初始化失败", e);
            }
            try {
                conversationService.initTable();
                log.info("✓ conversations 表初始化完成");
            } catch (Exception e) {
                log.error("✗ conversations 表初始化失败", e);
            }
            try {
                messageService.initTable();
                log.info("✓ messages 表初始化完成");
            } catch (Exception e) {
                log.error("✗ messages 表初始化失败", e);
            }
            try {
                graphExecutionService.initTable();
                log.info("✓ graph_executions 表初始化完成");
            } catch (Exception e) {
                log.error("✗ graph_executions 表初始化失败", e);
            }
            log.info("数据库初始化完成");
        };
    }
}
