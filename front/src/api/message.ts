import request from './request'

export interface Message {
  id: number
  conversationId: string
  agentId: string
  type: 'system' | 'user' | 'assistant' | 'tool'
  content: string
  toolCallId: string | null
  toolName: string | null
  toolCallsJson: string | null
  sequence: number
  metadataJson: string | null
  createdAt: string
}

export interface CreateConversationRequest {
  agentId: string
  systemPrompt?: string
}

export interface SendMessageRequest {
  agentId: string
  content: string
}

export interface GraphExecution {
  id: number
  threadId: string
  conversationId: string
  agentId: string
  status: string
  currentNode: string
  progress: number
  errorMessage: string | null
  requirementDocPath: string | null
  planJson: string | null
  extraData: string | null
  createdAt: string
  updatedAt: string
}

// 获取会话列表（指定 Agent）
export function listConversations(agentId: string) {
  return request.get<any, string[]>('/messages/conversations', {
    params: { agentId },
  })
}

// 创建新会话
export function createConversation(data: CreateConversationRequest) {
  return request.post<any, { conversationId: string }>('/messages/conversations', data)
}

// 获取会话消息列表
export function getMessages(conversationId: string) {
  return request.get<any, Message[]>(`/messages/conversations/${conversationId}`)
}

// 获取会话最近 N 条消息
export function getRecentMessages(conversationId: string, limit: number = 50) {
  return request.get<any, Message[]>(`/messages/conversations/${conversationId}/recent`, {
    params: { limit },
  })
}

// 发送消息
export function sendMessage(conversationId: string, data: SendMessageRequest) {
  return request.post<any, Message>(`/messages/conversations/${conversationId}/send`, data)
}

// 删除会话
export function deleteConversation(conversationId: string) {
  return request.delete(`/messages/conversations/${conversationId}`)
}

// 获取会话消息数量
export function countMessages(conversationId: string) {
  return request.get<any, { count: number }>(`/messages/conversations/${conversationId}/count`)
}

// 获取会话最新的 Graph 执行状态
export function getGraphStatus(conversationId: string) {
  return request.get<any, GraphExecution | null>(`/messages/conversations/${conversationId}/graph-status`)
}

// 获取会话的需求文档内容
export function getRequirementDoc(conversationId: string) {
  return request.get<any, { content: string } | null>(`/messages/conversations/${conversationId}/requirement-doc`)
}

// 更新会话的需求文档内容
export function updateRequirementDoc(conversationId: string, content: string) {
  return request.put<any, void>(`/messages/conversations/${conversationId}/requirement-doc`, { content })
}

// 更新会话的计划 JSON
export function updatePlanJson(threadId: string, planJson: string) {
  return request.put<any, void>(`/graph-executions/${threadId}/plan`, { planJson })
}
