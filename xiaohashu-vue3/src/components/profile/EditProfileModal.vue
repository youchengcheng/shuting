<template>
  <BaseModal
    :visible="visible"
    @update:visible="$emit('update:visible', $event)"
    title="编辑资料"
    width="640px"
    @confirm="handleConfirm"
  >
    <div>
      <!-- 头像 -->
      <div class="flex items-center gap-4 mb-6">
        <img
          v-if="form.avatar" 
          :src="form.avatar" 
          class="w-20 h-20 rounded-full object-cover border-1 border-line"
        />
        <span v-else class="w-20 h-20 bg-canvas-sunken rounded-full object-cover border border-line flex items-center justify-center">
            <svg class="w-10 h-10 text-ink-faint" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" stroke-width="2" stroke-linecap="round"/>
              <circle cx="12" cy="7" r="4" stroke-width="2" stroke-linecap="round"/>
            </svg>
        </span>
        <div>
          <input 
            type="file" 
            ref="avatarInput"
            accept="image/*"
            class="hidden"
            @change="handleAvatarChange"
          />
          <button 
            class="st-btn st-btn-ghost h-9 px-4 text-[13px]"
            @click="$refs.avatarInput.click()"
          >
            更换头像
          </button>
        </div>
      </div>

      <!-- 背景图 -->
      <div class="mb-6">
        <label class="block text-sm font-medium text-ink-soft mb-2">背景图</label>
        <div class="profile-bg-preview">
          <img v-if="form.backgroundImg" :src="form.backgroundImg" alt="背景图预览" />
          <span v-else class="profile-bg-preview__empty">尚未设置背景图</span>
        </div>
        <div class="flex items-center gap-3 mt-3">
          <input
            type="file"
            ref="backgroundInput"
            accept="image/*"
            class="hidden"
            @change="handleBackgroundChange"
          />
          <button
            type="button"
            class="st-btn st-btn-ghost h-9 px-4 text-[13px]"
            @click="$refs.backgroundInput.click()"
          >
            {{ form.backgroundImg ? '更换背景图' : '上传背景图' }}
          </button>
          <button
            v-if="form.backgroundImg"
            type="button"
            class="st-btn st-btn-ghost h-9 px-4 text-[13px] profile-bg-remove"
            @click="handleBackgroundRemove"
          >
            移除背景图
          </button>
          <span class="text-xs text-ink-faint">支持 jpg/png，不超过 5MB</span>
        </div>
      </div>

      <!-- 表单 -->
      <div class="space-y-6">
        <!-- 昵称 -->
        <div>
          <label class="block text-sm font-medium text-ink-soft mb-2">昵称</label>
          <div class="relative">
            <input
              v-model="form.nickname"
              type="text"
              class="st-input pr-12"
              placeholder="请输入昵称"
              maxlength="24"
              @input="updateNicknameCount"
            />
            <span class="absolute right-3 top-1/2 transform -translate-y-1/2 text-xs text-ink-faint">
              {{ nicknameCount }}/24
            </span>
          </div>
          <p class="mt-1 text-xs text-ink-faint">
            请设置 2-24 个字符，不包括 @<>/ 等无效字符哦
          </p>
        </div>

        <!-- 书亭号 -->
        <div>
          <label class="block text-sm font-medium text-ink-soft mb-2">书亭号</label>
          <div class="relative">
            <input
              v-model="form.xiaohashuId"
              type="text"
              class="st-input pr-12"
              placeholder="请输入书亭号"
              maxlength="15"
              @input="updateXiaohashuIdCount"
            />
            <span class="absolute right-3 top-1/2 transform -translate-y-1/2 text-xs text-ink-faint">
              {{ xiaohashuIdCount }}/15
            </span>
          </div>
          <p class="mt-1 text-xs text-ink-faint">
            6-15 个字符，仅可使用英文（必须）、数字、下划线
          </p>
        </div>

        <!-- 生日 -->
        <div>
          <label class="block text-sm font-medium text-ink-soft mb-2">生日</label>
          <div class="flex gap-3 text-sm">
            <select 
              v-model="form.birthYear"
              class="h-11 px-3 border border-line rounded-control bg-paper focus:outline-none focus:border-brand"
            >
              <option value="">年</option>
              <option 
                v-for="year in birthYears" 
                :key="year" 
                :value="year"
              >{{ year }}</option>
            </select>
            <select 
              v-model="form.birthMonth"
              class="h-11 px-3 border border-line rounded-control bg-paper focus:outline-none focus:border-brand"
            >
              <option value="">月</option>
              <option 
                v-for="month in months" 
                :key="month.value" 
                :value="month.value"
              >{{ month.label }}</option>
            </select>
            <select 
              v-model="form.birthDay"
              class="h-11 px-3 border border-line rounded-control bg-paper focus:outline-none focus:border-brand"
            >
              <option value="">日</option>
              <option 
                v-for="day in getDaysInMonth(form.birthYear, form.birthMonth)" 
                :key="day" 
                :value="day.toString().padStart(2, '0')"
              >{{ day }}日</option>
            </select>
          </div>
        </div>

        <!-- 简介 -->
        <div>
          <label class="block text-sm font-medium text-ink-soft mb-2">简介</label>
          <div class="relative">
            <textarea
              v-model="form.introduction"
              rows="3"
              class="st-input h-auto py-3 pr-16 resize-vertical min-h-[80px] max-h-[200px]"
              placeholder="介绍一下自己吧"
              maxlength="100"
              @input="updateIntroductionCount"
            ></textarea>
            <span class="absolute right-3 bottom-3 text-xs text-ink-faint">
              {{ introductionCount }}/100
            </span>
          </div>
        </div>

        <!-- 性别 -->
        <div>
          <label class="block text-sm font-medium text-ink-soft mb-2">性别</label>
          <div class="flex gap-4">
            <label class="flex items-center cursor-pointer">
              <input
                v-model="form.sex"
                type="radio"
                :value="0"
                class="w-4 h-4 accent-ink"
              />
              <span class="ml-2">女</span>
            </label>
            <label class="flex items-center cursor-pointer">
              <input
                v-model="form.sex"
                type="radio"
                :value="1"
                class="w-4 h-4 accent-ink"
              />
              <span class="ml-2">男</span>
            </label>
          </div>
        </div>
      </div>
    </div>
  </BaseModal>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import BaseModal from '@/components/common/BaseModal.vue'
