import { defineStore } from 'pinia'
import { ref } from 'vue'

// 创建store
export const useChatStore = defineStore('chat', () => {

  // 定义模型列表
  const models = ref([
  { id: 1, label: 'gpt-5.6-sol', name: 'qwen3.8-max', icon: 'deepseek-logo', description: '聊天对话', selected: true },
  { id: 2, label: 'claude-fable-5',name: 'qwen3.5-omni-flash',icon: 'claude', description: '图片生成', selected: false },
  { id: 2, label: 'deepseek-v4-pro',name: 'qwen3.5-omni-flash',icon: 'chatGPT', description: '视频生成', selected: false },
])


  // 选中的模型，默认为第一个
  const selectedModel = ref(models.value[0])

  // 联网搜索状态，默认为false
  const isNetworkSearchSelected = ref(false)

  // 深度思考状态，默认关闭（与用户需求保持一致：think 默认 false）
  // 通过 persist: true 持久化，切换后会记住，直到用户手动更改。
  const isDeepThinkingSelected = ref(false)

  // 更新选中的模型
  function updateSelectedModel(model) {
    // 将所有模型的 selected 置为 false
    models.value.forEach(m => {
      m.selected = false;
    });
    
    // 将选中模型的 selected 置为 true
    model.selected = true;
    
    // 更新当前选中的模型
    selectedModel.value = model;
  }

  // 更新联网搜索状态
  function updateNetworkSearchStatus(status) {
    isNetworkSearchSelected.value = status
  }

  // 更新深度思考开关状态
  function updateDeepThinkingStatus(status) {
    isDeepThinkingSelected.value = status
  }

  // 对外暴露相关变量与方法
  return {
    models,
    selectedModel,
    isNetworkSearchSelected,
    isDeepThinkingSelected,
    updateSelectedModel,
    updateNetworkSearchStatus,
    updateDeepThinkingStatus
  }
},
{
  // 开启持久化
  persist: true,
})
