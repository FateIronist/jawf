<template>
  <div class="flow-chart-editor" ref="containerRef">
    <!-- 工具栏 -->
    <div class="flow-chart-toolbar">
      <div class="toolbar-group">
        <el-button-group size="small">
          <el-tooltip content="放大">
            <el-button @click="zoomIn">
              <el-icon><ZoomIn /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="缩小">
            <el-button @click="zoomOut">
              <el-icon><ZoomOut /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="重置视图">
            <el-button @click="resetView">
              <el-icon><FullScreen /></el-icon>
            </el-button>
          </el-tooltip>
        </el-button-group>
      </div>

      <el-divider direction="vertical" />

      <!-- 添加节点下拉菜单 -->
      <el-dropdown @command="addNodeByType" trigger="click">
        <el-button size="small" type="primary">
          <el-icon><Plus /></el-icon>
          添加节点
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="llm">
              <el-icon><Cpu /></el-icon>
              LLM 节点
            </el-dropdown-item>
            <el-dropdown-item command="http_request">
              <el-icon><Connection /></el-icon>
              HTTP 请求
            </el-dropdown-item>
            <el-dropdown-item command="code">
              <el-icon><Document /></el-icon>
              代码节点
            </el-dropdown-item>
            <el-dropdown-item command="condition">
              <el-icon><Switch /></el-icon>
              条件分支
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 删除选中节点按钮 -->
      <el-button
        v-if="selectedNodeId && canDeleteSelectedNode"
        size="small"
        type="danger"
        @click="deleteSelectedNode"
      >
        <el-icon><Delete /></el-icon>
        删除节点
      </el-button>

      <!-- 删除选中边按钮 -->
      <el-button
        v-if="selectedEdgeId"
        size="small"
        type="danger"
        @click="deleteSelectedEdge"
      >
        <el-icon><Delete /></el-icon>
        删除连线
      </el-button>
    </div>

    <!-- 画布 -->
    <div
      class="flow-chart-canvas"
      ref="canvasRef"
      @mousedown="startPanCanvas"
      @mousemove="onCanvasMouseMove"
      @mouseup="onCanvasMouseUp"
      @contextmenu.prevent
      tabindex="0"
    >
      <svg class="connections-layer">
        <defs>
          <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#909399" />
          </marker>
          <marker id="arrowhead-selected" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#409eff" />
          </marker>
        </defs>
        <g :transform="`translate(${panX}, ${panY}) scale(${scale})`">
          <!-- 已有边 -->
          <g v-for="edge in edges" :key="edge.id">
            <!-- 边的可点击区域（透明宽线） -->
            <path
              :d="getEdgePath(edge)"
              class="edge-hitarea"
              @click.stop="selectEdge(edge)"
            />
            <!-- 边的可见路径 -->
            <path
              :d="getEdgePath(edge)"
              class="edge-path"
              :class="{ condition: edge.type === 'condition', selected: selectedEdgeId === edge.id }"
              :marker-end="selectedEdgeId === edge.id ? 'url(#arrowhead-selected)' : 'url(#arrowhead)'"
            />
            <text
              :x="getEdgeLabelX(edge)"
              :y="getEdgeLabelY(edge)"
              class="edge-label"
            >
              {{ edge.name }}
            </text>
          </g>

          <!-- 正在绘制的临时边（透明辅助线，确保连线功能正常） -->
          <path
            v-if="isDrawingEdge"
            :d="getTempEdgePath()"
            class="edge-path-temp"
          />
        </g>
      </svg>

      <!-- 节点 -->
      <div
        v-for="node in nodes"
        :key="node.id"
        class="flow-node"
        :class="[node.type, { selected: selectedNodeId === node.id }]"
        :style="getNodeStyle(node)"
        @mousedown.stop="startDragNode($event, node)"
        @click.stop="selectNode(node)"
        @dblclick.stop="openNodeEditor(node)"
      >
        <div class="node-header">
          <el-icon class="node-icon" :style="{ color: node.ui?.color }">
            <component :is="getNodeIcon(node)" />
          </el-icon>
          <span class="node-title">{{ node.name }}</span>
        </div>
        <div class="node-body">
          <div v-if="node.type === 'llm'" class="node-info">
            <span class="node-model">{{ node.llmConfig?.modelIdentifier || '未配置' }}</span>
          </div>
          <div v-else-if="node.type === 'start'" class="node-info">
            <span>输入</span>
          </div>
          <div v-else-if="node.type === 'end'" class="node-info">
            <span>输出</span>
          </div>
          <div v-else-if="node.type === 'http_request'" class="node-info">
            <span>HTTP</span>
          </div>
          <div v-else-if="node.type === 'code'" class="node-info">
            <span>代码</span>
          </div>
          <div v-else-if="node.type === 'condition'" class="node-info">
            <span>条件</span>
          </div>
        </div>
        <!-- 连接点 -->
        <div
          v-if="node.type !== 'end'"
          class="connector output"
          @mousedown.stop="startDrawEdge($event, node)"
        ></div>
        <div
          v-if="node.type !== 'start'"
          class="connector input"
          @mousedown.stop
          @mouseup.stop="finishDrawEdge($event, node)"
        ></div>
      </div>
    </div>

    <!-- 节点编辑对话框 -->
    <el-dialog
      v-model="nodeEditorVisible"
      title="编辑节点"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form v-if="editingNode" :model="editingNode" label-width="100px">
        <el-form-item label="节点名称">
          <el-input v-model="editingNode.name" placeholder="请输入节点名称" />
        </el-form-item>
        <el-form-item label="节点类型">
          <el-tag>{{ editingNode.type }}</el-tag>
        </el-form-item>

        <!-- LLM 节点特有配置 -->
        <template v-if="editingNode.type === 'llm'">
          <el-divider>LLM 配置</el-divider>
          <el-form-item label="模型">
            <el-input v-model="editingNode.llmConfig!.modelIdentifier" placeholder="如 dashscope_deepseek-v4-flash" />
          </el-form-item>
          <el-form-item label="系统提示词">
            <el-input
              v-model="editingNode.llmConfig!.systemPrompt"
              type="textarea"
              :rows="3"
              placeholder="请输入系统提示词"
            />
          </el-form-item>
          <el-form-item label="用户提示词">
            <el-input
              v-model="editingNode.llmConfig!.userPromptTemplate"
              type="textarea"
              :rows="3"
              placeholder="请输入用户提示词模板，支持 ${nodeId.field} 引用"
            />
          </el-form-item>
        </template>

        <!-- 开始节点配置 -->
        <template v-if="editingNode.type === 'start'">
          <el-divider>输入配置</el-divider>
          <el-form-item label="输入变量">
            <el-input
              v-model="editingNode.inputConfig"
              type="textarea"
              :rows="3"
              placeholder='定义输入变量，JSON 格式：{"userQuery": ""}'
            />
          </el-form-item>
        </template>

        <!-- 通用配置 -->
        <el-divider>高级配置</el-divider>
        <el-form-item label="最大重试">
          <el-input-number v-model="editingNode.maxRetry" :min="0" :max="10" />
        </el-form-item>
        <el-form-item label="图标颜色">
          <el-color-picker v-model="editingNode.ui.color" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeEditorVisible = false">取消</el-button>
        <el-button type="primary" @click="saveNodeEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import {
  ZoomIn,
  ZoomOut,
  FullScreen,
  VideoPlay,
  CircleCheck,
  Cpu,
  Document,
  Plus,
  Delete,
  Connection,
  Switch,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

