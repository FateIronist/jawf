package com.fateironist.jawf;

import com.fateironist.jawf.ai.EmbeddingChat;
import com.fateironist.jawf.ai.LLMChat;
import com.fateironist.jawf.ai.ModelFactory;
import com.fateironist.jawf.ai.ModelProvider;
import com.fateironist.jawf.functool.WeatherTool;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文本模型 + 向量模型 集成测试（使用真实 DashScope API）。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModelTest {

    @Autowired
    private ModelFactory modelFactory;

    // ==================== LLM Chat Tests ====================

    @Test
    @Order(1)
    @DisplayName("LLM - 简单文本对话")
    void testSimpleChat() {
        LLMChat chat = modelFactory.createLLMChat(
                new ModelProvider("dashscope", "deepseek-v4-flash"));
        assertNotNull(chat, "LLMChat 实例应不为空（请确认 model.* 配置正确）");

        String reply = chat.chat("你好，请用一句话介绍你自己");
        assertNotNull(reply);
        assertFalse(reply.isBlank(), "回复不应为空");
        System.out.println("[LLM 简单对话] " + reply);
    }

    @Test
    @Order(2)
    @DisplayName("LLM - 带系统提示词的对话")
    void testChatWithSystemPrompt() {
        LLMChat chat = modelFactory.createLLMChat(
                new ModelProvider("dashscope", "deepseek-v4-flash"),
                "你是一个只会用古文回答问题的诗人");
        assertNotNull(chat);

        String reply = chat.chat("今天天气怎么样？");
        assertNotNull(reply);
        assertFalse(reply.isBlank());
        System.out.println("[LLM 古文诗人] " + reply);
    }

    @Test
    @Order(3)
    @DisplayName("LLM - 带 Tool Calling 的对话")
    void testChatWithTool() {
        LLMChat chat = modelFactory.createLLMChat(
                new ModelProvider("dashscope", "deepseek-v4-flash"));
        assertNotNull(chat);

        String reply = chat.chat("请帮我查一下北京现在的天气", new WeatherTool());
        assertNotNull(reply);
        assertFalse(reply.isBlank());
        System.out.println("[LLM + Tool] " + reply);
    }

    // ==================== Embedding Tests ====================

    @Test
    @Order(10)
    @DisplayName("Embedding - 单条文本向量化")
    void testSingleEmbed() {
        EmbeddingChat embed = modelFactory.createEmbeddingChat(
                new ModelProvider("dashscope", "text-embedding-v3"));
        assertNotNull(embed, "EmbeddingChat 实例应不为空（请确认 model.* 中包含 embedding 模型）");

        float[] vector = embed.embed("Spring AI Alibaba 是一个 AI 应用开发框架");
        assertNotNull(vector);
        assertTrue(vector.length > 0, "向量维度不应为 0");
        System.out.println("[Embedding 单条] dim=" + vector.length
                + ", 前5维=" + Arrays.toString(Arrays.copyOf(vector, 5)));
    }

    @Test
    @Order(11)
    @DisplayName("Embedding - 批量文本向量化")
    void testBatchEmbed() {
        EmbeddingChat embed = modelFactory.createEmbeddingChat(
                new ModelProvider("dashscope", "text-embedding-v3"));
        assertNotNull(embed);

        List<String> texts = List.of(
                "SQLite 是一个轻量级嵌入式数据库",
                "向量数据库可以做语义相似度搜索",
                "DashScope 是阿里云的大模型服务"
        );
        List<float[]> vectors = embed.embed(texts);

        assertEquals(texts.size(), vectors.size(), "返回向量数应与输入文本数一致");
        for (int i = 0; i < vectors.size(); i++) {
            assertEquals(embed.dimensions(), vectors.get(i).length,
                    "第 " + i + " 条向量维度应与模型声明维度一致");
        }
        System.out.println("[Embedding 批量] 共 " + vectors.size() + " 条, dim=" + embed.dimensions());
    }

    @Test
    @Order(12)
    @DisplayName("Embedding - 维度一致性校验")
    void testDimensionsConsistency() {
        EmbeddingChat embed = modelFactory.createEmbeddingChat(
                new ModelProvider("dashscope", "text-embedding-v3"));
        assertNotNull(embed);

        int declared = embed.dimensions();
        float[] v1 = embed.embed("测试文本A");
        float[] v2 = embed.embed("测试文本B");

        assertEquals(declared, v1.length);
        assertEquals(declared, v2.length);
        System.out.println("[Embedding 维度] declared=" + declared + ", v1=" + v1.length + ", v2=" + v2.length);
    }
}
