# 视频笔记发布显示真实上传进度

## Context

当前发布视频笔记时，`PublishModal.vue` 的上传进度是假进度——按 `i / filesToAdd.length` 计算，视频模式下 `filesToAdd.length === 1`，所以只有 0→100 一次跳变，用户看不到真实上传过程。大视频文件上传耗时长，没有进度反馈体验差。

本次改造让视频上传显示真实的 0%→100% 平滑进度，图片上传保持原逻辑不动。后端 `FileController` 已流式接收 `MultipartFile`，无需改动。

## 改动清单

### 1. `d:\critic\xiaoshuting\xiaohashu-vue3\src\api\file.js` — 扩展 uploadFile 签名

向后兼容地加一个 `config` 参数，让调用方可以传 `onUploadProgress` / `timeout` 覆盖：

```js
export function uploadFile(formData, config = {}) {
  return axios.post(`${API_PREFIX}/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    ...config
  })
}
```

不传第二参数时行为完全不变，图片上传路径零影响。

### 2. `d:\critic\xiaoshuting\xiaohashu-vue3\src\components\note\PublishModal.vue` — handleFileChange 分叉

第 682-709 行的 `for` 循环内，根据 `noteType.value` 走两条分支：

- **图片分支（原逻辑保留）**：`const res = await uploadFile(formData)`，进度仍用 `Math.round((i / filesToAdd.length) * 100)`。
- **视频分支**：
  ```js
  const res = await uploadFile(formData, {
    timeout: 0,  // 禁用 axios 全局 15s 超时，大视频不会被切断
    onUploadProgress: (e) => {
      if (e.lengthComputable) {
        // 卡 99%，待响应返回后再置 100，避免"100% 但还在等后端"的错觉
        uploadProgress.value = Math.min(99, Math.round((e.loaded / e.total) * 100))
      }
    }
  })
  ```
  成功分支（第 694 行）追加 `uploadProgress.value = 100`，失败分支保持原逻辑。

### 3. `PublishModal.vue` 第 125-135 行模板 — 视频升级为进度条

根据 `isVideo` 条件分叉渲染：

- **图片**：维持现有 spinner + "上传中 X%"。
- **视频**：替换为细条形进度条：
  ```html
  <div class="w-3/4 h-1.5 bg-white/30 rounded overflow-hidden">
    <div class="h-full bg-white transition-all duration-200"
         :style="{ width: uploadProgress + '%' }"></div>
  </div>
  <div class="text-xs mt-1">{{ uploadProgress }}%</div>
  ```

## 关键设计决策

1. **axios timeout 不改全局**：`axios.js` 第 8 行 `timeout: 15000` 保持不变（避免影响其他接口），只在视频上传请求里通过 `config` 覆盖为 `timeout: 0`。图片仍走 15s。
2. **进度卡 99% 不卡 100%**：避免上传完成但后端还在处理时用户看到 100% 以为卡住；待响应返回再置 100。
3. **`e.lengthComputable` 保护**：某些代理/CDN 下该字段可能为 false，加 `if` 保护避免 NaN。
4. **图片视频物理隔离**：`file.js` 默认空 config + `noteType.value === 'video'` 判定分支，两套逻辑互不影响。

## 涉及文件

- `d:\critic\xiaoshuting\xiaohashu-vue3\src\api\file.js`（扩展签名）
- `d:\critic\xiaoshuting\xiaohashu-vue3\src\components\note\PublishModal.vue`（handleFileChange 分叉 + 模板分叉）

## 验证

1. **视频上传**：发布视频笔记，选择一个 20MB+ 的 mp4，观察进度从 0% 平滑涨到 99%，响应返回后跳到 100%，无卡顿。
2. **图片上传回归**：发布图文笔记，选 8 张图片，观察仍是原 spinner + 跳变进度，行为不变。
3. **断网/超时**：视频上传中断网，确认失败分支正常显示"文件 xxx 上传失败"，`isUploading` 复位。
4. **大视频**：上传 100MB+ 视频，确认不被 axios 15s 超时切断。
