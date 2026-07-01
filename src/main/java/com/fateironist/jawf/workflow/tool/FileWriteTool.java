package com.fateironist.jawf.workflow.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具。
 * <p>
 * 允许工作流节点将内容写入临时目录中的文件。
 * 默认目录：%TEMP%/jawf_graph/tmp/
 */
@Slf4j
public class FileWriteTool {

    private static final String DEFAULT_DIR = "jawf_graph/tmp";

    @Tool(description = "将内容写入指定文件。文件路径相对于临时目录。如果文件不存在会自动创建。")
    public String writeFile(
            @ToolParam(description = "文件名或相对路径") String fileName,
            @ToolParam(description = "要写入的内容") String content) {
        try {
            Path filePath = resolvePath(fileName);

            // 确保父目录存在
            if (!Files.exists(filePath.getParent())) {
                Files.createDirectories(filePath.getParent());
            }

            Files.writeString(filePath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("[FileWriteTool] 写入文件: {}, 长度: {}", filePath, content.length());
            return "成功写入文件: " + fileName + " (" + content.length() + " 字符)";
        } catch (IOException e) {
            log.error("[FileWriteTool] 写入文件失败: {}", fileName, e);
            return "错误: 写入文件失败 - " + e.getMessage();
        }
    }

    @Tool(description = "向指定文件追加内容。如果文件不存在会自动创建。")
    public String appendToFile(
            @ToolParam(description = "文件名或相对路径") String fileName,
            @ToolParam(description = "要追加的内容") String content) {
        try {
            Path filePath = resolvePath(fileName);

            // 确保父目录存在
            if (!Files.exists(filePath.getParent())) {
                Files.createDirectories(filePath.getParent());
            }

            Files.writeString(filePath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            log.info("[FileWriteTool] 追加到文件: {}, 长度: {}", filePath, content.length());
            return "成功追加到文件: " + fileName + " (" + content.length() + " 字符)";
        } catch (IOException e) {
            log.error("[FileWriteTool] 追加文件失败: {}", fileName, e);
            return "错误: 追加文件失败 - " + e.getMessage();
        }
    }

    @Tool(description = "删除指定文件。")
    public String deleteFile(@ToolParam(description = "文件名或相对路径") String fileName) {
        try {
            Path filePath = resolvePath(fileName);
            if (!Files.exists(filePath)) {
                return "文件不存在: " + fileName;
            }
            Files.delete(filePath);
            log.info("[FileWriteTool] 删除文件: {}", filePath);
            return "成功删除文件: " + fileName;
        } catch (IOException e) {
            log.error("[FileWriteTool] 删除文件失败: {}", fileName, e);
            return "错误: 删除文件失败 - " + e.getMessage();
        }
    }

    private Path resolvePath(String fileName) {
        // 防止路径遍历攻击
        if (fileName.contains("..") || fileName.contains(":")) {
            throw new IllegalArgumentException("非法文件路径: " + fileName);
        }
        return getBasePath().resolve(fileName);
    }

    private Path getBasePath() {
        return Path.of(System.getProperty("java.io.tmpdir"), DEFAULT_DIR);
    }
}
