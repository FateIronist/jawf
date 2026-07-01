import request from './request'

export interface Conversation {
  id: number
  conversationId: string
  agentId: string
  graphThreadId: string | null
  title: string
  status: 'active' | 'completed' | 'archived'
  createdAt: string
  updatedAt: string
}

// 获取 Agent 的会话列表
export function listConversations(agentId: string) {
  return request.get<any, Conversation[]>('/messages/conversations', {
    params: { agentId },
  })
}

// 创建新会话
export function createConversation(agentId: string, title?: string) {
  return request.post<any, Conversation>('/messages/conversations', {
    agentId,
    title: title || '新会话',
  })
}

// 删除会话
export function deleteConversation(conversationId: string) {
  return request.delete(`/messages/conversations/${conversationId}`)
}
