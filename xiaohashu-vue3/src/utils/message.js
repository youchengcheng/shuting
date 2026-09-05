import { createVNode, render } from 'vue'
import Message from '@/components/common/Message.vue'

const messageInstance = createVNode(Message)
const container = document.createElement('div')
document.body.appendChild(container)
render(messageInstance, container)

export const message = {
  show(msg, duration) {
    // 兼容传 { content, type } 对象的调用方式
    const text = (msg && typeof msg === 'object' && msg.content) ? msg.content : msg
    messageInstance.component.exposed.show(text, msg?.duration || duration)
  }
}
