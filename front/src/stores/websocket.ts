import { defineStore } from 'pinia'
import { ref } from 'vue'
import websocketService, { Event, EventType, ChatMessage } from '@/api/websocket'

export const useWebSocketStore = defineStore('websocket', () => {
  // 状态
  const connected = ref(false)
  const error = ref<string | null>(null)

  // 聊天消息回调
  const chatCallbacks = ref<Map<string, (event: Event<ChatMessage>) => void>>(new Map())

  /**
   * 连接 WebSocket。
   */
  async function connect() {
    try {
      error.value = null
      await websocketService.connect()
      connected.value = true

      // 注册事件处理器
      websocketService.on(EventType.CONNECTED, () => {
        connected.value = true
        error.value = null
      })

      websocketService.on(EventType.ERROR, (event: Event) => {
        error.value = event.data?.message || '未知错误'
      })
    } catch (err: any) {
      connected.value = false
      error.value = err.message || '连接失败'
      console.error('[WebSocket Store] 连接失败:', err)
    }
  }

  /**
   * 断开连接。
   */
  function disconnect() {
    websocketService.disconnect()
    connected.value = false
    chatCallbacks.value.clear()
  }

  /**
   * 订阅会话消息。
   */
  function subscribeConversation(
    conversationId: string,
    callback: (event: Event<ChatMessage>) => void
  ) {
    // 保存回调
    chatCallbacks.value.set(conversationId, callback)

    // 订阅会话
    websocketService.subscribeConversation(conversationId, (event: Event) => {
      const chatCallback = chatCallbacks.value.get(conversationId)
      if (chatCallback) {
        chatCallback(event as Event<ChatMessage>)
      }
    })
  }

  /**
   * 取消订阅会话。
   */
  function unsubscribeConversation(conversationId: string) {
    chatCallbacks.value.delete(conversationId)
    websocketService.unsubscribeConversation(conversationId)
  }

  /**
   * 发送聊天消息。
   */
  function sendChatMessage(conversationId: string, agentId: string, content: string) {
    if (!connected.value) {
      error.value = '未连接到服务器'
      return
    }
    websocketService.sendChatMessage(conversationId, agentId, content)
  }

  return {
    connected,
    error,
    connect,
    disconnect,
    subscribeConversation,
    unsubscribeConversation,
    sendChatMessage,
  }
})
