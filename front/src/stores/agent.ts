import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  listAgents,
  createAgent,
  updateAgent,
  deleteAgent,
  type Agent,
  type CreateAgentRequest,
  type UpdateAgentRequest,
} from '@/api/agent'

export const useAgentStore = defineStore('agent', () => {
  // 状态
  const agents = ref<Agent[]>([])
  const currentAgentId = ref<string | null>(null)
  const loading = ref(false)

  // 计算属性
  const currentAgent = computed(() => {
    return agents.value.find((a) => a.agentId === currentAgentId.value) || null
  })

  const enabledAgents = computed(() => {
    return agents.value.filter((a) => a.enabled)
  })

  // 加载 Agent 列表
  async function fetchAgents() {
    loading.value = true
    try {
      agents.value = await listAgents()
      // 如果没有选中的 Agent，默认选中第一个
      if (!currentAgentId.value && agents.value.length > 0) {
        currentAgentId.value = agents.value[0].agentId
      }
    } catch (error) {
      console.error('加载 Agent 列表失败:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  // 创建 Agent
  async function addAgent(data: CreateAgentRequest) {
    try {
      const agent = await createAgent(data)
      agents.value.push(agent)
      // 自动选中新创建的 Agent
      currentAgentId.value = agent.agentId
      return agent
    } catch (error) {
      console.error('创建 Agent 失败:', error)
      throw error
    }
  }

  // 更新 Agent
  async function modifyAgent(agentId: string, data: UpdateAgentRequest) {
    try {
      const agent = await updateAgent(agentId, data)
      const index = agents.value.findIndex((a) => a.agentId === agentId)
      if (index !== -1) {
        agents.value[index] = agent
      }
      return agent
    } catch (error) {
      console.error('更新 Agent 失败:', error)
      throw error
    }
  }

  // 删除 Agent
  async function removeAgent(agentId: string) {
    try {
      await deleteAgent(agentId)
      agents.value = agents.value.filter((a) => a.agentId !== agentId)
      // 如果删除的是当前选中的 Agent，切换到第一个
      if (currentAgentId.value === agentId) {
        currentAgentId.value = agents.value.length > 0 ? agents.value[0].agentId : null
      }
    } catch (error) {
      console.error('删除 Agent 失败:', error)
      throw error
    }
  }

  // 切换当前 Agent
  function setCurrentAgent(agentId: string) {
    currentAgentId.value = agentId
  }

  return {
    agents,
    currentAgentId,
    currentAgent,
    enabledAgents,
    loading,
    fetchAgents,
    addAgent,
    modifyAgent,
    removeAgent,
    setCurrentAgent,
  }
})
