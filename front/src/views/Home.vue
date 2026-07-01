<template>
  <div class="home-container">
    <!-- 折叠状态下的侧边栏 -->
    <aside v-if="sidebarCollapsed" class="sidebar-collapsed">
      <el-tooltip content="展开侧边栏" placement="right">
        <el-button type="primary" link class="expand-btn" @click="toggleSidebar">
          <el-icon><Expand /></el-icon>
        </el-button>
      </el-tooltip>
    </aside>

    <!-- 左侧边栏：Agent 选择和会话管理 -->
    <aside v-else class="sidebar">
      <div class="sidebar-header">
        <div class="header-top">
          <h2>JAWF</h2>
          <div class="header-actions">
            <el-tooltip content="折叠侧边栏" placement="bottom">
              <el-button type="primary" link class="toggle-sidebar-btn" @click="toggleSidebar">
                <el-icon><Fold /></el-icon>
              </el-button>
            </el-tooltip>
            <div class="connection-status" :class="{ connected: wsStore.connected }">
              <span class="status-dot"></span>
              <span class="status-text">{{ wsStore.connected ? '已连接' : '未连接' }}</span>
            </div>
          </div>
        </div>
        <span class="subtitle">AI Workflow</span>
      </div>

      <!-- Agent 选择区域 -->
      <div class="agent-section">
        <div class="section-header">
          <span class="section-title">Agents</span>
          <el-button type="primary" link @click="showCreateAgentDialog">
            <el-icon><Plus /></el-icon>
            新建
          </el-button>
        </div>

        <div class="agent-list">
          <div
            v-for="agent in agentStore.enabledAgents"
            :key="agent.agentId"
            class="agent-item"
            :class="{ active: agent.agentId === agentStore.currentAgentId }"
            @click="switchAgent(agent.agentId)"
          >
            <div class="agent-info">
              <div class="agent-name">{{ agent.name }}</div>
              <div class="agent-model">{{ agent.defaultModel }}</div>
            </div>
            <div class="agent-actions">
              <el-button type="primary" link size="small" @click.stop="showEditAgentDialog(agent)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button type="danger" link size="small" @click.stop="handleDeleteAgent(agent.agentId)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <el-empty v-if="agentStore.enabledAgents.length === 0" description="暂无 Agent" :image-size="60" />
        </div>
      </div>

      <!-- 会话列表区域 -->
      <div class="conversation-section">
        <div class="section-header">
          <span class="section-title">会话</span>
          <el-button type="primary" link :disabled="!agentStore.currentAgentId" @click="handleCreateConversation">
            <el-icon><Plus /></el-icon>
            新建
          </el-button>
        </div>

        <div class="conversation-list">
          <div
            v-for="conv in chatStore.conversations"
            :key="conv.conversationId"
            class="conversation-item"
            :class="{ active: conv.conversationId === chatStore.currentConversationId }"
            @click="switchConversation(conv.conversationId)"
          >
            <el-icon><ChatDotRound /></el-icon>
            <span class="conversation-title">{{ conv.title }}</span>
            <el-button type="danger" link size="small" class="delete-btn" @click.stop="handleDeleteConversation(conv.conversationId)">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
          <el-empty v-if="chatStore.conversations.length === 0" description="暂无会话" :image-size="60" />
        </div>
      </div>
    </aside>

    <!-- 右侧主内容区：聊天内容 -->
    <main class="main-content">
      <div v-if="!agentStore.currentAgent" class="empty-state">
        <el-icon class="empty-icon"><ChatLineRound /></el-icon>
        <h3>欢迎使用 JAWF</h3>
        <p>请在左侧选择或创建一个 Agent 开始对话</p>
      </div>

      <div v-else class="chat-container" :class="{ 'split-view': chatStore.isRequirementClarificationPhase || chatStore.isPlanPhase }">
        <!-- 聊天头部 -->
        <header class="chat-header">
          <div class="chat-agent-info">
            <span class="agent-name">{{ agentStore.currentAgent.name }}</span>
            <el-tag size="small" type="info">{{ agentStore.currentAgent.defaultModel }}</el-tag>
          </div>
        </header>

        <!-- Graph 执行状态栏 -->
        <div v-if="chatStore.graphStatus" class="graph-status-bar">
          <div class="status-info">
            <el-icon class="status-icon" :class="getStatusClass(chatStore.graphStatus.status)">
              <Loading v-if="isActiveStatus(chatStore.graphStatus.status)" />
              <CircleCheck v-else-if="chatStore.graphStatus.status === 'completed'" />
              <CircleClose v-else-if="chatStore.graphStatus.status === 'failed'" />
              <InfoFilled v-else />
            </el-icon>
            <span class="status-label">{{ chatStore.graphStatus.statusLabel }}</span>
            <span class="current-node">节点: {{ chatStore.graphStatus.currentNode }}</span>
          </div>
          <div class="status-progress">
            <el-progress
              :percentage="chatStore.graphStatus.progress"
              :status="getProgressStatus(chatStore.graphStatus.status)"
              :stroke-width="6"
              :show-text="false"
            />
          </div>
          <div v-if="chatStore.graphStatus.errorMessage" class="status-error">
            {{ chatStore.graphStatus.errorMessage }}
          </div>
        </div>

        <!-- 聊天内容区域（需求澄清阶段分为左右两栏） -->
        <div class="chat-content-area">
          <!-- 左侧：聊天栏 -->
          <div class="chat-panel">
            <!-- 消息列表 -->
            <div class="message-list" ref="messageListRef">
              <div v-if="chatStore.messages.length === 0 && !chatStore.streamingContent" class="empty-messages">
                <el-icon class="empty-icon"><ChatRound /></el-icon>
                <p>开始新的对话</p>
              </div>
              <div
                v-for="msg in chatStore.messages"
                :key="msg.id"
                class="message-item"
                :class="msg.type"
              >
                <div class="message-avatar">
                  <el-icon v-if="msg.type === 'user'"><User /></el-icon>
                  <el-icon v-else><Monitor /></el-icon>
                </div>
                <div class="message-content">
                  <!-- 用户消息：纯文本显示 -->
                  <div v-if="msg.type === 'user'" class="message-text">{{ msg.content }}</div>
                  <!-- 助手消息：Markdown 渲染 -->
                  <div v-else class="message-text markdown">
                    <MarkdownRenderer :content="msg.content" />
                  </div>
                  <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
                </div>
              </div>

              <!-- 流式输出临时消息 -->
              <div v-if="chatStore.streamingContent" class="message-item assistant">
                <div class="message-avatar">
                  <el-icon><Monitor /></el-icon>
                </div>
                <div class="message-content">
                  <div class="message-text streaming markdown">
                    <MarkdownRenderer :content="chatStore.streamingContent" />
                    <span class="cursor">|</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 输入区域 -->
            <div class="input-area">
              <el-input
                v-model="inputMessage"
                type="textarea"
                :rows="3"
                placeholder="输入消息..."
                :disabled="!chatStore.currentConversationId"
                @keydown.enter.ctrl="handleSendMessage"
              />
              <div class="input-actions">
                <span class="input-tip">Ctrl + Enter 发送</span>
                <el-button
                  type="primary"
                  :loading="chatStore.sending"
                  :disabled="!inputMessage.trim() || !chatStore.currentConversationId"
                  @click="handleSendMessage"
                >
                  发送
                </el-button>
              </div>
            </div>
          </div>

          <!-- 右侧：需求文档编辑器（仅需求澄清阶段显示，可横向拉伸） -->
          <div
            v-if="chatStore.isRequirementClarificationPhase"
            class="requirement-panel"
            :style="{ width: requirementPanelWidth + 'px' }"
          >
            <!-- 拉伸手柄 -->
            <div
              class="resize-handle"
              @mousedown="startResize"
            ></div>
            <div class="requirement-header">
              <span class="requirement-title">需求文档</span>
              <div class="requirement-actions">
                <!-- 保存状态 -->
                <span class="save-status" :class="{ saved: requirementDocSaved, unsaved: !requirementDocSaved }">
                  <el-icon v-if="requirementDocSaved"><CircleCheck /></el-icon>
                  <el-icon v-else><Warning /></el-icon>
                  {{ requirementDocSaved ? '已保存' : '未保存' }}
                </span>
                <!-- 手动保存按钮 -->
                <el-button
                  type="primary"
                  size="small"
                  :disabled="requirementDocSaved"
                  :loading="savingRequirementDoc"
                  @click="saveRequirementDoc"
                >
                  <el-icon><Check /></el-icon>
                  保存
                </el-button>
              </div>
            </div>
            <div class="requirement-editor">
              <textarea
                v-if="chatStore.requirementDoc"
                v-model="editableRequirementDoc"
                class="requirement-textarea"
                placeholder="需求文档内容..."
                @input="onRequirementDocChange"
              ></textarea>
              <div v-else class="empty-requirement">
                <el-icon class="empty-icon"><Document /></el-icon>
                <p>等待生成需求文档...</p>
              </div>
            </div>
          </div>

          <!-- 右侧：工作流图编辑器（仅计划阶段显示） -->
          <div
            v-if="chatStore.isPlanPhase"
            class="plan-panel"
            :style="{ width: planPanelWidth + 'px' }"
          >
            <!-- 拉伸手柄 -->
            <div
              class="resize-handle"
              @mousedown="startResizePlan"
            ></div>
            <div class="plan-header">
              <span class="plan-title">工作流图</span>
              <div class="plan-actions">
                <!-- 保存状态 -->
                <span class="save-status" :class="{ saved: planJsonSaved, unsaved: !planJsonSaved }">
                  <el-icon v-if="planJsonSaved"><CircleCheck /></el-icon>
                  <el-icon v-else><Warning /></el-icon>
                  {{ planJsonSaved ? '已保存' : '未保存' }}
                </span>
                <!-- 手动保存按钮 -->
                <el-button
                  type="primary"
                  size="small"
                  :disabled="planJsonSaved"
                  :loading="savingPlanJson"
                  @click="savePlanJson"
                >
                  <el-icon><Check /></el-icon>
                  保存
                </el-button>
              </div>
            </div>
            <div class="plan-editor">
              <FlowChartEditor
                v-if="parsedPlanGraph"
                :graph="parsedPlanGraph"
                @update:graph="onPlanGraphUpdate"
              />
              <div v-else class="empty-plan">
                <el-icon class="empty-icon"><Document /></el-icon>
                <p>等待生成工作流图...</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- 创建/编辑 Agent 对话框 -->
    <el-dialog
      v-model="agentDialogVisible"
      :title="isEditing ? '编辑 Agent' : '创建 Agent'"
      width="500px"
    >
      <el-form :model="agentForm" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="agentForm.name" placeholder="请输入 Agent 名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="agentForm.description" type="textarea" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="模型" required>
          <el-input v-model="agentForm.defaultModel" placeholder="如 dashscope_deepseek-v4-flash" />
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input
            v-model="agentForm.systemPrompt"
            type="textarea"
            :rows="3"
            placeholder="请输入系统提示词"
          />
        </el-form-item>
        <el-form-item label="支持并行">
          <el-switch v-model="agentForm.parallelEnabled" />
        </el-form-item>
        <el-form-item v-if="agentForm.parallelEnabled" label="最大并行数">
          <el-input-number v-model="agentForm.maxParallel" :min="1" :max="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="agentDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveAgent" :loading="saving">
          {{ isEditing ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Edit,
  Delete,
  ChatDotRound,
  ChatLineRound,
  ChatRound,
  User,
  Monitor,
  Close,
  Loading,
  CircleCheck,
  CircleClose,
  InfoFilled,
  Document,
  Fold,
  Expand,
  Warning,
  Check,
} from '@element-plus/icons-vue'
import { useAgentStore } from '@/stores/agent'
import { useChatStore } from '@/stores/chat'
import { useWebSocketStore } from '@/stores/websocket'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import FlowChartEditor from '@/components/FlowChartEditor.vue'
import type { Agent } from '@/api/agent'

interface FlowGraph {
  id: string
  name: string
  description: string
  nodes: any[]
  edges: any[]
}

const agentStore = useAgentStore()
const chatStore = useChatStore()
const wsStore = useWebSocketStore()

// 输入消息
const inputMessage = ref('')
const messageListRef = ref<HTMLElement>()

// 侧边栏折叠状态
const sidebarCollapsed = ref(false)

// 需求文档面板宽度（可拉伸）
const requirementPanelWidth = ref(400)
const isResizing = ref(false)

// Agent 对话框相关
const agentDialogVisible = ref(false)
const isEditing = ref(false)
const editingAgentId = ref<string | null>(null)
const saving = ref(false)
const agentForm = ref({
  name: '',
  description: '',
  defaultModel: 'dashscope_deepseek-v4-flash',
  systemPrompt: '',
  parallelEnabled: false,
  maxParallel: 1,
})

// 需求文档编辑器相关
const editableRequirementDoc = ref('')
const requirementDocSaved = ref(true)
const savingRequirementDoc = ref(false)
let autoSaveTimer: ReturnType<typeof setTimeout> | null = null

// 计划面板相关
const planPanelWidth = ref(600)
const planJsonSaved = ref(true)
const savingPlanJson = ref(false)
let planAutoSaveTimer: ReturnType<typeof setTimeout> | null = null

// 解析后的计划图数据
const parsedPlanGraph = ref<FlowGraph | null>(null)

// 监听需求文档变化，同步到编辑器
watch(
  () => chatStore.requirementDoc,
  (newDoc) => {
    if (newDoc && requirementDocSaved.value) {
      editableRequirementDoc.value = newDoc
    }
  },
  { immediate: true }
)

// 监听计划 JSON 变化，解析为图数据
watch(
  () => chatStore.planJson,
  (newJson) => {
    if (newJson) {
      try {
        parsedPlanGraph.value = JSON.parse(newJson)
        planJsonSaved.value = true
      } catch (e) {
        console.error('解析计划 JSON 失败:', e)
        parsedPlanGraph.value = null
      }
    } else {
      parsedPlanGraph.value = null
    }
  },
  { immediate: true }
)

// 需求文档内容变化时的处理
function onRequirementDocChange() {
  requirementDocSaved.value = false

  // 清除之前的定时器
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer)
  }

  // 设置新的定时器，5秒后自动保存
  autoSaveTimer = setTimeout(() => {
    saveRequirementDoc()
  }, 5000)
}

