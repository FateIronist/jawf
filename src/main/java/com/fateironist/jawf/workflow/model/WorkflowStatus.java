package com.fateironist.jawf.workflow.model;

/**
 * 工作流整体执行状态。
 */
public enum WorkflowStatus {

    /** 空闲 */
    IDLE,
    /** 运行中 */
    RUNNING,
    /** 已完成 */
    COMPLETED,
    /** 执行失败 */
    FAILED
}