import { useUserStore } from '@/stores/user'
import { message } from '@/utils/message'
import { updateUserProfile, getUserProfile } from '@/api/user'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'update-success'])

const userStore = useUserStore()

// 背景图文件输入框引用
const backgroundInput = ref(null)

// 表单数据
const form = ref({
  avatar: '',
  avatarFile: null,
  backgroundImg: '',
  backgroundImgFile: null,
  removeBackgroundImg: false,
  nickname: '',
  xiaohashuId: '',
  birthYear: '',
  birthMonth: '',
  birthDay: '',
  introduction: '',
  sex: 0
})

// 昵称字数计数
const nicknameCount = ref(0)
// 书亭号字数计数
const xiaohashuIdCount = ref(0)
// 简介字数计数
const introductionCount = ref(0)

// 更新昵称字数计数
const updateNicknameCount = () => {
  nicknameCount.value = form.value.nickname.length
}

// 更新书亭号字数计数
const updateXiaohashuIdCount = () => {
  xiaohashuIdCount.value = form.value.xiaohashuId.length
}

// 更新简介字数计数
const updateIntroductionCount = () => {
  introductionCount.value = form.value.introduction.length
}

// 监听 visible 变化，当打开模态框时初始化表单数据
watch(() => props.visible, (newValue) => {
  if (newValue) {
    initFormData()
  }
})

// 在组件挂载时初始化表单数据
onMounted(() => {
  if (props.visible) {
    initFormData()
  }
})

