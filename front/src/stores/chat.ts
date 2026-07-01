import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getMessages,
  getGraphStatus,
  getRequirementDoc,
  updateRequirementDoc,
  updatePlanJson,
  type Message,
} from '@/api/message'
import {
  listConversations,
  createConversation,
  deleteConversation,
  type Conversation,
} from '@/api/conversation'
import { useWebSocketStore } from './websocket'
import { Event, EventType, StreamToken, ErrorInfo, GraphStatusEvent, GraphStatusLabel } from '@/api/websocket'

// Graph 执行状态接口
export interface GraphStatus {
  threadId: string
  status: string
  statusLabel: string
  currentNode: string
  progress: number
  errorMessage: string | null
}

export const useChatStore = defineStore('chat', () => {
  // 状态
  const conversations = ref<Conversation[]>([])
  const currentConversationId = ref<string | null>(null)
  const loading = ref(false)

  // 每个会话独立的状态（全局暂存）
  const messagesMap = ref<Map<string, Message[]>>(new Map())
  const sendingMap = ref<Map<string, boolean>>(new Map())
  const streamingMap = ref<Map<string, string>>(new Map())
  const graphStatusMap = ref<Map<string, GraphStatus>>(new Map())
  const requirementDocMap = ref<Map<string, string>>(new Map()) // 每个会话的需求文档
  const planJsonMap = ref<Map<string, string>>(new Map()) // 每个会话的计划 JSON

  // WebSocket Store
  const wsStore = useWebSocketStore()

  // 计算属性：当前会话
  const currentConversation = computed(() => {
    return conversations.value.find((c) => c.conversationId === currentConversationId.value) || null
  })

  // 计算属性：当前会话的消息列表
  const messages = computed(() => {
    if (!currentConversationId.value) return []
    return messagesMap.value.get(currentConversationId.value) || []
  })

  // 计算属性：当前会话的发送状态
  const sending = computed(() => {
    if (!currentConversationId.value) return false
    return sendingMap.value.get(currentConversationId.value) || false
  })

  // 计算属性：当前会话的流式内容
  const streamingContent = computed(() => {
    if (!currentConversationId.value) return ''
    return streamingMap.value.get(currentConversationId.value) || ''
  })

  // 计算属性：当前会话的 Graph 状态
  const graphStatus = computed(() => {
    if (!currentConversationId.value) return null
    return graphStatusMap.value.get(currentConversationId.value) || null
  })

  // 计算属性：当前会话的需求文档
  const requirementDoc = computed(() => {
    if (!currentConversationId.value) return ''
    return requirementDocMap.value.get(currentConversationId.value) || ''
  })

  // 计算属性：当前会话的计划 JSON
  const planJson = computed(() => {
    if (!currentConversationId.value) return ''
    return planJsonMap.value.get(currentConversationId.value) || ''
  })

  // 计算属性：是否在需求澄清阶段（有需求文档且未进入计划阶段）
  const isRequirementClarificationPhase = computed(() => {
    if (!currentConversationId.value) return false
    // 如果已经在计划阶段，不显示需求文档编辑器
    if (isPlanPhase.value) return false
    // 检查是否有需求文档
    const hasDoc = requirementDocMap.value.has(currentConversationId.value)
    if (!hasDoc) return false
    // 检查 Graph 状态，如果已完成或初始化中，不显示
    const graphStatus = graphStatusMap.value.get(currentConversationId.value)
    if (graphStatus) {
      const status = graphStatus.status
      if (status === 'completed' || status === 'initializing' || status === 'failed') {
        return false
      }
    }
    return true
  })

  // 计算属性：是否在计划阶段（有计划 JSON）
  const isPlanPhase = computed(() => {
    if (!currentConversationId.value) return false
    const graphStatus = graphStatusMap.value.get(currentConversationId.value)
    if (!graphStatus) return false
    const status = graphStatus.status
    // 只在工作流生成、确认、校验、执行阶段显示计划编辑器
    return status === 'workflow_generation' || status === 'workflow_confirmation' ||
           status === 'workflow_validation' || status === 'workflow_execution'
  })

  // 加载会话列表
  async function fetchConversations(agentId: string) {
    loading.value = true
    try {
      const result = await listConversations(agentId)
      conversations.value = result || []
      // 如果没有选中的会话，默认选中第一个并订阅
      if (!currentConversationId.value && conversations.value.length > 0) {
        currentConversationId.value = conversations.value[0].conversationId
        subscribeToConversation(conversations.value[0].conversationId)
        // 加载第一个会话的消息和 Graph 状态
        await Promise.all([
          fetchMessages(conversations.value[0].conversationId),
          fetchGraphStatus(conversations.value[0].conversationId),
        ])
      }
    } catch (error) {
      console.error('加载会话列表失败:', error)
      conversations.value = []
    } finally {
      loading.value = false
    }
  }

  // 创建新会话
  async function addConversation(agentId: string, title?: string) {
    try {
      const conversation = await createConversation(agentId, title || '新会话')
      conversations.value.unshift(conversation)
      currentConversationId.value = conversation.conversationId

      // 订阅新会话的 WebSocket 消息
      subscribeToConversation(conversation.conversationId)

      return conversation.conversationId
    } catch (error) {
      console.error('创建会话失败:', error)
      throw error
    }
  }

  // 加载会话消息
  async function fetchMessages(conversationId: string) {
    // 如果已经有缓存的消息，不重复加载
    if (messagesMap.value.has(conversationId)) {
      return
    }

    loading.value = true
    try {
      const msgs = await getMessages(conversationId)
      messagesMap.value.set(conversationId, msgs || [])
    } catch (error) {
      console.error('加载消息失败:', error)
      messagesMap.value.set(conversationId, [])
    } finally {
      loading.value = false
    }
  }

  // 加载会话的 Graph 状态、需求文档和计划 JSON
  async function fetchGraphStatus(conversationId: string) {
    try {
      const execution = await getGraphStatus(conversationId)
      if (execution) {
        // 设置 Graph 状态（如果未完成或失败）
        if (execution.status !== 'completed' && execution.status !== 'failed') {
          graphStatusMap.value.set(conversationId, {
            threadId: execution.threadId,
            status: execution.status,
            statusLabel: GraphStatusLabel[execution.status] || execution.status,
            currentNode: execution.currentNode,
            progress: execution.progress,
            errorMessage: execution.errorMessage,
          })

          // 只在 Graph 活跃时加载需求文档和计划 JSON
          const statusOrder = [
            'requirement_clarification',
            'workflow_generation',
            'workflow_confirmation',
            'workflow_validation',
            'workflow_execution'
          ]
          if (statusOrder.includes(execution.status)) {
            await fetchRequirementDoc(conversationId)
          }

          const planStatusOrder = [
            'workflow_generation',
            'workflow_confirmation',
            'workflow_validation',
            'workflow_execution'
          ]
          if (planStatusOrder.includes(execution.status)) {
            await fetchPlanJson(conversationId)
          }
        }
        // 如果已完成或失败，不加载需求文档和计划 JSON（保持初始界面）
      }
    } catch (error) {
      // 如果没有 Graph 状态，忽略错误
      console.debug('加载 Graph 状态失败（可能没有执行中的 Graph）:', error)
    }
  }

  // 加载会话的需求文档
  async function fetchRequirementDoc(conversationId: string) {
    try {
      const result = await getRequirementDoc(conversationId)
      if (result && result.content) {
        requirementDocMap.value.set(conversationId, result.content)
      }
    } catch (error) {
      console.debug('加载需求文档失败:', error)
    }
  }

  // 加载会话的计划 JSON
  async function fetchPlanJson(conversationId: string) {
    try {
      const execution = await getGraphStatus(conversationId)
      if (execution && execution.planJson) {
        planJsonMap.value.set(conversationId, execution.planJson)
      }
    } catch (error) {
      console.debug('加载计划 JSON 失败:', error)
    }
  }

  // 订阅会话 WebSocket 消息
  function subscribeToConversation(conversationId: string) {
    wsStore.subscribeConversation(conversationId, (event: Event) => {
      // 使用事件中的 conversationId，而不是 currentConversationId
      const eventConversationId = event.conversationId || conversationId

      switch (event.type) {
        case EventType.CHAT_STREAM:
          // 流式 token
          handleStreamToken(eventConversationId, event.data as StreamToken)
          break

        case EventType.CHAT_DONE:
          // 完成 - 将流式内容转为正式消息
          handleChatDone(eventConversationId)
          break

        case EventType.CHAT_ERROR:
          // 错误
          handleError(eventConversationId, event.data as ErrorInfo)
          break

        case EventType.GRAPH_STATUS_CHANGED:
          // Graph 状态变更
          handleGraphStatusChanged(eventConversationId, event.data as GraphStatusEvent)
          break

        case EventType.REQUIREMENT_DOC_UPDATED:
          // 需求文档更新
          handleRequirementDocUpdated(eventConversationId, event.data)
          break
      }
    })
  }

  // 处理需求文档更新
  function handleRequirementDocUpdated(conversationId: string, data: { content: string }) {
    if (data && data.content) {
      requirementDocMap.value.set(conversationId, data.content)
    }
  }

  // 处理 Graph 状态变更
  async function handleGraphStatusChanged(conversationId: string, data: GraphStatusEvent) {
    if (data) {
      console.log('[ChatStore] Graph 状态变更:', conversationId, data.status)

      const status: GraphStatus = {
        threadId: data.threadId,
        status: data.status,
        statusLabel: GraphStatusLabel[data.status] || data.status,
        currentNode: data.currentNode,
        progress: data.progress,
        errorMessage: data.errorMessage,
      }
      graphStatusMap.value.set(conversationId, status)

      // 如果状态变为计划阶段相关，加载计划 JSON
      const planStatusOrder = [
        'workflow_generation',
        'workflow_confirmation',
        'workflow_validation',
        'workflow_execution'
      ]
      if (planStatusOrder.includes(data.status)) {
        await fetchPlanJson(conversationId)
      }

      // 如果状态变为需求澄清阶段相关，加载需求文档
      const requirementStatusOrder = [
        'requirement_clarification',
        'workflow_generation',
        'workflow_confirmation',
        'workflow_validation'
      ]
      if (requirementStatusOrder.includes(data.status)) {
        await fetchRequirementDoc(conversationId)
      }

      // 如果 Graph 执行完成或失败，清除状态并恢复初始界面
      if (data.status === 'completed' || data.status === 'failed') {
        console.log('[ChatStore] Graph 执行完成/失败，准备清除状态')
        // 延迟清除，让用户看到完成状态
        setTimeout(() => {
          console.log('[ChatStore] 清除 Graph 状态:', conversationId)
          graphStatusMap.value.delete(conversationId)
          requirementDocMap.value.delete(conversationId)
          planJsonMap.value.delete(conversationId)
        }, 3000)
      }
    }
  }

  // 处理流式 token
  function handleStreamToken(conversationId: string, data: StreamToken) {
    if (data) {
      streamingMap.value.set(conversationId, data.accumulated)
    }
  }

  // 处理完成
  function handleChatDone(conversationId: string) {
    // 如果有流式内容，将其转为正式消息
    const streaming = streamingMap.value.get(conversationId) || ''
    if (streaming) {
      const msgs = messagesMap.value.get(conversationId) || []
      const message: Message = {
        id: Date.now(),
        conversationId: conversationId,
        agentId: '',
        type: 'assistant',
        content: streaming,
        toolCallId: null,
        toolName: null,
        toolCallsJson: null,
        sequence: msgs.length,
        metadataJson: null,
        createdAt: new Date().toISOString(),
      }
      msgs.push(message)
      messagesMap.value.set(conversationId, msgs)
    }

    // 重置当前会话的发送状态和流式内容
    sendingMap.value.set(conversationId, false)
    streamingMap.value.delete(conversationId)
  }

  // 处理错误
  function handleError(conversationId: string, data: ErrorInfo) {
    console.error('聊天错误:', data)

    const msgs = messagesMap.value.get(conversationId) || []

    // 如果有流式内容，也保存为消息（部分内容）
    const streaming = streamingMap.value.get(conversationId) || ''
    if (streaming) {
      const message: Message = {
        id: Date.now(),
        conversationId: conversationId,
        agentId: '',
        type: 'assistant',
        content: streaming + '\n\n[响应中断]',
        toolCallId: null,
        toolName: null,
        toolCallsJson: null,
        sequence: msgs.length,
        metadataJson: null,
        createdAt: new Date().toISOString(),
      }
      msgs.push(message)
    }

    // 重置发送状态和流式内容
    sendingMap.value.set(conversationId, false)
    streamingMap.value.delete(conversationId)

    // 添加错误提示消息
    if (data) {
      const errorMessage: Message = {
        id: Date.now(),
        conversationId: conversationId,
        agentId: '',
        type: 'system',
        content: `错误: ${data.message}`,
        toolCallId: null,
        toolName: null,
        toolCallsJson: null,
        sequence: msgs.length,
        metadataJson: null,
        createdAt: new Date().toISOString(),
      }
      msgs.push(errorMessage)
    }

    messagesMap.value.set(conversationId, msgs)
  }

  // 发送消息（通过 WebSocket）
  async function send(agentId: string, content: string) {
    if (!currentConversationId.value) {
      throw new Error('没有选中的会话')
    }

    const conversationId = currentConversationId.value

    // 设置当前会话的发送状态
    sendingMap.value.set(conversationId, true)
    streamingMap.value.delete(conversationId)

    // 先添加用户消息到本地列表
    const msgs = messagesMap.value.get(conversationId) || []
    const userMessage: Message = {
      id: Date.now(),
      conversationId: conversationId,
      agentId: agentId,
      type: 'user',
      content: content,
      toolCallId: null,
      toolName: null,
      toolCallsJson: null,
      sequence: msgs.length,
      metadataJson: null,
      createdAt: new Date().toISOString(),
    }
    msgs.push(userMessage)
    messagesMap.value.set(conversationId, msgs)

    // 通过 WebSocket 发送消息
    wsStore.sendChatMessage(conversationId, agentId, content)
  }

  // 删除会话
  async function removeConversation(conversationId: string) {
    try {
      await deleteConversation(conversationId)

      // 取消 WebSocket 订阅
      wsStore.unsubscribeConversation(conversationId)

      // 清除该会话的缓存数据
      messagesMap.value.delete(conversationId)
      sendingMap.value.delete(conversationId)
      streamingMap.value.delete(conversationId)
      graphStatusMap.value.delete(conversationId)

      conversations.value = conversations.value.filter(
        (c) => c.conversationId !== conversationId
      )
      // 如果删除的是当前会话，切换到第一个
      if (currentConversationId.value === conversationId) {
        currentConversationId.value =
          conversations.value.length > 0 ? conversations.value[0].conversationId : null
        if (currentConversationId.value) {
          await fetchMessages(currentConversationId.value)
        }
      }
    } catch (error) {
      console.error('删除会话失败:', error)
      throw error
    }
  }

  // 切换当前会话
  async function setCurrentConversation(conversationId: string) {
    // 如果是同一个会话，不需要重新订阅
    if (currentConversationId.value === conversationId) {
      return
    }

    // 取消订阅旧会话
    if (currentConversationId.value) {
      wsStore.unsubscribeConversation(currentConversationId.value)
    }

    currentConversationId.value = conversationId

    // 加载消息和 Graph 状态
    await Promise.all([
      fetchMessages(conversationId),
      fetchGraphStatus(conversationId),
    ])

    // 订阅新会话
    subscribeToConversation(conversationId)
  }

  // 保存需求文档
  async function saveRequirementDoc(content: string) {
    if (!currentConversationId.value) {
      throw new Error('没有选中的会话')
    }

    try {
      await updateRequirementDoc(currentConversationId.value, content)
      // 更新本地缓存
      requirementDocMap.value.set(currentConversationId.value, content)
    } catch (error) {
      console.error('保存需求文档失败:', error)
      throw error
    }
  }

  // 保存计划 JSON
  async function savePlanJson(planJsonContent: string) {
    if (!currentConversationId.value) {
      throw new Error('没有选中的会话')
    }

    const status = graphStatusMap.value.get(currentConversationId.value)
    if (!status || !status.threadId) {
      throw new Error('没有活跃的 Graph 执行')
    }

    try {
      await updatePlanJson(status.threadId, planJsonContent)
      // 更新本地缓存
      planJsonMap.value.set(currentConversationId.value, planJsonContent)
    } catch (error) {
      console.error('保存计划 JSON 失败:', error)
      throw error
    }
  }

  // 清空状态
  function clear() {
    // 取消所有订阅
    if (currentConversationId.value) {
      wsStore.unsubscribeConversation(currentConversationId.value)
    }

    conversations.value = []
    currentConversationId.value = null
    messagesMap.value.clear()
    sendingMap.value.clear()
    streamingMap.value.clear()
    graphStatusMap.value.clear()
    requirementDocMap.value.clear()
    planJsonMap.value.clear()
  }

  return {
    conversations,
    currentConversationId,
    currentConversation,
    messages,
    loading,
    sending,
    streamingContent,
    graphStatus,
    requirementDoc,
    planJson,
    isRequirementClarificationPhase,
    isPlanPhase,
    fetchConversations,
    addConversation,
    fetchMessages,
    send,
    removeConversation,
    setCurrentConversation,
    saveRequirementDoc,
    savePlanJson,
    clear,
  }
})