// 保存需求文档到后端
async function saveRequirementDoc() {
  if (requirementDocSaved.value || savingRequirementDoc.value) {
    return
  }

  if (!chatStore.currentConversationId) {
    return
  }

  savingRequirementDoc.value = true
  try {
    await chatStore.saveRequirementDoc(editableRequirementDoc.value)
    requirementDocSaved.value = true
    console.log('需求文档已保存')
  } catch (error) {
    console.error('保存需求文档失败:', error)
    ElMessage.error('保存需求文档失败')
  } finally {
    savingRequirementDoc.value = false
  }
}

// 工作流图更新时的处理
function onPlanGraphUpdate(graph: FlowGraph) {
  parsedPlanGraph.value = graph
  planJsonSaved.value = false

  // 清除之前的定时器
  if (planAutoSaveTimer) {
    clearTimeout(planAutoSaveTimer)
  }

  // 设置新的定时器，5秒后自动保存
  planAutoSaveTimer = setTimeout(() => {
    savePlanJson()
  }, 5000)
}

// 保存计划 JSON 到后端
async function savePlanJson() {
  if (planJsonSaved.value || savingPlanJson.value) {
    return
  }

  if (!parsedPlanGraph.value) {
    return
  }

  savingPlanJson.value = true
  try {
    await chatStore.savePlanJson(JSON.stringify(parsedPlanGraph.value))
    planJsonSaved.value = true
    console.log('工作流图已保存')
  } catch (error) {
    console.error('保存工作流图失败:', error)
    ElMessage.error('保存工作流图失败')
  } finally {
    savingPlanJson.value = false
  }
}