// 初始化表单数据
const initFormData = () => {
  if (userStore.profile) {
    // 从 userStore 获取数据并填充表单
    const profile = userStore.profile
    
    // 处理生日格式 (从 "YYYY-MM-DD" 格式拆分为年、月、日)
    let birthYear = ''
    let birthMonth = ''
    let birthDay = ''
    
    if (profile.birthday) {
      const birthdayParts = profile.birthday.split('-')
      if (birthdayParts.length === 3) {
        birthYear = birthdayParts[0]
        birthMonth = birthdayParts[1]
        birthDay = birthdayParts[2]
      }
    }
    
    form.value = {
      avatar: profile.avatar || '',
      avatarFile: null,
      backgroundImg: profile.backgroundImg || '',
      backgroundImgFile: null,
      removeBackgroundImg: false,
      nickname: profile.nickname || '',
      xiaohashuId: profile.xiaohashuId || '',
      birthYear,
      birthMonth,
      birthDay,
      introduction: profile.introduction || '',
      sex: profile.sex !== null ? profile.sex : 0
    }
    
    // 初始化昵称字数计数
    nicknameCount.value = form.value.nickname.length
    // 初始化书亭号字数计数
    xiaohashuIdCount.value = form.value.xiaohashuId.length
    // 初始化简介字数计数
    introductionCount.value = form.value.introduction.length
  }
}

// 生成年份选项（从当前年份开始，往前推算100年）
const birthYears = computed(() => {
  const currentYear = new Date().getFullYear()
  const years = []
  // 从当前年份开始，往前推算100年
  for (let i = currentYear; i >= currentYear - 100; i--) {
    years.push(i.toString())
  }
  return years
})

// 获取指定年月的天数
const getDaysInMonth = (year, month) => {
  if (!year || !month) return 31
  return new Date(year, month, 0).getDate()
}

// 月份选项
const months = [
  { value: '01', label: '1月' },
  { value: '02', label: '2月' },
  { value: '03', label: '3月' },
  { value: '04', label: '4月' },
  { value: '05', label: '5月' },
  { value: '06', label: '6月' },
  { value: '07', label: '7月' },
  { value: '08', label: '8月' },
  { value: '09', label: '9月' },
  { value: '10', label: '10月' },
  { value: '11', label: '11月' },
  { value: '12', label: '12月' }
]

// 更新成功到整页刷新之间的间隔（毫秒）：留出时间让“更新成功”提示可见
const PROFILE_REFRESH_DELAY = 600

