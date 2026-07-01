import request from './request'

export interface Agent {
  id: number
  agentId: string
  name: string
  description: string
  defaultModel: string
  systemPrompt: string
  parallelEnabled: boolean
  maxParallel: number
  maxRetry: number
  timeoutSeconds: number
  agentType: string
  configJson: string
  createdAt: string
  updatedAt: string
  enabled: boolean
}

export interface CreateAgentRequest {
  name: string
  description?: string
  defaultModel: string
  systemPrompt?: string
  parallelEnabled?: boolean
  maxParallel?: number
  agentType?: string
}

export interface UpdateAgentRequest {
  name?: string
  description?: string
  defaultModel?: string
  systemPrompt?: string
  parallelEnabled?: boolean
  maxParallel?: number
  agentType?: string
}

// 获取所有启用的 Agent
export function listAgents() {
  return request.get<any, Agent[]>('/agents')
}

// 根据 agentId 获取 Agent
export function getAgent(agentId: string) {
  return request.get<any, Agent>(`/agents/${agentId}`)
}

// 创建 Agent
export function createAgent(data: CreateAgentRequest) {
  return request.post<any, Agent>('/agents', data)
}

// 更新 Agent
export function updateAgent(agentId: string, data: UpdateAgentRequest) {
  return request.put<any, Agent>(`/agents/${agentId}`, data)
}

// 删除 Agent
export function deleteAgent(agentId: string) {
  return request.delete(`/agents/${agentId}`)
}

// 启用/禁用 Agent
export function setAgentEnabled(agentId: string, enabled: boolean) {
  return request.patch(`/agents/${agentId}/enabled`, { enabled })
}
