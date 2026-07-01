# Workflow System Implementation Plan

## Overview

Implement a Dify-style workflow system under `com.fateironist.jawf.workflow` package that supports building, compiling, and executing graph-based workflows with mapping to Spring AI Alibaba Graph.

## Package Structure

```
src/main/java/com/fateironist/jawf/workflow/
├── model/
│   ├── node/
│   │   ├── Node.java              # 节点抽象基类
│   │   ├── StartNode.java         # 开始节点
│   │   ├── EndNode.java           # 结束节点
│   │   └── LLMNode.java           # LLM 节点
│   ├── edge/
│   │   ├── Edge.java              # 边抽象基类
│   │   ├── NormalEdge.java        # 普通边
│   │   └── ConditionEdge.java     # 条件边
│   ├── Workflow.java              # 工作流容器
│   ├── NodeStatus.java            # 节点状态枚举
│   └── WorkflowStatus.java        # 工作流状态枚举
├── engine/
│   ├── WorkflowEngine.java        # 执行引擎
│   └── GraphBuilder.java          # Graph 构建器
├── expression/
│   ├── ExpressionEngine.java      # 表达式引擎
│   └── ExpressionContext.java     # 表达式上下文
├── validation/
│   └── WorkflowValidator.java     # 图校验（拓扑排序/环检测）
├── serialization/
│   ├── WorkflowJsonSerializer.java
│   └── WorkflowJsonDeserializer.java
└── service/
    └── WorkflowService.java       # 服务层
```

## Phase 1: Domain Models

### 1.1 Node 抽象类
- 属性: id, name, input, output, status, progress, retry, maxRetry, error
- 抽象方法: `execute(OverAllState state)` → `Map<String, Object>`
- input/output 中的 `${...}` 引用在执行前从 OverallState 解析为实际值

### 1.2 Edge 抽象类
- 属性: id, name, fromNodeId, fromNodeName, toNodeId, toNodeName
- NormalEdge: 无条件，始终通过
- ConditionEdge: 包含 condition 表达式，通过 ExpressionEngine 求值

### 1.3 Workflow 容器
- 管理 nodes 和 edges 列表
- 记录 graphId
- 提供 `buildGraph()` 构建 StateGraph
- 提供 `toJson()` / `fromJson()` 序列化

### 1.4 状态枚举
- NodeStatus: PENDING, RUNNING, SUCCESS, FAILED, SKIPPED
- WorkflowStatus: IDLE, RUNNING, COMPLETED, FAILED

## Phase 2: Expression Engine

### 2.1 ExpressionEngine
- 解析 `${...}` 引用为 OverallState 中的实际值
- 支持运算符: >, <, ==, !=, &&, ||, +, -, *, /, >>, >>>, <<, %, ()
- 使用递归下降解析器实现

### 2.2 ExpressionContext
- 从 OverallState 提供变量解析
- 缓存已解析的值

## Phase 3: Graph Builder

### 3.1 GraphBuilder
- 将 Workflow 模型转换为 StateGraph
- 每个 Node 映射为 AsyncNodeAction
- 每个 Edge 映射为条件路由
- 添加 START/END 虚拟节点

### 3.2 节点执行逻辑
1. 解析 input 中的 `${...}` 引用
2. 执行节点逻辑
3. 将 output 写入 OverallState
4. 失败时重试，超过 maxRetry 后标记 SKIPPED

## Phase 4: Workflow Engine

### 4.1 WorkflowEngine
- 编译工作流为 CompiledGraph
- 启动执行
- 恢复中断的执行
- 跟踪执行状态和进度

### 4.2 执行流程
1. 拓扑排序校验（检测环）
2. 构建 StateGraph
3. 编译图
4. 执行（带 checkpoint）
5. 处理节点失败和重试

## Phase 5: JSON Serialization

### 5.1 序列化
- 包含节点、边、状态、输入输出引用
- 包含布局信息 (x, y 坐标)
- 包含 UI 元数据 (展开状态、图标、颜色、类型标识)

### 5.2 反序列化
- 从 JSON 恢复完整的 Workflow 对象
- 重建 Node 和 Edge 实例

## Phase 6: Workflow Service

### 6.1 WorkflowService
- 高层 API: start, resume, get status
- 注入 ModelFactory 创建 LLMChat
- 管理工作流实例

## Testing Strategy

### 单元测试
- Node/Edge 模型验证
- ExpressionEngine 解析和求值
- JSON 序列化/反序列化往返
- 拓扑排序环检测

### 集成测试
- 工作流编译和执行
- 节点重试逻辑
- 条件边路由
- 状态持久化

## Implementation Order

1. Node/Edge/Workflow 基础模型
2. NodeStatus/WorkflowStatus 枚举
3. ExpressionEngine
4. WorkflowValidator (拓扑排序)
5. GraphBuilder
6. WorkflowEngine
7. JSON 序列化/反序列化
8. WorkflowService
9. 单元测试
10. 集成测试