interface Position {
  x: number
  y: number
}

interface NodeUI {
  icon?: string
  color?: string
  typeIdentifier?: string
}

interface LLMConfig {
  modelIdentifier?: string
  systemPrompt?: string
  userPromptTemplate?: string
}

interface FlowNode {
  id: string
  name: string
  type: 'start' | 'end' | 'llm' | 'http_request' | 'code' | 'condition'
  status: string
  progress: number
  retry: number
  maxRetry: number
  error: string | null
  input: Record<string, any>
  output: Record<string, any>
  position: Position
  ui: NodeUI
  llmConfig?: LLMConfig
  inputConfig?: string
}

interface FlowEdge {
  id: string
  name: string
  type: 'normal' | 'condition'
  fromNodeId: string
  fromNodeName: string | null
  toNodeId: string
  toNodeName: string | null
  condition?: string
}

interface FlowGraph {
  id: string
  name: string
  description: string
  nodes: FlowNode[]
  edges: FlowEdge[]
}

const props = defineProps<{
  graph: FlowGraph
}>()

const emit = defineEmits<{
  (e: 'update:graph', graph: FlowGraph): void
  (e: 'node-click', node: FlowNode): void
}>()

const containerRef = ref<HTMLElement>()
const canvasRef = ref<HTMLElement>()
const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const scale = ref(1)
const panX = ref(0)
const panY = ref(0)

