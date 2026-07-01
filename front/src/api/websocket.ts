import { Client, IMessage, StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

/**
 * WebSocket 事件接口。
 */
export interface Event<T = any> {
  type: string
  data: T
  timestamp: string
  conversationId?: string
  agentId?: string
  source?: 'client' | 'server'
  eventId?: string
}

/**
 * 聊天消息接口。
 */
export interface ChatMessage {
  content: string
  role: 'user' | 'assistant' | 'system'
  timestamp: string
}

/**
 * 流式 token 接口。
 */
export interface StreamToken {
  token: string
  accumulated: string
}

/**
 * 错误信息接口。
 */
export interface ErrorInfo {
  code: string
  message: string
}

/**
 * 需求文档更新接口。
 */
export interface RequirementDocUpdate {
  content: string
}

/**
 * Graph 状态变更事件接口。
 */
export interface GraphStatusEvent {
  threadId: string
  status: string
  currentNode: string
  progress: number
  errorMessage: string | null
}

/**
 * Graph 执行状态常量。
 */
export const GraphStatus = {
  INITIALIZING: 'initializing',
  INTENT_RECOGNITION: 'intent_recognition',
  REQUIREMENT_CLARIFICATION: 'requirement_clarification',
  WORKFLOW_GENERATION: 'workflow_generation',
  WORKFLOW_CONFIRMATION: 'workflow_confirmation',
  WORKFLOW_VALIDATION: 'workflow_validation',
  WORKFLOW_EXECUTION: 'workflow_execution',
  COMPLETED: 'completed',
  FAILED: 'failed',
} as const

/**
 * Graph 状态标签。
 */
export const GraphStatusLabel: Record<string, string> = {
  [GraphStatus.INITIALIZING]: '初始化中',
  [GraphStatus.INTENT_RECOGNITION]: '意图识别中',
  [GraphStatus.REQUIREMENT_CLARIFICATION]: '需求澄清中',
  [GraphStatus.WORKFLOW_GENERATION]: '工作流生成中',
  [GraphStatus.WORKFLOW_CONFIRMATION]: '工作流确认中',
  [GraphStatus.WORKFLOW_VALIDATION]: '工作流校验中',
  [GraphStatus.WORKFLOW_EXECUTION]: '工作流执行中',
  [GraphStatus.COMPLETED]: '执行完成',
  [GraphStatus.FAILED]: '执行失败',
}

/**
 * 事件类型常量。
 */
export const EventType = {
  CHAT_SEND: 'chat.send',
  CHAT_RECEIVE: 'chat.receive',
  CHAT_STREAM: 'chat.stream',
  CHAT_DONE: 'chat.done',
  CHAT_ERROR: 'chat.error',
  REQUIREMENT_DOC_UPDATED: 'requirement.doc.updated',
  GRAPH_STATUS_CHANGED: 'graph.status.changed',
  CONVERSATION_CREATED: 'conversation.created',
  CONVERSATION_DELETED: 'conversation.deleted',
  AGENT_UPDATED: 'agent.updated',
  CONNECTED: 'connected',
  HEARTBEAT: 'heartbeat',
  ERROR: 'error',
} as const

type EventHandler<T = any> = (event: Event<T>) => void

/**
 * WebSocket 服务。
 */
class WebSocketService {
  private client: Client | null = null
  private connected = false
  private subscriptions: Map<string, StompSubscription> = new Map()
  private eventHandlers: Map<string, Set<EventHandler>> = new Map()

  /**
   * 连接 WebSocket。
   */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.connected) {
        resolve()
        return
      }

      this.client = new Client({
        // 使用 SockJS 作为 WebSocket 传输
        webSocketFactory: () => new SockJS('/ws'),

        // 连接成功回调
        onConnect: () => {
          console.log('[WebSocket] 连接成功')
          this.connected = true

          // 订阅广播主题
          this.subscribe('/topic/broadcast', (message: IMessage) => {
            const event: Event = JSON.parse(message.body)
            this.handleEvent(event)
          })

          // 触发连接事件
          this.emit(EventType.CONNECTED, null)

          resolve()
        },

        // 连接错误回调
        onStompError: (frame) => {
          console.error('[WebSocket] 连接错误:', frame.headers['message'])
          this.connected = false
          reject(new Error(frame.headers['message']))
        },

        // 断开连接回调
        onDisconnect: () => {
          console.log('[WebSocket] 断开连接')
          this.connected = false
        },

        // 心跳配置
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
      })

      // 激活连接
      this.client.activate()
    })
  }

  /**
   * 断开 WebSocket 连接。
   */
  disconnect(): void {
    if (this.client) {
      // 取消所有订阅
      this.subscriptions.forEach((sub) => sub.unsubscribe())
      this.subscriptions.clear()

      // 断开连接
      this.client.deactivate()
      this.client = null
      this.connected = false
      console.log('[WebSocket] 已断开连接')
    }
  }

  /**
   * 检查是否已连接。
   */
  isConnected(): boolean {
    return this.connected
  }

  /**
   * 订阅主题。
   */
  subscribe(destination: string, callback: (message: IMessage) => void): StompSubscription | null {
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] 未连接，无法订阅:', destination)
      return null
    }

    // 如果已经订阅过，先取消
    const existing = this.subscriptions.get(destination)
    if (existing) {
      existing.unsubscribe()
    }

    const subscription = this.client.subscribe(destination, callback)
    this.subscriptions.set(destination, subscription)
    console.log('[WebSocket] 已订阅:', destination)
    return subscription
  }

  /**
   * 取消订阅。
   */
  unsubscribe(destination: string): void {
    const subscription = this.subscriptions.get(destination)
    if (subscription) {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
      console.log('[WebSocket] 已取消订阅:', destination)
    }
  }

  /**
   * 发送消息。
   */
  send(destination: string, body: any, headers: Record<string, string> = {}): void {
    if (!this.client || !this.connected) {
      console.error('[WebSocket] 未连接，无法发送消息')
      return
    }

    this.client.publish({
      destination,
      body: JSON.stringify(body),
      headers,
    })
  }

  /**
   * 订阅会话聊天消息。
   */
  subscribeConversation(conversationId: string, callback: (event: Event) => void): void {
    const destination = `/topic/chat/${conversationId}`

    this.subscribe(destination, (message: IMessage) => {
      const event: Event = JSON.parse(message.body)
      callback(event)
      this.handleEvent(event)
    })
  }

  /**
   * 取消订阅会话聊天消息。
   */
  unsubscribeConversation(conversationId: string): void {
    this.unsubscribe(`/topic/chat/${conversationId}`)
  }

  /**
   * 发送聊天消息。
   */
  sendChatMessage(conversationId: string, agentId: string, content: string): void {
    const event: Event<ChatMessage> = {
      type: EventType.CHAT_SEND,
      data: {
        content,
        role: 'user',
        timestamp: new Date().toISOString(),
      },
      timestamp: new Date().toISOString(),
      conversationId,
      agentId,
      source: 'client',
    }

    this.send('/app/chat.send', event)
  }

  /**
   * 注册事件处理器。
   */
  on<T = any>(eventType: string, handler: EventHandler<T>): void {
    if (!this.eventHandlers.has(eventType)) {
      this.eventHandlers.set(eventType, new Set())
    }
    this.eventHandlers.get(eventType)!.add(handler as EventHandler)
  }

  /**
   * 取消事件处理器。
   */
  off<T = any>(eventType: string, handler: EventHandler<T>): void {
    const handlers = this.eventHandlers.get(eventType)
    if (handlers) {
      handlers.delete(handler as EventHandler)
    }
  }

  /**
   * 触发事件。
   */
  private emit(eventType: string, data: any): void {
    const handlers = this.eventHandlers.get(eventType)
    if (handlers) {
      const event: Event = {
        type: eventType,
        data,
        timestamp: new Date().toISOString(),
        source: 'server',
      }
      handlers.forEach((handler) => handler(event))
    }
  }

  /**
   * 处理接收到的事件。
   */
  private handleEvent(event: Event): void {
    console.log('[WebSocket] 收到事件:', event.type, event)

    const handlers = this.eventHandlers.get(event.type)
    if (handlers) {
      handlers.forEach((handler) => handler(event))
    }
  }
}

// 导出单例
export const websocketService = new WebSocketService()
export default websocketService
