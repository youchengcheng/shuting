<template>
  <div v-if="visible" class="login-layer">
    <!-- 遮罩层 -->
    <div class="login-mask" @click="onClose"></div>

    <!-- 登录框 -->
    <Transition name="zoom" appear @before-enter="onBeforeEnter" @enter="onEnter" @leave="onLeave">
      <div class="login-panel" ref="modalRef" role="dialog" aria-modal="true" aria-label="登录书亭">
        <button type="button" class="login-close" aria-label="关闭" @click="onClose">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M6 18L18 6M6 6l12 12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </button>

        <div class="login-body">
          <BrandLogo class="login-seal" :size="44" :show-word="false" />
          <h2 class="login-title">手机号登录</h2>
          <p class="login-note">新用户可直接登录</p>

          <!-- 手机号输入 -->
          <div class="login-field">
            <div class="login-prefix">
              <span class="st-num">+86</span>
              <span class="login-prefix__divider"></span>
            </div>
            <input
              type="text"
              inputmode="numeric"
              placeholder="输入手机号"
              class="login-input"
              v-model="formattedPhone"
              maxlength="13"
              @input="formatPhoneNumber"
            />
          </div>

          <!-- 验证码输入 -->
          <div class="login-field">
            <input
              type="text"
              inputmode="numeric"
              placeholder="输入验证码"
              class="login-input login-input--code"
              v-model="code"
              maxlength="6"
              @input="formatCode"
            />
            <button
              type="button"
              class="login-code-btn"
              :class="buttonClass"
              @click="getCode"
              :disabled="!isPhoneValid || countdown > 0"
            >
              {{ countdown > 0 ? `重新发送(${countdown}s)` : '获取验证码' }}
            </button>
          </div>

          <!-- 登录按钮 -->
          <button type="button" class="st-btn st-btn-primary login-submit" @click="handleLogin">
            登录
          </button>

          <!-- 协议勾选 -->
          <div class="login-terms">
            <label class="login-terms__row">
              <input type="checkbox" class="login-checkbox" v-model="agreeTerms" />
              <span class="login-terms__text">
                我已阅读并同意
                <a href="#" class="login-link">《用户协议》</a>
                <a href="#" class="login-link">《隐私政策》</a>
                <a href="#" class="login-link">《儿童/青少年个人信息保护规则》</a>
              </span>
            </label>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 添加协议确认模态框 -->
    <TermsConfirmModal v-model:visible="showTermsConfirm" @confirm="handleConfirmTerms" />
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import gsap from 'gsap'
import { message } from '@/utils/message'
import BrandLogo from '@/components/common/BrandLogo.vue'
import TermsConfirmModal from './TermsConfirmModal.vue'
import { login, getVerificationCode } from '@/api/auth'
import { getUserProfile } from '@/api/user'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible'])

const phone = ref('')
const formattedPhone = ref('')
const code = ref('')
const agreeTerms = ref(false)

const modalRef = ref(null)

const showTermsConfirm = ref(false)

// 倒计时
const countdown = ref(0)
let timer = null

const onClose = () => {
  emit('update:visible', false)
}

// 验证手机号格式
const isPhoneValid = computed(() => {
  return phone.value.length === 11 && /^1[3-9]\d{9}$/.test(phone.value)
})

// 按钮样式计算属性
const buttonClass = computed(() => {
  if (countdown.value > 0) {
    return 'text-ink-faint cursor-not-allowed'
  }
  return isPhoneValid.value 
    ? 'text-ink hover:opacity-70' 
    : 'text-ink-faint cursor-not-allowed'
})

// 获取验证码
const getCode = () => {
  if (!isPhoneValid.value || countdown.value > 0) return
  
  // 调用获取验证码接口
  getVerificationCode(phone.value).then(res => {
    if (!res.success) {
      message.show(res.message)
      return
    }
    
    message.show('验证码已发送')
    
    // 开始倒计时
    countdown.value = 180 // 3分钟
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  })
}

// 组件卸载时清除定时器
onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})

// 处理登录
const handleLogin = () => {
  if (!agreeTerms.value) {
    showTermsConfirm.value = true
    return
  }
  
  doLogin()
}

// 确认同意协议
const handleConfirmTerms = () => {
  agreeTerms.value = true
  doLogin()
}

// 执行登录
const doLogin = () => {
  if (!isPhoneValid.value) {
    message.show('请输入正确的手机号')
    return
  }
  
  if (!code.value || code.value.length !== 6) {
    message.show('请输入正确的验证码')
    return
  }
  
  // 调用登录接口
  login({phone: phone.value, code: code.value, type: 1}).then(res => {
    console.log(res)
    if (!res.success) {
      message.show('验证码错误')
      return
    }

    // 存储 token
    userStore.setToken(res.data)

    // 获取用户基本信息
    getUserProfile().then(res => {
      if (res.success) {
        userStore.setProfile(res.data)
      }
    })
    
    message.show('登录成功')
    onClose() // 关闭登录框
  })
}