// 节点编辑相关
const nodeEditorVisible = ref(false)
const editingNode = ref<FlowNode | null>(null)

// 边绘制相关
const isDrawingEdge = ref(false)
const drawingFromNode = ref<FlowNode | null>(null)
const drawingStartPos = ref<Position>({ x: 0, y: 0 })
const drawingCurrentPos = ref<Position>({ x: 0, y: 0 })

// 拖拽相关状态
let isDraggingNode = false
let dragNode: FlowNode | null = null
let dragOffset: Position = { x: 0, y: 0 }

// 深拷贝图数据用于编辑
const nodes = ref<FlowNode[]>(JSON.parse(JSON.stringify(props.graph.nodes)))
const edges = ref<FlowEdge[]>(JSON.parse(JSON.stringify(props.graph.edges)))

// 监听 props 变化
watch(() => props.graph, (newGraph) => {
  nodes.value = JSON.parse(JSON.stringify(newGraph.nodes))
  edges.value = JSON.parse(JSON.stringify(newGraph.edges))
}, { deep: true })

// 计算选中的节点是否可以删除
const canDeleteSelectedNode = computed(() => {
  if (!selectedNodeId.value) return false
  const node = nodes.value.find(n => n.id === selectedNodeId.value)
  return node && node.type !== 'start' && node.type !== 'end'
})

// 获取节点样式
function getNodeStyle(node: FlowNode) {
  return {
    left: (node.position.x * scale.value + panX.value) + 'px',
    top: (node.position.y * scale.value + panY.value) + 'px',
    transform: `scale(${scale.value})`,
    transformOrigin: 'top left',
  }
}

// 获取节点图标
function getNodeIcon(node: FlowNode) {
  switch (node.type) {
    case 'start': return VideoPlay
    case 'end': return CircleCheck
    case 'llm': return Cpu
    case 'http_request': return Connection
    case 'code': return Document
    case 'condition': return Switch
    default: return Document
  }
}

// 获取节点颜色
function getNodeColor(type: string): string {
  switch (type) {
    case 'start': return '#67c23a'
    case 'end': return '#909399'
    case 'llm': return '#409eff'
    case 'http_request': return '#E91E63'
    case 'code': return '#00BCD4'
    case 'condition': return '#FF9800'
    default: return '#909399'
  }
}

// 获取边路径
function getEdgePath(edge: FlowEdge): string {
  const fromNode = nodes.value.find(n => n.id === edge.fromNodeId)
  const toNode = nodes.value.find(n => n.id === edge.toNodeId)
  if (!fromNode || !toNode) return ''

  const fromX = fromNode.position.x + 150
  const fromY = fromNode.position.y + 40
  const toX = toNode.position.x
  const toY = toNode.position.y + 40

  return computeBezierPath(fromX, fromY, toX, toY)
}

// 获取边标签位置
function getEdgeLabelX(edge: FlowEdge): number {
  const fromNode = nodes.value.find(n => n.id === edge.fromNodeId)
  const toNode = nodes.value.find(n => n.id === edge.toNodeId)
  if (!fromNode || !toNode) return 0
  return (fromNode.position.x + 150 + toNode.position.x) / 2
}

function getEdgeLabelY(edge: FlowEdge): number {
  const fromNode = nodes.value.find(n => n.id === edge.fromNodeId)
  const toNode = nodes.value.find(n => n.id === edge.toNodeId)
  if (!fromNode || !toNode) return 0
  return (fromNode.position.y + 40 + toNode.position.y + 40) / 2 - 10
}

// 获取临时边路径
function getTempEdgePath(): string {
  const fromX = drawingStartPos.value.x
  const fromY = drawingStartPos.value.y
  const toX = drawingCurrentPos.value.x
  const toY = drawingCurrentPos.value.y

  return computeBezierPath(fromX, fromY, toX, toY)
}

// 计算贝塞尔曲线路径
function computeBezierPath(fromX: number, fromY: number, toX: number, toY: number): string {
  const midX = (fromX + toX) / 2
  return `M ${fromX} ${fromY} C ${midX} ${fromY}, ${midX} ${toY}, ${toX} ${toY}`
}

