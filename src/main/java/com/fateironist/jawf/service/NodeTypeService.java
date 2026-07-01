package com.fateironist.jawf.service;

import com.fateironist.jawf.model.NodeTypeInfo;
import com.fateironist.jawf.model.NodeTypeInfo.ConfigField;
import com.fateironist.jawf.model.NodeTypeInfo.Option;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 节点类型服务。
 * <p>
 * 提供工作流中可用的节点类型及其配置信息。
 */
@Slf4j
@Service
public class NodeTypeService {

    private final Map<String, NodeTypeInfo> nodeTypes = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        registerStartNode();
        registerEndNode();
        registerLLMNode();
        registerHTTPRequestNode();
        registerCodeNode();
        registerConditionNode();
        log.info("[NodeTypeService] 注册了 {} 种节点类型", nodeTypes.size());
    }

    /**
     * 获取所有可用的节点类型。
     */
    public List<NodeTypeInfo> getAllNodeTypes() {
        return new ArrayList<>(nodeTypes.values());
    }

    /**
     * 获取指定类型的节点信息。
     */
    public NodeTypeInfo getNodeType(String type) {
        return nodeTypes.get(type);
    }

    /**
     * 检查节点类型是否有效。
     */
    public boolean isValidNodeType(String type) {
        return nodeTypes.containsKey(type);
    }

    // ==================== 节点类型注册 ====================

    private void registerStartNode() {
        List<ConfigField> fields = List.of(
                new ConfigField("inputVariables", "输入变量", "textarea", false, null,
                        "定义输入变量，每行一个，格式：变量名=默认值", null, "工作流的输入参数")
        );
        nodeTypes.put("start", new NodeTypeInfo(
                "start", "开始节点", "工作流入口，定义输入参数",
                "video-play", "#67c23a", "基础", fields
        ));
    }

    private void registerEndNode() {
        List<ConfigField> fields = List.of(
                new ConfigField("outputVariables", "输出变量", "textarea", false, null,
                        "定义输出变量，每行一个，格式：变量名=${nodeId.field}", null, "工作流的输出参数"),
                new ConfigField("summaryPrompt", "总结提示词", "textarea", false,
                        "请根据以下执行结果，为用户生成一份简洁的执行总结报告，包括：\n1. 执行了哪些步骤\n2. 每个步骤的结果\n3. 生成的文件或输出在哪里\n4. 任何需要注意的问题\n\n执行结果：\n{results}",
                        "用于让 LLM 生成执行总结的提示词模板", null, "使用 {results} 占位符引用执行结果")
        );
        nodeTypes.put("end", new NodeTypeInfo(
                "end", "结束节点", "工作流出口，生成执行总结",
                "circle-check", "#909399", "基础", fields
        ));
    }

    private void registerLLMNode() {
        List<ConfigField> fields = List.of(
                new ConfigField("modelIdentifier", "模型标识", "text", true, "dashscope_deepseek-v4-flash",
                        "如 dashscope_deepseek-v4-flash", null, "使用的 LLM 模型"),
                new ConfigField("systemPrompt", "系统提示词", "textarea", false, null,
                        "定义 LLM 的角色和行为", null, "系统级提示词"),
                new ConfigField("userPromptTemplate", "用户提示词", "textarea", true, null,
                        "支持 ${nodeId.field} 引用其他节点输出", null, "用户输入的提示词模板"),
                new ConfigField("temperature", "温度", "number", false, 0.7,
                        "0-2 之间", null, "控制输出随机性"),
                new ConfigField("maxTokens", "最大Token数", "number", false, 4096,
                        "最大输出 Token 数", null, "限制输出长度")
        );
        nodeTypes.put("llm", new NodeTypeInfo(
                "llm", "LLM 节点", "调用大语言模型处理文本",
                "cpu", "#409eff", "AI", fields
        ));
    }

    private void registerHTTPRequestNode() {
        List<Option> methodOptions = List.of(
                new Option("GET", "GET"),
                new Option("POST", "POST"),
                new Option("PUT", "PUT"),
                new Option("DELETE", "DELETE"),
                new Option("PATCH", "PATCH")
        );
        List<ConfigField> fields = List.of(
                new ConfigField("url", "请求地址", "text", true, null,
                        "支持 ${nodeId.field} 引用", null, "HTTP 请求地址"),
                new ConfigField("method", "请求方法", "select", true, "GET",
                        null, methodOptions, "HTTP 请求方法"),
                new ConfigField("headers", "请求头", "textarea", false, null,
                        "JSON 格式，每行一个", null, "请求头信息"),
                new ConfigField("body", "请求体", "textarea", false, null,
                        "JSON 格式", null, "请求体内容"),
                new ConfigField("timeout", "超时时间(秒)", "number", false, 30,
                        "请求超时时间", null, "请求超时设置")
        );
        nodeTypes.put("http_request", new NodeTypeInfo(
                "http_request", "HTTP 请求", "发送 HTTP 请求调用外部 API",
                "cloud", "#E91E63", "工具", fields
        ));
    }

    private void registerCodeNode() {
        List<Option> languageOptions = List.of(
                new Option("javascript", "JavaScript"),
                new Option("python", "Python")
        );
        List<ConfigField> fields = List.of(
                new ConfigField("language", "编程语言", "select", true, "javascript",
                        null, languageOptions, "代码执行语言"),
                new ConfigField("code", "代码", "textarea", true, null,
                        "编写处理逻辑代码", null, "可使用 input 变量获取输入"),
                new ConfigField("timeout", "超时时间(秒)", "number", false, 30,
                        "代码执行超时", null, "执行超时设置")
        );
        nodeTypes.put("code", new NodeTypeInfo(
                "code", "代码节点", "执行自定义代码逻辑",
                "code", "#00BCD4", "工具", fields
        ));
    }

    private void registerConditionNode() {
        List<ConfigField> fields = List.of(
                new ConfigField("condition", "条件表达式", "textarea", true, null,
                        "如: ${node1.output} > 10", null, "条件判断表达式"),
                new ConfigField("trueBranch", "条件为真时", "text", true, null,
                        "目标节点 ID", null, "条件为真时的下一个节点"),
                new ConfigField("falseBranch", "条件为假时", "text", true, null,
                        "目标节点 ID", null, "条件为假时的下一个节点")
        );
        nodeTypes.put("condition", new NodeTypeInfo(
                "condition", "条件分支", "根据条件表达式选择执行路径",
                "filter", "#FF9800", "逻辑", fields
        ));
    }
}
