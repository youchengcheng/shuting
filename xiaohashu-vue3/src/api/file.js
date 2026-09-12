import axios from '@/axios'

// 接口前缀
const API_PREFIX = '/oss/file'

/**
 * 上传文件
 * @param {FormData} formData 文件表单数据
 * @param {Object} config 额外 axios 配置（如 onUploadProgress、timeout）
 */
export function uploadFile(formData, config = {}) {
  return axios.post(`${API_PREFIX}/upload`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    ...config
  })
}


