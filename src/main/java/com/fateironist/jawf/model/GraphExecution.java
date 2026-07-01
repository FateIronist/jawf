package com.fateironist.jawf.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Graph 执行实例。
 * <p>
 * 与不同 threadId 的 IntentRecognitionGraph 相对应，记录执行状态和进度。
 * 随着 Graph 推进，状态随之流转，并通过 WebSocket 通知前端更新。
 */
@Data
public class GraphExecution {

    /** 自增主键 */
    private Long id;

    /** Graph 线程 ID（与 IntentRecognitionGraph 的 threadId 对应） */
    private String threadId;

    /** 关联的会话 ID */
    private String conversationId;

    /** 关联的 Agent ID */
    private String agentId;

    /** 执行状态 */
    private String status;

    /** 当前节点名称 */
    private String currentNode;

    /** 执行进度 (0-100) */
    private Integer progress;

    /** 错误信息（如果失败） */
    private String errorMessage;

    /** 需求文档路径 */
    private String requirementDocPath;

    /** 计划 JSON 文件路径 */
    private String planJsonPath;

    /** 计划 JSON（工作流图定义） */
    private String planJson;

    /** 扩展数据（JSON 格式，存储中间结果） */
    private String extraData;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 执行状态枚举。
     * <p>
     * 与 IntentRecognitionGraph 的节点对应：
     * <pre>
     *   INITIALIZING → INTENT_RECOGNITION → REQUIREMENT_CLARIFICATION → WORKFLOW_GENERATION → WORKFLOW_EXECUTION → COMPLETED
     *                                                  ↑                           │
     *                                                  └───── (循环补充需求) ───────┘
     * </pre>
     */
    public enum Status {
        /** 初始化中 */
        INITIALIZING("initializing", "初始化中", 0),

        /** 意图识别中 */
        INTENT_RECOGNITION("intent_recognition", "意图识别中", 10),

        /** 需求澄清中（包括追问循环） */
        REQUIREMENT_CLARIFICATION("requirement_clarification", "需求澄清中", 30),

        /** 工作流图生成中 */
        WORKFLOW_GENERATION("workflow_generation", "工作流生成中", 50),

        /** 工作流图确认中（等待用户确认） */
        WORKFLOW_CONFIRMATION("workflow_confirmation", "工作流确认中", 60),

        /** 工作流图校验中 */
        WORKFLOW_VALIDATION("workflow_validation", "工作流校验中", 70),

        /** 工作流执行中 */
        WORKFLOW_EXECUTION("workflow_execution", "工作流执行中", 80),

        /** 执行完成 */
        COMPLETED("completed", "执行完成", 100),

        /** 执行失败 */
        FAILED("failed", "执行失败", -1);

        private final String value;
        private final String label;
        private final int progressValue;

        Status(String value, String label, int progressValue) {
            this.value = value;
            this.label = label;
            this.progressValue = progressValue;
        }

        public String getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }

        public int getProgressValue() {
            return progressValue;
        }

        public static Status fromValue(String value) {
            for (Status status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("未知的执行状态: " + value);
        }
    }
}
