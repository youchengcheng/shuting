<template>
  <BaseModal
    :visible="visible"
    title="修改密码"
    width="420px"
    :show-footer="false"
    @update:visible="onVisibleChange"
  >
    <form class="pwd-form" @submit.prevent="handleConfirm">
      <label class="pwd-field">
        <span class="pwd-field__label">新密码</span>
        <span class="pwd-field__control" :class="{ 'pwd-field__control--error': Boolean(passwordError) }">
          <input
            :type="showPassword ? 'text' : 'password'"
            class="pwd-input"
            placeholder="6-20 位，需包含字母和数字"
            autocomplete="new-password"
            maxlength="20"
            v-model="newPassword"
          />
          <button
            type="button"
            class="pwd-eye"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            @click="showPassword = !showPassword"
          >
            <svg v-if="showPassword" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M3 3l18 18" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
              <path
                d="M10.6 5.3A9.6 9.6 0 0 1 12 5.2c5 0 9 4.1 9 6.8 0 1-.6 2.2-1.6 3.3M6.3 6.9C4 8.5 3 10.8 3 12c0 2.7 4 6.8 9 6.8 1.6 0 3-.4 4.3-1.1"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
              />
              <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M12 5.2c5 0 9 4.1 9 6.8s-4 6.8-9 6.8-9-4.1-9-6.8 4-6.8 9-6.8Z"
                stroke="currentColor"
                stroke-width="1.7"
              />
              <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.7" />
            </svg>
          </button>
        </span>
        <span v-if="passwordError" class="pwd-field__error">{{ passwordError }}</span>
      </label>

      <label class="pwd-field">
        <span class="pwd-field__label">确认新密码</span>
        <span class="pwd-field__control" :class="{ 'pwd-field__control--error': Boolean(confirmError) }">
          <input
            :type="showConfirm ? 'text' : 'password'"
            class="pwd-input"
            placeholder="请再次输入新密码"
            autocomplete="new-password"
            maxlength="20"
            v-model="confirmPassword"
          />
          <button
            type="button"
            class="pwd-eye"
            :aria-label="showConfirm ? '隐藏密码' : '显示密码'"
            @click="showConfirm = !showConfirm"
          >
            <svg v-if="showConfirm" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M3 3l18 18" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
              <path
                d="M10.6 5.3A9.6 9.6 0 0 1 12 5.2c5 0 9 4.1 9 6.8 0 1-.6 2.2-1.6 3.3M6.3 6.9C4 8.5 3 10.8 3 12c0 2.7 4 6.8 9 6.8 1.6 0 3-.4 4.3-1.1"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
              />
              <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M12 5.2c5 0 9 4.1 9 6.8s-4 6.8-9 6.8-9-4.1-9-6.8 4-6.8 9-6.8Z"
                stroke="currentColor"
                stroke-width="1.7"
              />
              <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.7" />
            </svg>
          </button>
        </span>
        <span v-if="confirmError" class="pwd-field__error">{{ confirmError }}</span>
      </label>

      <div class="pwd-actions">
        <button type="button" class="st-btn st-btn-ghost" :disabled="submitting" @click="onClose">取消</button>
        <button type="submit" class="st-btn st-btn-primary" :disabled="!canSubmit">
          <span v-if="submitting" class="pwd-spinner" aria-hidden="true"></span>
          {{ submitting ? '提交中…' : '确定' }}
        </button>
      </div>
    </form>
  </BaseModal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import BaseModal from '@/components/common/BaseModal.vue'
import { updatePassword } from '@/api/auth'
import { message } from '@/utils/message'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible'])

const newPassword = ref('')
const confirmPassword = ref('')
const showPassword = ref(false)
const showConfirm = ref(false)
const submitting = ref(false)
const touched = ref(false)

// 密码校验：6-20 位，且同时包含字母和数字
const isPasswordValid = (value) => /^(?=.*[A-Za-z])(?=.*\d)[\S]{6,20}$/.test(value)

const resetForm = () => {
  newPassword.value = ''
  confirmPassword.value = ''
  showPassword.value = false
  showConfirm.value = false
  submitting.value = false
  touched.value = false
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      resetForm()
    }
  }
)

// 输入内容后实时提示：校验不通过时按钮禁用，同时字段下方给出红色文案
const passwordError = computed(() => {
  if (!newPassword.value) return ''
  return isPasswordValid(newPassword.value) ? '' : '密码需为 6-20 位且包含字母和数字'
})

const confirmError = computed(() => {
  if (!confirmPassword.value) return ''
  return newPassword.value === confirmPassword.value ? '' : '两次输入的密码不一致'
})

const canSubmit = computed(() => {
  return !submitting.value
    && isPasswordValid(newPassword.value)
    && newPassword.value === confirmPassword.value
})

const onClose = () => {
  if (submitting.value) return
  emit('update:visible', false)
}

const onVisibleChange = (value) => {
  if (!value) {
    onClose()
    return
  }
  emit('update:visible', value)
}

const handleConfirm = () => {
  touched.value = true

  if (!isPasswordValid(newPassword.value)) {
    message.show('密码需为 6-20 位且包含字母和数字')
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    message.show('两次输入的密码不一致')
    return
  }

  submitting.value = true
  updatePassword(newPassword.value).then((res) => {
    submitting.value = false

    if (!res.success) {
      message.show(res.message || '修改失败')
      return
    }

    message.show('密码修改成功')
    emit('update:visible', false)
  }).catch(() => {
    submitting.value = false
  })
}
</script>

<style scoped>
.pwd-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.pwd-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pwd-field__label {
  font-size: 13px;
  color: var(--color-ink-soft);
}

.pwd-field__control {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-control);
  background: var(--color-canvas-sunken);
  transition:
    border-color var(--motion-fast) var(--ease-standard),
    background-color var(--motion-fast) var(--ease-standard),
    box-shadow var(--motion-fast) var(--ease-standard);
}

.pwd-field__control:focus-within {
  border-color: var(--color-line-strong);
  background: var(--color-paper);
  box-shadow: 0 2px 12px rgb(0 0 0 / 0.08);
}

.pwd-field__control--error {
  border-color: #e02020;
}

.pwd-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--color-ink);
}

.pwd-input::placeholder {
  color: var(--color-ink-faint);
}

.pwd-eye {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-ink-faint);
  cursor: pointer;
  transition: color var(--motion-fast) var(--ease-standard);
}

.pwd-eye:hover {
  color: var(--color-ink);
}

.pwd-eye svg {
  width: 18px;
  height: 18px;
}

.pwd-field__error {
  font-size: 12px;
  color: #e02020;
}

.pwd-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 4px;
}

.pwd-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgb(255 255 255 / 0.5);
  border-top-color: #fff;
  border-radius: 50%;
  animation: pwd-spin 0.7s linear infinite;
}

@keyframes pwd-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
