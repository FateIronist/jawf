package com.fateironist.jawf.workflow.model;

/**
 * 工作流节点执行状态。
 */
public enum NodeStatus {

    /** 待执行 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 执行成功 */
    SUCCESS,
    /** 执行失败 */
    FAILED,
    /** 超过最大重试次数后跳过 */
    SKIPPED
}
