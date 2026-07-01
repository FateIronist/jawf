package com.fateironist.jawf.workflow.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件夹管理工具。
 * <p>
 * 允许工作流节点创建、删除、列出临时目录中的文件夹。
 * 默认目录：%TEMP%/jawf_graph/tmp/
 */
@Slf4j
public class FolderTool {

    private static final String DEFAULT_DIR = "jawf_graph/tmp";

    @Tool(description = "创建文件夹。如果文件夹已存在则忽略。用于在生成文件前创建输出目录。")
    public String createFolder(@ToolParam(description = "文件夹名称或相对路径，例如 'output' 或 'docs/reports'") String folderName) {
        try {
            Path folderPath = resolvePath(folderName);
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
                log.info("[FolderTool] 创建文件夹: {}", folderPath);
                return "成功创建文件夹: " + folderName;
            } else {
                return "文件夹已存在: " + folderName;
            }
        } catch (IOException e) {
            log.error("[FolderTool] 创建文件夹失败: {}", folderName, e);
            return "错误: 创建文件夹失败 - " + e.getMessage();
        }
    }

    @Tool(description = "删除文件夹及其所有内容。请谨慎使用，删除后无法恢复。")
    public String deleteFolder(@ToolParam(description = "文件夹名称或相对路径") String folderName) {
        try {
            Path folderPath = resolvePath(folderName);
            if (!Files.exists(folderPath)) {
                return "文件夹不存在: " + folderName;
            }
            if (!Files.isDirectory(folderPath)) {
                return "不是文件夹: " + folderName;
            }
            // 递归删除文件夹
            deleteDirectory(folderPath);
            log.info("[FolderTool] 删除文件夹: {}", folderPath);
            return "成功删除文件夹: " + folderName;
        } catch (IOException e) {
            log.error("[FolderTool] 删除文件夹失败: {}", folderName, e);
            return "错误: 删除文件夹失败 - " + e.getMessage();
        }
    }

    @Tool(description = "列出文件夹中的内容。返回文件和子文件夹列表。")
    public String listFolder(@ToolParam(description = "文件夹名称或相对路径，留空则列出根目录") String folderName) {
        try {
            Path folderPath = (folderName == null || folderName.isBlank())
                    ? getBasePath()
                    : resolvePath(folderName);

            if (!Files.exists(folderPath)) {
                return "文件夹不存在: " + folderName;
            }
            if (!Files.isDirectory(folderPath)) {
                return "不是文件夹: " + folderName;
            }

            List<String> items = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folderPath)) {
                for (Path entry : stream) {
                    String type = Files.isDirectory(entry) ? "[文件夹]" : "[文件]";
                    long size = Files.isRegularFile(entry) ? Files.size(entry) : 0;
                    items.add(type + " " + entry.getFileName() + (size > 0 ? " (" + size + " bytes)" : ""));
                }
            }

            if (items.isEmpty()) {
                return "文件夹为空: " + folderName;
            }
            return String.join("\n", items);
        } catch (IOException e) {
            log.error("[FolderTool] 列出文件夹内容失败: {}", folderName, e);
            return "错误: 列出文件夹内容失败 - " + e.getMessage();
        }
    }

    @Tool(description = "检查文件夹是否存在")
    public String checkFolder(@ToolParam(description = "文件夹名称或相对路径") String folderName) {
        Path folderPath = resolvePath(folderName);
        if (Files.exists(folderPath) && Files.isDirectory(folderPath)) {
            return "文件夹存在: " + folderName;
        } else {
            return "文件夹不存在: " + folderName;
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .sorted((a, b) -> b.compareTo(a)) // 逆序删除（先删文件再删目录）
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("[FolderTool] 删除失败: {}", path, e);
                    }
                });
    }

    private Path resolvePath(String folderName) {
        // 防止路径遍历攻击
        if (folderName.contains("..") || folderName.contains(":")) {
            throw new IllegalArgumentException("非法文件夹路径: " + folderName);
        }
        return getBasePath().resolve(folderName);
    }

    private Path getBasePath() {
        return Path.of(System.getProperty("java.io.tmpdir"), DEFAULT_DIR);
    }
}
