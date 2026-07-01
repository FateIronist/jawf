package com.fateironist.jawf.workflow.model.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fateironist.jawf.ai.LLMChat;
import com.fateironist.jawf.ai.ModelFactory;
import com.fateironist.jawf.ai.ModelProvider;
import com.fateironist.jawf.workflow.expression.ExpressionEngine;
import com.fateironist.jawf.workflow.tool.FileReadTool;
import com.fateironist.jawf.workflow.tool.FileWriteTool;
import com.fateironist.jawf.workflow.tool.FolderTool;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用节点。
 * <p>
 * 通过 {@link ModelFactory} 创建 {@link LLMChat} 实例，
 * 将输入中的 {@code ${...}} 引用解析后构造 prompt 调用大模型，
 * 并将结果写入 {@link OverAllState}。
 * <p>
 * <b>工具支持</b>：默认包含文件夹管理和文件读写工具，LLM 可以自主决定是否调用。
 * 当任务有输出（如文档、代码等）时，LLM 应先创建文件夹，再写入文件。
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class LLMNode extends Node {

    /** 模型供应商标识，格式：厂商名_模型名 */
    private String modelIdentifier;

    /** 系统提示词 */
    private String systemPrompt;

    /** 用户提示词模板，支持 ${...} 引用 */
    private String userPromptTemplate;

    /** 是否启用文件工具（默认 true） */
    private boolean fileToolsEnabled = true;

    /** 文件工具提示词后缀 */
    private static final String FILE_TOOLS_PROMPT_SUFFIX = """

            【文件操作工具说明】
            你拥有以下文件操作工具，可以在需要时使用：
            - createFolder(folderName): 创建文件夹
            - deleteFolder(folderName): 删除文件夹
            - listFolder(folderName): 列出文件夹内容
            - writeFile(fileName, content): 写入文件
            - appendToFile(fileName, content): 追加内容到文件
            - readFile(fileName): 读取文件内容
            - deleteFile(fileName): 删除文件

            文件操作默认目录为系统临时目录，无需指定完整路径。

            【重要提示】
            如果任务有输出（如文档、Word、PPT、代码、报告等），请：
            1. 先使用 createFolder 创建输出文件夹（如 "output" 或 "报告_2024"）
            2. 再使用 writeFile 将内容写入文件夹中的文件
            3. 在回复中告知用户文件保存位置

            示例：
            - createFolder("项目报告")
            - writeFile("项目报告/报告.md", "报告内容...")
            - writeFile("项目报告/数据.csv", "数据内容...")
            """;

    public LLMNode() {
        this.typeIdentifier = "llm";
    }

    public LLMNode(String id, String name) {
        this.id = id;
        this.name = name;
        this.typeIdentifier = "llm";
    }

    @Override
    public Map<String, Object> execute(OverAllState state) throws Exception {
        // 1. 解析用户提示词模板中的 ${...} 引用
        String resolvedPrompt = ExpressionEngine.resolveTemplate(userPromptTemplate, state);

        // 2. 创建 LLMChat（合并系统提示词和文件工具提示词）
        ModelProvider provider = ModelProvider.from(modelIdentifier);
        String effectiveSystemPrompt = systemPrompt;
        if (fileToolsEnabled) {
            if (effectiveSystemPrompt == null || effectiveSystemPrompt.isBlank()) {
                effectiveSystemPrompt = FILE_TOOLS_PROMPT_SUFFIX.trim();
            } else {
                effectiveSystemPrompt = effectiveSystemPrompt + "\n" + FILE_TOOLS_PROMPT_SUFFIX;
            }
        }

        LLMChat llmChat;
        if (effectiveSystemPrompt != null && !effectiveSystemPrompt.isBlank()) {
            llmChat = getModelFactory().createLLMChat(provider, effectiveSystemPrompt);
        } else {
            llmChat = getModelFactory().createLLMChat(provider);
        }

        // 3. 准备工具列表
        List<Object> tools = new ArrayList<>();
        if (fileToolsEnabled) {
            tools.add(new FolderTool());
            tools.add(new FileReadTool());
            tools.add(new FileWriteTool());
        }

        // 4. 调用 LLM（带工具）
        String response;
        if (!tools.isEmpty()) {
            response = llmChat.chat(resolvedPrompt, tools.toArray());
        } else {
            response = llmChat.chat(resolvedPrompt);
        }

        // 5. 将 LLM 响应保存到文件
        String outputFileName = "output_" + getId() + "_" + System.currentTimeMillis() + ".md";
        String outputDir = System.getProperty("java.io.tmpdir") + "/jawf_graph/tmp";
        String outputFilePath = outputDir + "/" + outputFileName;

        try {
            java.nio.file.Path outputPath = java.nio.file.Path.of(outputFilePath);
            if (!java.nio.file.Files.exists(outputPath.getParent())) {
                java.nio.file.Files.createDirectories(outputPath.getParent());
            }
            java.nio.file.Files.writeString(outputPath, response,
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            log.info("[LLMNode] LLM 响应已保存到文件: {}", outputFilePath);
        } catch (Exception e) {
            log.warn("[LLMNode] 保存 LLM 响应到文件失败: {}", outputFilePath, e);
        }

        // 6. 将结果写入 OverallState（包含响应内容和文件路径）
        Map<String, Object> result = new HashMap<>();
        String prefix = getStateKeyPrefix();
        result.put(prefix + "response", response);
        result.put(prefix + "outputFile", outputFilePath);

        // 7. 解析 output 中的额外引用
        for (Map.Entry<String, Object> entry : output.entrySet()) {
            Object resolved = ExpressionEngine.resolveValue(entry.getValue(), state);
            result.put(prefix + entry.getKey(), resolved);
        }
        return result;
    }

    /**
     * 获取 ModelFactory（延迟注入）。
     * <p>
     * 由于 Node 不是 Spring Bean，需要通过 WorkflowEngine 或 WorkflowService 注入。
     * 这里使用静态持有者模式，在 WorkflowService 初始化时设置。
     */
    private static volatile ModelFactory modelFactory;

    public static void setModelFactory(ModelFactory factory) {
        modelFactory = factory;
    }

    private static ModelFactory getModelFactory() {
        if (modelFactory == null) {
            throw new IllegalStateException("ModelFactory 尚未初始化，请先调用 LLMNode.setModelFactory()");
        }
        return modelFactory;
    }
}