const onBeforeEnter = (el) => {
  gsap.set(el, {
    opacity: 0,
    scale: 0.5,
    y: 40
  })
}

const onEnter = (el) => {
  gsap.to(el, {
    opacity: 1,
    scale: 1,
    y: 0,
    duration: 0.3,
    ease: 'back.out(1.7)'
  })
}

const onLeave = (el) => {
  gsap.to(el, {
    opacity: 0,
    scale: 0.5,
    duration: 0.2,
    ease: 'power2.in'
  })
}

// 格式化手机号
const formatPhoneNumber = (event) => {
  // 移除所有非数字字符
  let value = event.target.value.replace(/\D/g, '')
  
  // 限制长度为11位数字
  if (value.length > 11) {
    value = value.slice(0, 11)
  }
  
  // 添加空格: xxx xxxx xxxx
  if (value.length > 7) {
    formattedPhone.value = `${value.slice(0, 3)} ${value.slice(3, 7)} ${value.slice(7)}`
  } else if (value.length > 3) {
    formattedPhone.value = `${value.slice(0, 3)} ${value.slice(3)}`
  } else {
    formattedPhone.value = value
  }
  
  // 更新实际的手机号(不带空格)
  phone.value = value
}

// 格式化验证码 - 只允许输入数字
const formatCode = (event) => {
  // 移除所有非数字字符
  let value = event.target.value.replace(/\D/g, '')
  
  // 限制长度为6位数字
  if (value.length > 6) {
    value = value.slice(0, 6)
  }
  
  code.value = value
}
</script>

<style scoped>
.login-layer {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.login-mask {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: rgb(20 17 14 / 0.32);
}

.login-panel {
  position: relative;
  z-index: 2;
  width: 440px;
  max-width: 100%;
  background: var(--color-paper);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
}

.login-close {
  position: absolute;
  top: 16px;
  right: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--color-ink-faint);
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.login-close:hover {
  background: var(--color-canvas-sunken);
  color: var(--color-ink);
}

.login-close svg {
  width: 18px;
  height: 18px;
}

.login-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 40px 32px;
  text-align: center;
}

.login-seal {
  margin-bottom: 16px;
}

.login-title {
  font-size: 24px;
  line-height: 1.3;
  color: var(--color-ink);
}

.login-note {
  margin: 6px 0 28px;
  font-size: 13px;
  color: var(--color-ink-faint);
}

.login-field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 48px;
  padding: 0 14px;
  margin-bottom: 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-control);
  background: var(--color-canvas-sunken);
  transition:
    border-color var(--motion-fast) var(--ease-standard),
    box-shadow var(--motion-fast) var(--ease-standard),
    background-color var(--motion-fast) var(--ease-standard);
}

.login-field:focus-within {
  border-color: var(--color-line-strong);
  background: var(--color-paper);
  box-shadow: 0 2px 12px rgb(0 0 0 / 0.08);
}

.login-prefix {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  color: var(--color-ink);
}

.login-prefix__divider {
  width: 1px;
  height: 14px;
  background: var(--color-line);
}

.login-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  color: var(--color-ink);
  letter-spacing: 0.01em;
}

.login-input::placeholder {
  color: var(--color-ink-faint);
}

.login-input--code {
  letter-spacing: 0.18em;
}

.login-code-btn {
  flex-shrink: 0;
  padding: 0;
  border: none;
  background: none;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: color var(--motion-fast) var(--ease-standard);
}

.login-submit {
  width: 100%;
  height: 48px;
  margin-top: 12px;
  font-size: 16px;
}

.login-terms {
  margin-top: 20px;
}

.login-terms__row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  text-align: left;
  cursor: pointer;
}

.login-checkbox {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  margin-top: 3px;
  accent-color: var(--color-ink);
  cursor: pointer;
}

.login-terms__text {
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-ink-faint);
}

.login-link {
  color: var(--color-ink-soft);
  text-decoration: underline;
  text-decoration-color: var(--color-line);
  text-underline-offset: 2px;
  transition: color var(--motion-fast) var(--ease-standard);
}

.login-link:hover {
  color: var(--color-ink);
  text-decoration-color: currentColor;
}

.zoom-move {
  transition: transform 0.3s ease-out;
}

/* 可选：禁用 Chrome 浏览器的自动填充背景色 */
input:-webkit-autofill,
input:-webkit-autofill:hover,
input:-webkit-autofill:focus {
  -webkit-box-shadow: 0 0 0 30px var(--color-paper) inset !important;
  -webkit-text-fill-color: var(--color-ink);
}
</style>