// 开始拉伸计划面板
function startResizePlan(event: MouseEvent) {
  const startX = event.clientX
  const startWidth = planPanelWidth.value

  const onMouseMove = (e: MouseEvent) => {
    const diff = startX - e.clientX
    const newWidth = Math.max(300, Math.min(1000, startWidth + diff))
    planPanelWidth.value = newWidth
  }

  const onMouseUp = () => {
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

// 初始化
onMounted(async () => {
  // 连接 WebSocket
  await wsStore.connect()

  // 加载 Agent 列表
  await agentStore.fetchAgents()
  if (agentStore.currentAgentId) {
    await chatStore.fetchConversations(agentStore.currentAgentId)
  }
})

// 组件卸载时断开 WebSocket
onUnmounted(() => {
  wsStore.disconnect()
})

// 监听当前 Agent 变化，加载会话列表
watch(
  () => agentStore.currentAgentId,
  async (newAgentId) => {
    if (newAgentId) {
      chatStore.clear()
      await chatStore.fetchConversations(newAgentId)
    }
  }
)

// 监听消息变化，自动滚动到底部
watch(
  () => chatStore.messages.length,
  () => {
    nextTick(() => {
      if (messageListRef.value) {
        messageListRef.value.scrollTop = messageListRef.value.scrollHeight
      }
    })
  }
)

// 切换 Agent
async function switchAgent(agentId: string) {
  agentStore.setCurrentAgent(agentId)
}

// 切换会话
async function switchConversation(conversationId: string) {
  await chatStore.setCurrentConversation(conversationId)
}

// 切换侧边栏折叠状态
function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

// 开始拉伸需求文档面板
function startResize(event: MouseEvent) {
  isResizing.value = true
  const startX = event.clientX
  const startWidth = requirementPanelWidth.value

  const onMouseMove = (e: MouseEvent) => {
    if (!isResizing.value) return
    const diff = startX - e.clientX
    const newWidth = Math.max(250, Math.min(800, startWidth + diff))
    requirementPanelWidth.value = newWidth
  }

  const onMouseUp = () => {
    isResizing.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

// 显示创建 Agent 对话框
function showCreateAgentDialog() {
  isEditing.value = false
  editingAgentId.value = null
  agentForm.value = {
    name: '',
    description: '',
    defaultModel: 'dashscope_deepseek-v4-flash',
    systemPrompt: '',
    parallelEnabled: false,
    maxParallel: 1,
  }
  agentDialogVisible.value = true
}

// 显示编辑 Agent 对话框
function showEditAgentDialog(agent: Agent) {
  isEditing.value = true
  editingAgentId.value = agent.agentId
  agentForm.value = {
    name: agent.name,
    description: agent.description || '',
    defaultModel: agent.defaultModel,
    systemPrompt: agent.systemPrompt || '',
    parallelEnabled: agent.parallelEnabled || false,
    maxParallel: agent.maxParallel || 1,
  }
  agentDialogVisible.value = true
}

// 保存 Agent
async function handleSaveAgent() {
  if (!agentForm.value.name.trim()) {
    ElMessage.warning('请输入 Agent 名称')
    return
  }
  if (!agentForm.value.defaultModel.trim()) {
    ElMessage.warning('请输入模型标识')
    return
  }

  saving.value = true
  try {
    if (isEditing.value && editingAgentId.value) {
      await agentStore.modifyAgent(editingAgentId.value, agentForm.value)
      ElMessage.success('Agent 已更新')
    } else {
      await agentStore.addAgent(agentForm.value)
      ElMessage.success('Agent 已创建')
    }
    agentDialogVisible.value = false
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

// 删除 Agent
async function handleDeleteAgent(agentId: string) {
  try {
    await ElMessageBox.confirm('确定要删除这个 Agent 吗？', '确认删除', {
      type: 'warning',
    })
    await agentStore.removeAgent(agentId)
    ElMessage.success('Agent 已删除')
  } catch (error) {
    // 用户取消
  }
}

// 创建新会话
async function handleCreateConversation() {
  if (!agentStore.currentAgentId) {
    ElMessage.warning('请先选择一个 Agent')
    return
  }
  try {
    await chatStore.addConversation(agentStore.currentAgentId)
    ElMessage.success('新会话已创建')
  } catch (error) {
    ElMessage.error('创建会话失败')
  }
}

// 删除会话
async function handleDeleteConversation(conversationId: string) {
  try {
    await ElMessageBox.confirm('确定要删除这个会话吗？', '确认删除', {
      type: 'warning',
    })
    await chatStore.removeConversation(conversationId)
    ElMessage.success('会话已删除')
  } catch (error) {
    // 用户取消
  }
}

// 发送消息
async function handleSendMessage() {
  if (!inputMessage.value.trim() || !agentStore.currentAgentId) {
    return
  }

  try {
    await chatStore.send(agentStore.currentAgentId, inputMessage.value.trim())
    inputMessage.value = ''
  } catch (error) {
    ElMessage.error('发送失败')
  }
}

// 格式化时间
function formatTime(time?: string) {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

// Graph 状态相关辅助函数
import { GraphStatus } from '@/api/websocket'

function isActiveStatus(status: string): boolean {
  return status !== GraphStatus.COMPLETED && status !== GraphStatus.FAILED
}

function getStatusClass(status: string): string {
  if (status === GraphStatus.COMPLETED) return 'status-success'
  if (status === GraphStatus.FAILED) return 'status-danger'
  return 'status-loading'
}

function getProgressStatus(status: string): string {
  if (status === GraphStatus.COMPLETED) return 'success'
  if (status === GraphStatus.FAILED) return 'exception'
  return ''
}
</script>

<style scoped>
.home-container {
  display: flex;
  height: 100vh;
  background-color: #f5f7fa;
}

/* 侧边栏 */
.sidebar {
  width: 280px;
  background-color: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.3s;
}

/* 折叠状态下的侧边栏 */
.sidebar-collapsed {
  width: 48px;
  background-color: #f5f7fa;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 16px;
}

.expand-btn {
  font-size: 20px;
  color: #409eff;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid #e4e7ed;
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  color: #fff;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.sidebar-header .subtitle {
  font-size: 12px;
  opacity: 0.8;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toggle-sidebar-btn {
  color: #fff;
  opacity: 0.8;
  transition: opacity 0.2s;
}

.toggle-sidebar-btn:hover {
  opacity: 1;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  opacity: 0.9;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #f56c6c;
  transition: background-color 0.3s;
}

.connection-status.connected .status-dot {
  background-color: #67c23a;
}

/* Agent 区域 */
.agent-section {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.agent-list {
  max-height: 200px;
  overflow-y: auto;
}

.agent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background-color: #f5f7fa;
}

.agent-item:hover {
  background-color: #ecf5ff;
}

.agent-item.active {
  background-color: #ecf5ff;
  border: 1px solid #409eff;
}

.agent-info {
  flex: 1;
  min-width: 0;
}

.agent-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-model {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.agent-item:hover .agent-actions {
  opacity: 1;
}

/* 会话区域 */
.conversation-section {
  flex: 1;
  padding: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background-color: #f5f7fa;
  position: relative;
}

.conversation-item:hover {
  background-color: #ecf5ff;
}

.conversation-item.active {
  background-color: #ecf5ff;
  border: 1px solid #409eff;
}

.conversation-item .el-icon {
  margin-right: 8px;
  color: #909399;
}

.conversation-title {
  flex: 1;
  font-size: 14px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}

/* 主内容区 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 空状态 */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #909399;
}

.empty-state .empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  color: #c0c4cc;
}

.empty-state h3 {
  margin: 0 0 8px;
  font-size: 18px;
  color: #303133;
}

.empty-state p {
  margin: 0;
  font-size: 14px;
}

/* 聊天容器 */
.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  padding: 16px 24px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #fff;
}

.chat-agent-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-agent-info .agent-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

/* 消息列表 */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background-color: #f5f7fa;
}

.empty-messages {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.empty-messages .empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: #c0c4cc;
}

.message-item {
  display: flex;
  margin-bottom: 24px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.message-item.user .message-avatar {
  background-color: #409eff;
  color: #fff;
}

.message-item.assistant .message-avatar,
.message-item.system .message-avatar {
  background-color: #67c23a;
  color: #fff;
}

.message-content {
  max-width: 70%;
  margin: 0 12px;
}

.message-item.user .message-content {
  text-align: right;
}

.message-text {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.message-item.user .message-text {
  background-color: #409eff;
  color: #fff;
  border-top-right-radius: 4px;
}

.message-item.assistant .message-text,
.message-item.system .message-text {
  background-color: #fff;
  color: #303133;
  border-top-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

/* Markdown 消息样式 */
.message-text.markdown {
  padding: 16px 20px;
  max-width: 100%;
}

.message-text.markdown :deep(.markdown-body) {
  font-size: 14px;
}

.message-text.streaming {
  position: relative;
}

.message-text.streaming.markdown {
  padding: 16px 20px;
}

.message-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* 输入区域 */
.input-area {
  padding: 16px 24px;
  background-color: #fff;
  border-top: 1px solid #e4e7ed;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.input-tip {
  font-size: 12px;
  color: #909399;
}

/* 流式输出光标样式 */
.cursor {
  display: inline-block;
  animation: blink 0.7s infinite;
  color: #409eff;
  font-weight: bold;
}

@keyframes blink {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

/* Graph 执行状态栏 */
.graph-status-bar {
  padding: 12px 24px;
  background-color: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.status-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.status-icon {
  font-size: 16px;
}

.status-icon.status-loading {
  color: #409eff;
  animation: rotate 1s linear infinite;
}

.status-icon.status-success {
  color: #67c23a;
}

.status-icon.status-danger {
  color: #f56c6c;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.status-label {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.current-node {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}

.status-progress {
  width: 100%;
}

.status-error {
  margin-top: 8px;
  font-size: 12px;
  color: #f56c6c;
  padding: 8px;
  background-color: #fef0f0;
  border-radius: 4px;
}

/* 聊天内容区域（分栏布局） */
.chat-content-area {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧聊天栏 */
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 需求澄清阶段：分栏布局 */
.chat-container.split-view .chat-content-area {
  display: flex;
}

.chat-container.split-view .chat-panel {
  flex: 1;
  min-width: 0;
}

/* 右侧需求文档面板（可拉伸） */
.requirement-panel {
  min-width: 250px;
  max-width: 800px;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background-color: #fff;
  position: relative;
}

/* 拉伸手柄 */
.resize-handle {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  cursor: col-resize;
  background-color: transparent;
  transition: background-color 0.2s;
  z-index: 10;
}

.resize-handle:hover {
  background-color: #409eff;
}

.requirement-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #f5f7fa;
}

.requirement-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.requirement-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.save-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.save-status.saved {
  color: #67c23a;
}

.save-status.unsaved {
  color: #e6a23c;
}

.requirement-editor {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.requirement-textarea {
  flex: 1;
  width: 100%;
  padding: 16px;
  border: none;
  outline: none;
  resize: none;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
  background-color: #fff;
  box-sizing: border-box;
}

.requirement-textarea:focus {
  background-color: #fafbfc;
}

.empty-requirement {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.empty-requirement .empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: #c0c4cc;
}

.empty-requirement p {
  font-size: 14px;
}

/* 右侧工作流图面板（可拉伸） */
.plan-panel {
  min-width: 300px;
  max-width: 1000px;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background-color: #fff;
  position: relative;
}

.plan-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #f5f7fa;
}

.plan-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.plan-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.plan-editor {
  flex: 1;
  overflow: hidden;
}

.empty-plan {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.empty-plan .empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: #c0c4cc;
}

.empty-plan p {
  font-size: 14px;
}
</style>