// 添加节点
function addNodeByType(type: string) {
  const nodeId = `${type}_${Date.now()}`
  const centerX = (-panX.value + 400) / scale.value
  const centerY = (-panY.value + 300) / scale.value

  const newNode: FlowNode = {
    id: nodeId,
    name: getNodeTypeLabel(type),
    type: type as FlowNode['type'],
    status: 'PENDING',
    progress: 0,
    retry: 0,
    maxRetry: 3,
    error: null,
    input: {},
    output: {},
    position: { x: centerX, y: centerY },
    ui: {
      icon: '',
      color: getNodeColor(type),
    },
  }

  // 为 LLM 节点添加默认配置
  if (type === 'llm') {
    newNode.llmConfig = {
      modelIdentifier: 'dashscope_deepseek-v4-flash',
      systemPrompt: '',
      userPromptTemplate: '',
    }
  }

  nodes.value.push(newNode)
  selectedNodeId.value = nodeId
  emitUpdate()
  ElMessage.success(`已添加 ${getNodeTypeLabel(type)}`)
}

// 获取节点类型标签
function getNodeTypeLabel(type: string): string {
  switch (type) {
    case 'llm': return 'LLM 节点'
    case 'http_request': return 'HTTP 请求'
    case 'code': return '代码节点'
    case 'condition': return '条件分支'
    default: return '新节点'
  }
}