// 表单验证和提交
const handleConfirm = async () => {
  // 验证表单
  if (!form.value.nickname.trim()) {
    message.show('请输入昵称')
    return
  }
  
  // 昵称长度验证
  const nickname = form.value.nickname.trim()
  if (nickname.length < 2 || nickname.length > 24) {
    message.show('昵称长度应为 2-24 个字符')
    return
  }
  
  // 昵称特殊字符验证
  const invalidCharsRegex = /[@<>/\\:*?"'|]/
  if (invalidCharsRegex.test(nickname)) {
    message.show('昵称不能包含 @<>/ 等特殊字符')
    return
  }

  // 书亭号验证
  if (!form.value.xiaohashuId.trim()) {
    message.show('请输入书亭号')
    return
  }
  
  // 书亭号长度验证
  const xiaohashuId = form.value.xiaohashuId.trim()
  if (xiaohashuId.length < 6 || xiaohashuId.length > 15) {
    message.show('书亭号长度应为 6-15 个字符')
    return
  }

  // 书亭号格式验证：必须包含英文字母，且只能包含英文、数字和下划线
  const hasLetter = /[a-zA-Z]/.test(xiaohashuId)
  const validFormat = /^[a-zA-Z0-9_]+$/.test(xiaohashuId)

  if (!hasLetter) {
    message.show('书亭号必须包含英文字母')
    return
  }

  if (!validFormat) {
    message.show('书亭号只能包含英文字母、数字和下划线')
    return
  }

  // 生日为选填：只要填了就必须完整；全部留空则不提交，避免误改用户已保存的生日
  const hasBirthdayInput = !!(form.value.birthYear || form.value.birthMonth || form.value.birthDay)
  if (hasBirthdayInput && (!form.value.birthYear || !form.value.birthMonth || !form.value.birthDay)) {
    message.show('请选择完整的生日信息')
    return
  }

  // 组合生日为 YYYY-MM-DD 格式（未选择时为空字符串）
  const birthday = hasBirthdayInput
    ? `${form.value.birthYear}-${form.value.birthMonth}-${form.value.birthDay}`
    : ''
  
  try {
    // 准备提交的数据
    const profileData = {
      userId: userStore.profile?.userId,
      avatar: form.value.avatarFile, // 如果有新上传的头像文件
      backgroundImg: form.value.backgroundImgFile, // 如果有新上传的背景图文件
      removeBackgroundImg: form.value.removeBackgroundImg, // 保存时移除背景图
      nickname: form.value.nickname,
      xiaohashuId: form.value.xiaohashuId,
      introduction: form.value.introduction,
      sex: form.value.sex
    }

    // 仅当用户选择了完整生日时才提交，避免把未修改的生日覆盖掉
    if (birthday) {
      profileData.birthday = birthday
    }
    
    // 调用 API 更新用户资料
    const res = await updateUserProfile(profileData)
    if (!res.success) {
      message.show(res.message || '更新失败，请重试')
      return
    }

    message.show('更新成功')

    // 获取最新的用户信息并写回 store：pinia 持久化会同步到 localStorage，
    // 保证整页刷新后头部、侧边栏等位置拿到的是最新资料
    try {
      const profileRes = await getUserProfile()
      if (profileRes.success) {
        userStore.setProfile(profileRes.data)
      }
    } catch (err) {
      console.error('刷新用户资料出错:', err)
    }

    // 触发更新成功事件，通知父组件
    emit('update-success', profileData)

    // 关闭模态框
    emit('update:visible', false)

    // 资料更新成功后整页刷新，避免页面其他位置（头部、笔记列表等）仍展示旧的用户信息
    setTimeout(() => {
      window.location.reload()
    }, PROFILE_REFRESH_DELAY)

  } catch (error) {
    console.error('更新资料出错:', error)
    message.show('更新失败，请重试')
  }
}

// 处理背景图上传
const handleBackgroundChange = (event) => {
  const file = event.target.files[0]
  if (!file) return

  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    message.show('请上传图片文件')
    return
  }

  // 验证文件大小（限制为 5MB）
  if (file.size > 5 * 1024 * 1024) {
    message.show('图片大小不能超过 5MB')
    return
  }

  // 存储文件对象
  form.value.backgroundImgFile = file
  // 重新选择图片时撤销「移除」标记
  form.value.removeBackgroundImg = false

  // 创建临时 URL 用于预览
  const reader = new FileReader()
  reader.onload = (e) => {
    form.value.backgroundImg = e.target.result
  }
  reader.readAsDataURL(file)
}

// 处理背景图移除（保存时生效）
const handleBackgroundRemove = () => {
  form.value.backgroundImg = ''
  form.value.backgroundImgFile = null
  form.value.removeBackgroundImg = true
  if (backgroundInput.value) {
    backgroundInput.value.value = ''
  }
  message.show('保存后移除')
}

// 处理头像上传
const handleAvatarChange = (event) => {
  const file = event.target.files[0]
  if (!file) return
  
  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    message.show('请上传图片文件')
    return
  }
  
  // 验证文件大小（限制为 5MB）
  if (file.size > 5 * 1024 * 1024) {
    message.show('图片大小不能超过 5MB')
    return
  }
  
  // 存储文件对象
  form.value.avatarFile = file
  
  // 创建临时 URL 用于预览
  const reader = new FileReader()
  reader.onload = (e) => {
    form.value.avatar = e.target.result
  }
  reader.readAsDataURL(file)
}
</script>

<style scoped>
/* 移除背景图按钮 */
.profile-bg-remove {
  color: var(--color-ink-faint);
}

.profile-bg-remove:hover {
  color: var(--color-brand);
}

/* 背景图预览 */
.profile-bg-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 120px;
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-control);
  background: var(--color-canvas-sunken);
}

.profile-bg-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-bg-preview__empty {
  font-size: 13px;
  color: var(--color-ink-faint);
}

/* 自定义下拉框样式 */
select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke='%23666666'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='M19 9l-7 7-7-7'%3E%3C/path%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  background-size: 16px;
  padding-right: 32px;
  min-width: 80px;
}

select:focus {
  outline: none;
  border-color: var(--color-line-strong);
}

/* 禁用状态下的输入框样式 */
input:disabled {
  background-color: var(--color-canvas-sunken);
  cursor: not-allowed;
}
</style> 
