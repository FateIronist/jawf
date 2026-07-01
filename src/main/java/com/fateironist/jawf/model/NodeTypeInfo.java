package com.fateironist.jawf.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 节点类型信息。
 * <p>
 * 描述工作流中可用的节点类型及其配置项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeTypeInfo {

    /** 节点类型标识 */
    private String type;

    /** 节点类型名称 */
    private String name;

    /** 节点描述 */
    private String description;

    /** 节点图标 */
    private String icon;

    /** 节点颜色 */
    private String color;

    /** 节点分类 */
    private String category;

    /** 配置项列表 */
    private List<ConfigField> configFields;

    /**
     * 配置字段信息。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigField {
        /** 字段名 */
        private String field;
        /** 字段标签 */
        private String label;
        /** 字段类型：text, textarea, number, select, boolean */
        private String type;
        /** 是否必填 */
        private boolean required;
        /** 默认值 */
        private Object defaultValue;
        /** 占位符 */
        private String placeholder;
        /** 选项列表（type=select 时使用） */
        private List<Option> options;
        /** 字段描述 */
        private String description;
    }

    /**
     * 选项信息。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        /** 选项值 */
        private String value;
        /** 选项标签 */
        private String label;
    }
}