// 删除选中节点
async function deleteSelectedNode() {
  if (!selectedNodeId.value) return

  const node = nodes.value.find(n => n.id === selectedNodeId.value)
  if (!node) return

  if (node.type === 'start' || node.type === 'end') {
    ElMessage.warning('不能删除开始或结束节点')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除节点 "${node.name}" 吗？`,
      '确认删除',
      { type: 'warning' }
    )

    // 删除与该节点相连的边
    edges.value = edges.value.filter(
      e => e.fromNodeId !== selectedNodeId.value && e.toNodeId !== selectedNodeId.value
    )

    // 删除节点
    nodes.value = nodes.value.filter(n => n.id !== selectedNodeId.value)
    selectedNodeId.value = null

    emitUpdate()
    ElMessage.success('节点已删除')
  } catch {
    // 用户取消
  }
}

// 删除选中边
async function deleteSelectedEdge() {
  if (!selectedEdgeId.value) return

  const edge = edges.value.find(e => e.id === selectedEdgeId.value)
  if (!edge) return

  try {
    await ElMessageBox.confirm(
      `确定要删除连线 "${edge.name}" 吗？`,
      '确认删除',
      { type: 'warning' }
    )

    edges.value = edges.value.filter(e => e.id !== selectedEdgeId.value)
    selectedEdgeId.value = null

    emitUpdate()
    ElMessage.success('连线已删除')
  } catch {
    // 用户取消
  }
}

// 选择边
function selectEdge(edge: FlowEdge) {
  selectedEdgeId.value = edge.id
  selectedNodeId.value = null
}

// 画布平移
let isPanning = false
let panStartX = 0
let panStartY = 0
let panStartPanX = 0
let panStartPanY = 0

function startPanCanvas(event: MouseEvent) {
  // 只有点击画布空白区域才触发平移
  const target = event.target as HTMLElement
  if (target.closest('.flow-node')) return
  if (target.closest('.connector')) return
  if (target.closest('.edge-hitarea')) return

  // 清除选中状态
  selectedNodeId.value = null
  selectedEdgeId.value = null

  isPanning = true
  panStartX = event.clientX
  panStartY = event.clientY
  panStartPanX = panX.value
  panStartPanY = panY.value
}

function onCanvasMouseMove(event: MouseEvent) {
  // 处理画布平移
  if (isPanning) {
    const dx = event.clientX - panStartX
    const dy = event.clientY - panStartY
    panX.value = panStartPanX + dx
    panY.value = panStartPanY + dy
    return
  }

  // 处理节点拖拽
  if (isDraggingNode && dragNode) {
    dragNode.position.x = (event.clientX - dragOffset.x - panX.value) / scale.value
    dragNode.position.y = (event.clientY - dragOffset.y - panY.value) / scale.value
    return
  }

  // 注意：边绘制的鼠标移动已通过全局事件监听器处理
}

function onCanvasMouseUp(_event: MouseEvent) {
  // 结束画布平移
  if (isPanning) {
    isPanning = false
    return
  }

  // 结束节点拖拽
  if (isDraggingNode) {
    isDraggingNode = false
    dragNode = null
    emitUpdate()
    return
  }

  // 取消边绘制（如果没在输入连接点上释放）
  if (isDrawingEdge.value) {
    isDrawingEdge.value = false
    drawingFromNode.value = null
  }
}

// 拖拽节点
function startDragNode(event: MouseEvent, node: FlowNode) {
  // 如果正在绘制边，不拖拽节点
  if (isDrawingEdge.value) return

  isDraggingNode = true
  dragNode = node
  const nodeScreenX = node.position.x * scale.value + panX.value
  const nodeScreenY = node.position.y * scale.value + panY.value
  dragOffset = {
    x: event.clientX - nodeScreenX,
    y: event.clientY - nodeScreenY,
  }
}

// 绘制边
function startDrawEdge(_event: MouseEvent, node: FlowNode) {
  isDrawingEdge.value = true
  drawingFromNode.value = node

  // 计算输出连接点位置
  drawingStartPos.value = {
    x: (node.position.x + 150),
    y: (node.position.y + 40),
  }
  drawingCurrentPos.value = { ...drawingStartPos.value }

  // 附加全局事件监听器
  document.addEventListener('mousemove', onDrawEdgeMove)
  document.addEventListener('mouseup', onDrawEdgeUp)
}

function onDrawEdgeMove(event: MouseEvent) {
  if (!isDrawingEdge.value) return
  drawingCurrentPos.value = {
    x: (event.clientX - panX.value) / scale.value,
    y: (event.clientY - panY.value) / scale.value,
  }
}

function onDrawEdgeUp(_event: MouseEvent) {
  // 如果没有在输入连接点上释放，取消绘制
  if (isDrawingEdge.value) {
    isDrawingEdge.value = false
    drawingFromNode.value = null
  }
  // 移除全局事件监听器
  document.removeEventListener('mousemove', onDrawEdgeMove)
  document.removeEventListener('mouseup', onDrawEdgeUp)
}

function finishDrawEdge(_event: MouseEvent, toNode: FlowNode) {
  if (!isDrawingEdge.value || !drawingFromNode.value) {
    return
  }

  const fromNode = drawingFromNode.value

  // 不能连接到自身
  if (fromNode.id === toNode.id) {
    cancelDrawEdge()
    ElMessage.warning('不能连接到自身')
    return
  }

  // 不能从 end 节点连出
  if (fromNode.type === 'end') {
    cancelDrawEdge()
    ElMessage.warning('不能从结束节点连出')
    return
  }

  // 不能连入 start 节点
  if (toNode.type === 'start') {
    cancelDrawEdge()
    ElMessage.warning('不能连接到开始节点')
    return
  }

  // 检查是否已存在相同的边
  const existingEdge = edges.value.find(
    e => e.fromNodeId === fromNode.id && e.toNodeId === toNode.id
  )
  if (existingEdge) {
    cancelDrawEdge()
    ElMessage.warning('已存在相同的连接')
    return
  }

  // 创建新边
  const edgeId = `e_${Date.now()}`
  const newEdge: FlowEdge = {
    id: edgeId,
    name: `${fromNode.name} -> ${toNode.name}`,
    type: 'normal',
    fromNodeId: fromNode.id,
    fromNodeName: fromNode.name,
    toNodeId: toNode.id,
    toNodeName: toNode.name,
  }

  edges.value.push(newEdge)
  emitUpdate()

  // 清理状态
  isDrawingEdge.value = false
  drawingFromNode.value = null
  document.removeEventListener('mousemove', onDrawEdgeMove)
  document.removeEventListener('mouseup', onDrawEdgeUp)

  ElMessage.success('连接已创建')
}

// 取消绘制边
function cancelDrawEdge() {
  isDrawingEdge.value = false
  drawingFromNode.value = null
  document.removeEventListener('mousemove', onDrawEdgeMove)
  document.removeEventListener('mouseup', onDrawEdgeUp)
}

// 选择节点
function selectNode(node: FlowNode) {
  selectedNodeId.value = node.id
  selectedEdgeId.value = null
  emit('node-click', node)
}

// 打开节点编辑器
function openNodeEditor(node: FlowNode) {
  // 深拷贝节点数据用于编辑
  editingNode.value = JSON.parse(JSON.stringify(node))
  // 确保 llmConfig 存在
  if (editingNode.value?.type === 'llm' && !editingNode.value.llmConfig) {
    editingNode.value.llmConfig = {
      modelIdentifier: '',
      systemPrompt: '',
      userPromptTemplate: '',
    }
  }
  // 确保 ui 存在
  if (editingNode.value && !editingNode.value.ui) {
    editingNode.value.ui = {}
  }
  // 确保 start 节点有 inputConfig
  if (editingNode.value?.type === 'start' && !editingNode.value.inputConfig) {
    editingNode.value.inputConfig = JSON.stringify(editingNode.value.input, null, 2)
  }
  nodeEditorVisible.value = true
}

// 保存节点编辑
function saveNodeEdit() {
  if (!editingNode.value) return

  // 处理 start 节点的 inputConfig
  if (editingNode.value.type === 'start' && editingNode.value.inputConfig) {
    try {
      editingNode.value.input = JSON.parse(editingNode.value.inputConfig)
    } catch {
      // 如果 JSON 解析失败，保持原样
    }
  }

  // 找到并更新节点
  const index = nodes.value.findIndex(n => n.id === editingNode.value!.id)
  if (index !== -1) {
    nodes.value[index] = JSON.parse(JSON.stringify(editingNode.value))
    emitUpdate()
  }

  nodeEditorVisible.value = false
  editingNode.value = null
}

// 缩放
function zoomIn() {
  scale.value = Math.min(2, scale.value + 0.1)
}

function zoomOut() {
  scale.value = Math.max(0.3, scale.value - 0.1)
}

function resetView() {
  scale.value = 1
  panX.value = 0
  panY.value = 0
}

// 发送更新
function emitUpdate() {
  const graph: FlowGraph = {
    ...props.graph,
    nodes: nodes.value,
    edges: edges.value,
  }
  emit('update:graph', graph)
}

onMounted(() => {
  // 聚焦容器以接收键盘事件
  containerRef.value?.focus()
})
</script>

<style scoped>
.flow-chart-editor {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
  background-color: #f5f7fa;
  background-image: radial-gradient(circle, #d0d0d0 1px, transparent 1px);
  background-size: 20px 20px;
  outline: none;
}

.flow-chart-toolbar {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 8px;
  background: white;
  padding: 8px 12px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.toolbar-group {
  display: flex;
  align-items: center;
}

.flow-chart-canvas {
  width: 100%;
  height: 100%;
  position: relative;
  cursor: grab;
  outline: none;
}

.flow-chart-canvas:active {
  cursor: grabbing;
}

.connections-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.edge-hitarea {
  fill: none;
  stroke: transparent;
  stroke-width: 15;
  pointer-events: stroke;
  cursor: pointer;
}

.edge-path {
  fill: none;
  stroke: #909399;
  stroke-width: 2;
  pointer-events: none;
}

.edge-label {
  pointer-events: none;
}

.edge-path.condition {
  stroke-dasharray: 5, 5;
}

.edge-path.selected {
  stroke: #409eff;
  stroke-width: 3;
}

.edge-path-temp {
  fill: none;
  stroke: transparent;
  stroke-width: 2;
  pointer-events: none;
}

.edge-label {
  font-size: 12px;
  fill: #606266;
  text-anchor: middle;
  pointer-events: none;
}

.flow-node {
  position: absolute;
  width: 150px;
  background: white;
  border: 2px solid #dcdfe6;
  border-radius: 8px;
  cursor: move;
  user-select: none;
  transition: box-shadow 0.2s;
  z-index: 2;
}

.flow-node:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.flow-node.selected {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.flow-node.start {
  border-color: #67c23a;
}

.flow-node.end {
  border-color: #909399;
}

.flow-node.llm {
  border-color: #409eff;
}

.flow-node.http_request {
  border-color: #E91E63;
}

.flow-node.code {
  border-color: #00BCD4;
}

.flow-node.condition {
  border-color: #FF9800;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
  border-radius: 6px 6px 0 0;
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-body {
  padding: 8px 12px;
}

.node-info {
  font-size: 12px;
  color: #909399;
}

.node-model {
  display: inline-block;
  padding: 2px 6px;
  background: #ecf5ff;
  border-radius: 4px;
  color: #409eff;
  font-size: 11px;
}

.connector {
  position: absolute;
  width: 12px;
  height: 12px;
  background: #409eff;
  border: 2px solid white;
  border-radius: 50%;
  cursor: crosshair;
  z-index: 5;
  transition: transform 0.2s;
}

.connector:hover {
  transform: scale(1.3);
  background: #66b1ff;
}

.connector.output {
  right: -6px;
  top: 50%;
  transform: translateY(-50%);
}

.connector.output:hover {
  transform: translateY(-50%) scale(1.3);
}

.connector.input {
  left: -6px;
  top: 50%;
  transform: translateY(-50%);
}

.connector.input:hover {
  transform: translateY(-50%) scale(1.3);
}
</style>
