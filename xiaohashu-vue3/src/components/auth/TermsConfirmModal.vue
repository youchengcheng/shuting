<template>
  <div v-if="visible" class="terms-layer">
    <!-- 遮罩层 -->
    <div class="terms-mask" @click="onClose"></div>

    <!-- 确认框 -->
    <div class="terms-panel" role="dialog" aria-modal="true" aria-label="阅读并同意">
      <h3 class="terms-title">阅读并同意</h3>

      <ul class="terms-list">
        <li>《用户协议》</li>
        <li>《隐私政策》</li>
        <li>《儿童/青少年个人信息保护规则》</li>
      </ul>

      <div class="terms-actions">
        <button type="button" class="st-btn st-btn-primary terms-action" @click="onConfirm">
          同意并继续
        </button>
        <button type="button" class="st-btn st-btn-ghost terms-action" @click="onClose">
          取消
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'confirm'])

const onClose = () => {
  emit('update:visible', false)
}

const onConfirm = () => {
  emit('confirm')
  onClose()
}
</script>

<style scoped>
.terms-layer {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.terms-mask {
  position: absolute;
  inset: 0;
  background: rgb(20 17 14 / 0.32);
}

.terms-panel {
  position: relative;
  width: 360px;
  max-width: 100%;
  padding: 28px 24px 20px;
  background: var(--color-paper);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
  text-align: center;
}

.terms-title {
  font-size: 18px;
  line-height: 1.4;
  color: var(--color-ink);
}

.terms-list {
  margin: 16px 0 24px;
  padding: 0;
  list-style: none;
  font-size: 14px;
  line-height: 2;
  color: var(--color-ink-soft);
}

.terms-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--color-line);
}

.terms-action {
  width: 100%;
}
</style>
