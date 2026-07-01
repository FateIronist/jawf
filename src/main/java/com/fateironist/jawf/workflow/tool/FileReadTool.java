package com.fateironist.jawf.workflow.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件读取工具。
 * <p>
 * 允许工作流节点读取临时目录中的文件。
 * 默认目录：%TEMP%/jawf_graph/tmp/
 */
@Slf4j
public class FileReadTool {

    private static final String DEFAULT_DIR = "jawf_graph/tmp";

    @Tool(description = "读取指定文件的内容。文件路径相对于临时目录。例如：'data.txt' 会读取 %TEMP%/jawf_graph/tmp/data.txt")
    public String readFile(@ToolParam(description = "文件名或相对路径") String fileName) {
        try {
            Path filePath = resolvePath(fileName);
            if (!Files.exists(filePath)) {
                return "错误: 文件不存在 - " + fileName;
            }
            if (!Files.isReadable(filePath)) {
                return "错误: 文件不可读 - " + fileName;
            }
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            log.info("[FileReadTool] 读取文件: {}, 长度: {}", filePath, content.length());
            return content;
        } catch (IOException e) {
            log.error("[FileReadTool] 读取文件失败: {}", fileName, e);
            return "错误: 读取文件失败 - " + e.getMessage();
        }
    }

    @Tool(description = "列出临时目录中的文件。可选指定子目录。")
    public String listFiles(@ToolParam(description = "子目录名（可选）", required = false) String subDir) {
        try {
            Path dirPath = subDir != null && !subDir.isBlank()
                    ? getBasePath().resolve(subDir)
                    : getBasePath();

            if (!Files.exists(dirPath)) {
                return "目录不存在: " + dirPath;
            }

            StringBuilder sb = new StringBuilder();
            Files.list(dirPath).forEach(path -> {
                String type = Files.isDirectory(path) ? "[目录]" : "[文件]";
                sb.append(type).append(" ").append(path.getFileName()).append("\n");
            });

            return sb.length() > 0 ? sb.toString() : "目录为空";
        } catch (IOException e) {
            log.error("[FileReadTool] 列出文件失败", e);
            return "错误: 列出文件失败 - " + e.getMessage();
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
