import axios from '@/axios'

// 接口前缀
const API_PREFIX = '/user'

/**
 * 获取用户个人资料
 */
export function getUserProfile(userId) {
  const id = userId === 'undefined' || userId === undefined || userId === null ? null : userId
  return axios.post(`${API_PREFIX}/profile`, id === null ? {} : { userId: id })
}

/**
 * 更新用户资料
 */
export function updateUserProfile(data) {
  // 创建 FormData 对象用于文件上传
  const formData = new FormData()
  
  // 被更新的用户 ID（后端必传）
  if (data.userId !== undefined && data.userId !== null) {
    formData.append('userId', data.userId)
  }
  
  // 如果有头像文件，添加到表单
  if (data.avatar && data.avatar instanceof File) {
    formData.append('avatar', data.avatar)
  }

  // 如果有背景图文件，添加到表单
  if (data.backgroundImg && data.backgroundImg instanceof File) {
    formData.append('backgroundImg', data.backgroundImg)
  }

  // 移除背景图：后端用显式标记清空（选择性更新无法把字段置为 NULL）
  if (data.removeBackgroundImg === true) {
    formData.append('removeBackgroundImg', 'true')
  }
  
  // 添加其他字段
  if (data.nickname) formData.append('nickname', data.nickname)
  if (data.xiaohashuId) formData.append('xiaohashuId', data.xiaohashuId)
  if (data.sex !== undefined && data.sex !== null) formData.append('sex', data.sex)
  if (data.birthday) formData.append('birthday', data.birthday)
  if (data.introduction) formData.append('introduction', data.introduction)
  
  return axios.post(`${API_PREFIX}/update`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}


