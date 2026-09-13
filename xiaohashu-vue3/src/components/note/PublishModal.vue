<template>
  <Teleport to="body">
    <!-- 遮罩层 - 调整透明度和模糊效果 -->
    <div v-if="visible" class="fixed inset-0 bg-ink/30 z-[100]"></div>
    
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="transform scale-95 opacity-0"
      enter-to-class="transform scale-100 opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="transform scale-100 opacity-100"
      leave-to-class="transform scale-95 opacity-0"
    >
      <!-- 发布笔记模态框 -->
      <div v-if="visible" class="fixed inset-0 z-[101] flex items-center justify-center p-4">
        <div class="relative bg-paper w-[800px] rounded-panel flex flex-col max-h-[90vh] shadow-panel">
          <!-- 发布中 / 发布成功：过渡浮层 -->
          <Transition name="publish-state">
            <div v-if="isPublishing || isPublishSuccess" class="publish-state">
              <div class="publish-state__inner">
                <span v-if="isPublishSuccess" class="publish-state__check">
                  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M4 12.6l5.1 5.1L20 7.2" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </span>
                <span v-else class="publish-state__spinner" aria-hidden="true"></span>
                <p class="publish-state__text">{{ isPublishSuccess ? successText : pendingText }}</p>
              </div>
            </div>
          </Transition>

          <!-- 顶部标题栏 - 优化间距和分割线 -->
          <div class="p-[24px] flex items-center justify-between px-8 border-b border-line/60 shrink-0">
            <h2 class="text-[18px] font-semibold text-ink">{{ isEditMode ? '编辑笔记' : '发布笔记' }}</h2>
            <button 
              class="w-8 h-8 flex items-center justify-center hover:bg-canvas-sunken/80 rounded-full transition-colors"
              @click="onClose"
            >
              <svg class="w-5 h-5 text-ink-faint" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M18 6L6 18M6 6l12 12" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </button>
          </div>

          <!-- 内容区域 - 优化内边距和间距 -->
          <div class="flex-1 overflow-auto px-8 py-6 min-h-[500px]">
            <!-- 笔记类型选择（编辑时锁定原类型，不提供切换） -->
            <div v-if="!isEditMode" class="mb-6 border-b border-line pb-4">
              <div class="text-sm font-medium text-ink-soft mb-3">笔记类型</div>
              <div class="flex gap-4">
                <button 
                  class="px-4 py-2 rounded-full text-sm font-medium transition-colors border"
                  :class="noteType === 'image' 
                    ? 'border-ink text-ink bg-canvas-sunken' 
                    : 'border-line text-ink-soft hover:border-ink-faint bg-paper'"
                  @click="setNoteType('image')"
                >
                  <div class="flex items-center">
                    <svg class="w-4 h-4 mr-1.5" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <rect x="3" y="3" width="18" height="18" rx="2" stroke-width="2"/>
                      <circle cx="8.5" cy="8.5" r="1.5" fill="currentColor"/>
                      <path d="M21 15l-5-5L5 21" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    图文
                    <!-- 对号标识 -->
                    <svg 
                      v-if="noteType === 'image'" 
                      class="w-4 h-4 ml-1.5 text-ink" 
                      viewBox="0 0 24 24" 
                      fill="none" 
                      stroke="currentColor"
                    >
                      <path d="M20 6L9 17l-5-5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                  </div>
                </button>
                <button 
                  class="px-4 py-2 rounded-full text-sm font-medium transition-colors border"
                  :class="noteType === 'video' 
                    ? 'border-ink text-ink bg-canvas-sunken' 
                    : 'border-line text-ink-soft hover:border-ink-faint bg-paper'"
                  @click="setNoteType('video')"
                >
                  <div class="flex items-center">
                    <svg class="w-4 h-4 mr-1.5" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <rect x="2" y="2" width="20" height="20" rx="2.18" stroke-width="2"/>
                      <path d="M10 8l6 4-6 4V8z" fill="currentColor"/>
                    </svg>
                    视频
                    <!-- 对号标识 -->
                    <svg 
                      v-if="noteType === 'video'" 
                      class="w-4 h-4 ml-1.5 text-ink" 
                      viewBox="0 0 24 24" 
                      fill="none" 
                      stroke="currentColor"
                    >
                      <path d="M20 6L9 17l-5-5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                  </div>
                </button>
              </div>
            </div>

            <!-- 图片/视频上传区域 -->
            <div class="flex gap-4 overflow-x-auto pb-4 -mx-1 px-1">
              <!-- 已上传图片/视频预览 -->
              <div 
                v-for="(item, index) in mediaItems" 
                :key="item.key"
                class="relative w-[135px] h-[135px] rounded-card overflow-hidden bg-canvas-sunken shrink-0 border border-line"
                :class="{
                  'opacity-50': draggedItem === index,
                  'border-ink border-2': dragOverIndex === index && draggedItem !== index,
                  'cursor-grab': !isVideo
                }"
                :draggable="!isVideo"
                @dragstart="startDrag(index)"
                @dragend="endDrag"
                @dragenter="onDragEnter(index)"
                @dragleave="onDragLeave"
                @dragover="onDragOver"
                @drop="onDrop(index)"
              >
                <!-- 上传中状态 -->
                <div
                  v-if="item.kind === 'local' && item.uploading"
                  class="absolute inset-0 bg-black/30 backdrop-blur-sm flex flex-col items-center justify-center text-white"
                >
                  <!-- 图片：spinner + 百分比 -->
                  <template v-if="!isVideo">
                    <svg class="animate-spin h-8 w-8 mb-2" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                      <path class="opacity-75" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" fill="currentColor"></path>
                    </svg>
                    <div class="text-sm">上传中 {{ item.progress }}%</div>
                  </template>
                  <!-- 视频：进度条 + 百分比 -->
                  <template v-else>
                    <div class="w-3/4 h-1.5 bg-white/30 rounded overflow-hidden">
                      <div
                        class="h-full bg-white transition-all duration-200"
                        :style="{ width: item.progress + '%' }"
                      ></div>
                    </div>
                    <div class="text-xs mt-1.5">{{ item.progress }}%</div>
                  </template>
                </div>
                
                <!-- 视频缩略图 -->
                <div 
                  v-if="isVideo"
                  class="w-full h-full relative cursor-zoom-in"
                  @click="previewImage(index)"
                >
                  <video 
                    :src="mediaUrl(item)" 
                    class="w-full h-full object-cover"
                    muted
                  ></video>
                  <!-- 播放按钮覆盖层 -->
                  <div class="absolute inset-0 flex items-center justify-center bg-black/20 hover:bg-black/30 transition-colors">
                    <div class="w-10 h-10 rounded-full bg-paper/80 backdrop-blur-sm flex items-center justify-center">
                      <svg class="w-5 h-5 text-ink-soft" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                        <path d="M8 5v14l11-7z"/>
                      </svg>
                    </div>
                  </div>
                </div>
                
                <!-- 图片预览 -->
                <img 
                  v-else
                  :src="mediaUrl(item)" 
                  class="w-full h-full object-cover cursor-zoom-in"
                  @click="previewImage(index)"
                />
                <button 
                  class="absolute right-2 top-2 w-7 h-7 rounded-full bg-black/50 backdrop-blur-sm flex items-center justify-center hover:bg-black/60 transition-colors"
                  @click.stop="removeFile(index)"
                >
                  <svg class="w-4 h-4 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                    <path d="M18 6L6 18M6 6l12 12" stroke-width="2" stroke-linecap="round"/>
                  </svg>
                </button>
                
                <!-- 拖拽提示图标 - 只在图文模式下显示 -->
                <div 
                  v-if="!isVideo && mediaItems.length > 1"
                  class="absolute left-2 top-2 w-7 h-7 rounded-full bg-black/50 backdrop-blur-sm flex items-center justify-center"
                >
                  <svg class="w-4 h-4 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                    <path d="M8 9h8M8 15h8" stroke-width="2" stroke-linecap="round"/>
                  </svg>
                </div>
                
                <!-- 图片序号 -->
                <div 
                  v-if="!isVideo"
                  class="absolute left-2 bottom-2 w-6 h-6 rounded-full bg-black/50 backdrop-blur-sm flex items-center justify-center text-white text-xs font-medium"
                >
                  {{ index + 1 }}
                </div>
              </div>

              <!-- 上传按钮 -->
              <div 
                v-if="(noteType === 'image' && mediaItems.length < 8) || (noteType === 'video' && mediaItems.length === 0)"
                class="w-[135px] h-[135px] rounded-card border-2 border-dashed border-line flex flex-col items-center 
                justify-center cursor-pointer hover:border-ink-faint hover:bg-canvas-sunken transition-colors bg-canvas-sunken/50 shrink-0"
                @click="triggerUpload"
                :class="{'opacity-50 pointer-events-none': isUploading}"
              >
                <input 
                  type="file" 
                  ref="fileInput"
                  class="hidden"
                  :accept="noteType === 'image' ? 'image/*' : 'video/*'"
                  :multiple="noteType === 'image'"
                  @change="handleFileChange"
                />
                <div class="w-10 h-10 rounded-full bg-paper mb-2 flex items-center justify-center border border-line">
                  <svg class="w-5 h-5 text-ink-soft" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                    <path d="M12 5v14M5 12h14" stroke-width="2" stroke-linecap="round"/>
                  </svg>
                </div>
                <div class="text-[13px] text-ink-faint font-medium">
                  {{ isUploading ? '上传中...' : (noteType === 'image' ? `${mediaItems.length}/8` : '上传视频') }}
                </div>
              </div>
            </div>

            <!-- 拖拽提示 - 只在图文模式下且有多张图片时显示 -->
            <div 
              v-if="!isVideo && mediaItems.length > 1"
              class="text-[13px] text-ink-faint mt-1 flex items-center"
            >
              <svg class="w-4 h-4 mr-1 text-ink-faint" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M12 5v14M5 12h14" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              拖拽可调整图片顺序
            </div>

            <!-- 图片/视频必填提示 -->
            <div 
              v-if="errors.files" 
              class="text-brand text-[13px] mt-1 flex items-center"
            >
              <svg class="w-3.5 h-3.5 mr-1" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <circle cx="12" cy="12" r="10" stroke-width="2"/>
                <path d="M12 8v4M12 16h.01" stroke-width="2" stroke-linecap="round"/>
              </svg>
              {{ noteType === 'image' ? '请上传至少一张图片' : '请上传一个视频' }}
            </div>

            <!-- 标题输入 -->
            <div class="relative mt-2">
              <div class="relative">
                <input 
                  type="text"
                  v-model="title"
                  placeholder="填写标题，可能会帮助更多人看到你的笔记"
                  class="w-full text-[14px] py-4 focus:outline-none border-b border-line placeholder:text-ink-faint transition-colors pr-20"
                  :class="{'border-brand': errors.title}"
                />
                <!-- 清除按钮和字数统计 -->
                <div class="absolute right-0 top-1/2 -translate-y-1/2 flex items-center gap-2">
                  <button 
                    v-if="title"
                    class="w-4 h-4 flex items-center justify-center text-ink-faint hover:text-ink-soft"
                    @click="title = ''"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <circle cx="12" cy="12" r="10" stroke-width="2"/>
                      <path d="M15 9l-6 6M9 9l6 6" stroke-width="2" stroke-linecap="round"/>
                    </svg>
                  </button>
                  <span class="text-[13px] text-ink-faint font-medium">{{ title.length }}/20</span>
                </div>
              </div>
              <!-- 标题必填提示 -->
              <div 
                v-if="errors.title" 
                class="text-brand text-[13px] mt-1 flex items-center"
              >
                <svg class="w-3.5 h-3.5 mr-1" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <circle cx="12" cy="12" r="10" stroke-width="2"/>
                  <path d="M12 8v4M12 16h.01" stroke-width="2" stroke-linecap="round"/>
                </svg>
                请填写标题
              </div>
            </div>

            <!-- 正文输入 -->
            <div class="relative mt-2">
              <div class="relative">
                <textarea 
                  v-model="content"
                  placeholder="添加正文"
                  rows="6"
                  class="w-full text-[14px] py-4 focus:outline-none resize-none placeholder:text-ink-faint pr-20"
                ></textarea>
                <!-- 清除按钮和字数统计 -->
                <div class="absolute right-0 bottom-2 flex items-center gap-2">
                  <button 
                    v-if="content"
                    class="w-4 h-4 flex items-center justify-center text-ink-faint hover:text-ink-soft"
                    @click="content = ''"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <circle cx="12" cy="12" r="10" stroke-width="2"/>
                      <path d="M15 9l-6 6M9 9l6 6" stroke-width="2" stroke-linecap="round"/>
                    </svg>
                  </button>
                  <span class="text-[13px] text-ink-faint font-medium">{{ content.length }}/1000</span>
                </div>
              </div>
            </div>

            <!-- 底部选项区域 -->
            <div class="space-y-2 mt-4">
              <!-- 选择频道（编辑接口不支持 channelId，编辑模式隐藏） -->
              <div v-if="!isEditMode" class="relative border-t border-line">
                <div 
                  class="relative py-4 px-4 mt-2 -mx-4 cursor-pointer hover:bg-canvas-sunken rounded-control flex items-center justify-between"
                  @click="showChannelSelector = !showChannelSelector"
                  ref="channelTriggerRef"
                >
                  <div class="flex items-center text-[14px] text-ink">
                    <svg class="w-4 h-4 mr-3 text-ink-faint" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <path d="M4 4h6v6H4V4zm10 0h6v6h-6V4zM4 14h6v6H4v-6zm10 0h6v6h-6v-6z" stroke-width="2"/>
                    </svg>
                    <span>
                      {{ selectedChannel ? selectedChannel.name : '选择频道' }}
                    </span>
                  </div>
                  <svg 
                    class="w-4 h-4 text-ink-faint transition-transform duration-200"
                    :class="{'rotate-90': showChannelSelector}"
                    viewBox="0 0 24 24" 
                    fill="none" 
                    stroke="currentColor"
                  >
                    <path d="M9 18l6-6-6-6" stroke-width="2" stroke-linecap="round"/>
                  </svg>
                </div>

                <!-- 频道选择下拉框 -->
                <Transition
                  enter-active-class="transition duration-200"
                  enter-from-class="opacity-0 translate-y-2"
                  enter-to-class="opacity-100 translate-y-0"
                  leave-active-class="transition duration-200"
                  leave-from-class="opacity-100"
                  leave-to-class="opacity-0"
                >
                  <div 
                    v-if="showChannelSelector"
                    class="absolute bottom-full left-0 right-0 bg-paper rounded-card shadow-panel p-2 mb-2 z-20 border border-line channel-selector-dropdown"
                    ref="channelSelectorRef"
                  >
                    <div class="max-h-[300px] overflow-auto">
                      <button
                        v-for="channel in channelStore.channels"
                        :key="channel.id"
                        class="w-full px-4 py-3 rounded-control text-left text-[14px] flex items-center justify-between hover:bg-canvas-sunken transition-colors"
                        :class="{'text-ink font-medium': selectedChannel?.id === channel.id}"
                        @click="selectChannel(channel)"
                      >
                        <div class="flex items-center text-ink">
                          <component :is="channel.icon" class="w-4 h-4 mr-2"/>
                          {{ channel.name }}
                        </div>
                        <div v-if="selectedChannel?.id === channel.id" class="text-ink">
                          <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                            <path d="M20 6L9 17l-5-5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                          </svg>
                        </div>
                      </button>
                    </div>
                  </div>
                </Transition>
              </div>

              <!-- 添加话题部分 -->
              <div class="relative border-t border-line">
                <!-- 话题标题 -->
                <div class="py-4 px-4 -mx-4 mt-2">
                  <div class="flex items-center text-[14px] text-ink mb-3">
                    <svg class="w-4 h-4 mr-3 text-ink-faint" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z" stroke-width="2"/>
                      <line x1="7" y1="7" x2="7.01" y2="7" stroke-width="2"/>
                    </svg>
                    <span>添加话题</span>
                  </div>
                  
                  <!-- 话题输入和已选话题 -->
                  <div class="relative">
                    <div class="flex flex-wrap items-center gap-2 min-h-[40px] p-2 border border-line rounded-control focus-within:border-brand">
                      <!-- 已选话题标签 -->
                      <div 
                        v-for="(topic, index) in selectedTopics" 
                        :key="index"
                        class="flex items-center bg-canvas-sunken text-ink px-2 py-1 rounded-full text-[13px]"
                      >
                        <span class="mr-1">#{{ topic.name }}</span>
                        <button 
                          class="w-4 h-4 flex items-center justify-center text-ink-faint hover:text-ink-soft"
                          @click.stop="removeTopic(topic)"
                        >
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" class="w-3 h-3">
                            <path d="M18 6L6 18M6 6l12 12" stroke-width="2" stroke-linecap="round"/>
                          </svg>
                        </button>
                      </div>
                      
                      <!-- 话题输入框 -->
                      <input 
                        ref="topicInputRef"
                        v-model="topicKeyword"
                        type="text"
                        :placeholder="isEditMode ? '选择已有话题' : '添加话题，如：美食探店'"
                        class="flex-1 min-w-[150px] outline-none text-[14px]"
                        @input="onTopicInput"
                        @keydown.enter.prevent="handleTopicEnter"
                        @keydown.backspace="handleTopicBackspace"
                        @focus="isTopicInputActive = true"
                        @blur="handleTopicBlur"
                        @click="isTopicInputActive = true"
                      />
                    </div>

                    <div v-if="errors.topic" class="mt-1 text-[13px] text-brand">
                      选择频道时请同时添加一个话题
                    </div>

                    <!-- 话题建议列表 -->
                    <div 
                      v-if="isTopicInputActive && (topicSuggestions.length > 0 || isLoadingTopics || topicKeyword.trim())"
                      class="absolute bottom-full left-0 right-0 bg-paper rounded-card shadow-panel p-2 mb-2 z-20 border border-line max-h-[300px] overflow-auto"
                    >
                      <!-- 加载中状态 -->
                      <div v-if="isLoadingTopics" class="py-3 px-4 text-ink-faint text-center">
                        <div class="flex items-center justify-center">
                          <svg class="animate-spin h-5 w-5 mr-2" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                            <path class="opacity-75" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" fill="currentColor"></path>
                          </svg>
                          正在搜索话题...
                        </div>
                      </div>
                      
                      <!-- 话题列表 -->
                      <button
                        v-for="topic in topicSuggestions"
                        :key="topic.id"
                        class="w-full px-4 py-3 rounded-control text-left text-[14px] flex items-center hover:bg-canvas-sunken transition-colors"
                        @click="selectTopic(topic)"
                      >
                        <svg class="w-4 h-4 mr-2 text-ink-faint" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                          <path d="M7 20l4-16m2 16l4-16M6 9h14M4 15h14" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                        </svg>
                        <span class="text-ink">{{ topic.name }}</span>
                      </button>
                      
                      <!-- 添加新话题选项 -->
                      <button
                        v-if="!isEditMode && topicKeyword.trim() && !isLoadingTopics && !isTopicExistsInSuggestions"
                        class="w-full px-4 py-3 rounded-control text-left text-[14px] flex items-center justify-between hover:bg-canvas-sunken transition-colors"
                        @click="createNewTopic"
                      >
                        <div class="flex items-center">
                          <svg class="w-4 h-4 mr-2 text-ink-faint" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                            <path d="M7 20l4-16m2 16l4-16M6 9h14M4 15h14" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                          </svg>
                          <span class="text-ink">{{ topicKeyword.trim() }}</span>
                        </div>
                        <span class="text-ink text-xs font-medium">添加新话题</span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部按钮区域 -->
          <div class="border-t border-line shrink-0">
            <div class="px-8 py-6 flex gap-4">
              <!-- 取消按钮 -->
              <button 
                class="st-btn st-btn-ghost w-32 h-12 text-[16px]"
                :disabled="isPublishing"
                @click="onClose"
              >
                取消
              </button>
              <!-- 发布按钮 -->
              <button 
                class="st-btn st-btn-primary flex-1 h-12 text-[16px] flex items-center justify-center gap-2"
                :disabled="isPublishing"
                @click="handlePublish"
              >
                <span v-if="isPublishing" class="publish-state__spinner publish-state__spinner--sm" aria-hidden="true"></span>
                {{ isPublishing ? (isEditMode ? '保存中…' : '发布中…') : (isEditMode ? '保存修改' : '发布笔记') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 图片预览组件 -->
    <ImagePreview
      v-model:visible="showImagePreview"
      :images="mediaItems.map(mediaUrl)"
      :initial-index="currentPreviewIndex"
    />
    
    <!-- 视频预览组件 -->
    <VideoPreview
      v-model:visible="showVideoPreview"
      :video-url="currentVideoUrl"
    />
  </Teleport>
</template>

<script setup>
import { 
  ref, 
  computed, 
  watch, 
  nextTick, 
  onMounted, 
  onUnmounted, 
  markRaw,
  defineComponent
} from 'vue'
import ImagePreview from '@/components/common/ImagePreview.vue'
import VideoPreview from '@/components/common/VideoPreview.vue'
import { useChannelStore } from '@/stores/channel'
import { getTopicList } from '@/api/topic'
import { uploadFile } from '@/api/file'
import { publishNote, updateNote } from '@/api/note'
import { message } from '@/utils/message'
import { useNoteStore } from '@/stores/note'
import { useRouter } from 'vue-router'

const router = useRouter()
const noteStore = useNoteStore()

// 发布过渡状态：发布中显示遮罩与按钮 loading，发布成功播放勾选动画后再关闭弹窗
const isPublishing = ref(false)
const isPublishSuccess = ref(false)

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  // 传入笔记详情对象即进入编辑模式（锁定类型、隐藏频道、只能选择已有话题）
  editNote: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:visible', 'success'])

const isEditMode = computed(() => Boolean(props.editNote))
// 编辑与发布共用一套过渡浮层，仅文案不同
const successText = computed(() => (isEditMode.value ? '修改成功' : '发布成功'))
const pendingText = computed(() => (isEditMode.value ? '正在保存，请稍候…' : '正在发布，请稍候…'))

const channelStore = useChannelStore()

// 表单数据
const title = ref('')
const content = ref('')
const tags = ref('')
const isVideo = ref(false)
const fileInput = ref(null)

// 媒体统一模型：远程（编辑回填）与本地（新选择）混用
// - 远程：{ kind: 'remote', url }
// - 本地：{ kind: 'local', file, previewUrl, uploading, progress }
const mediaItems = ref([])

let mediaKeySeed = 0

const createLocalItem = (file) => ({
  kind: 'local',
  key: `local-${++mediaKeySeed}`,
  file,
  previewUrl: URL.createObjectURL(file),
  uploading: true,
  progress: 0
})

const createRemoteItem = (url) => ({
  kind: 'remote',
  key: `remote-${++mediaKeySeed}`,
  url
})

// 取媒体展示地址：本地项用 blob 预览地址，远程项直接用后端地址
const mediaUrl = (item) => (item.kind === 'remote' ? item.url : item.previewUrl)

// 是否还有文件在上传中
const isUploading = computed(() =>
  mediaItems.value.some((item) => item.kind === 'local' && item.uploading)
)

// 清空媒体列表，并释放本地 blob 地址
const releaseMedia = () => {
  mediaItems.value.forEach((item) => {
    if (item.kind === 'local' && item.previewUrl) URL.revokeObjectURL(item.previewUrl)
  })
  mediaItems.value = []
}

// 图片预览相关状态
const showImagePreview = ref(false)
const currentPreviewIndex = ref(0)
const showVideoPreview = ref(false)
const currentVideoUrl = ref('')

// 获取所有媒体的预览地址
const previewUrls = computed(() => mediaItems.value.map(mediaUrl))

// 预览图片 / 视频
const previewImage = (index) => {
  const item = mediaItems.value[index]
  if (!item) return

  if (isVideo.value) {
    // 视频预览
    currentVideoUrl.value = mediaUrl(item)
    showVideoPreview.value = true
  } else {
    // 图片预览
    currentPreviewIndex.value = index
    showImagePreview.value = true
  }
}

// 触发文件选择
const triggerUpload = () => {
  fileInput.value?.click()
}

// 笔记类型（图文/视频）
const noteType = ref('image') // 默认为图文类型

// 设置笔记类型
const setNoteType = (type) => {
  // 如果类型没有变化，不做任何处理
  if (noteType.value === type) return
  
  // 更新笔记类型
  noteType.value = type
  
  // 清空媒体列表（释放本地预览地址）
  releaseMedia()

  // 重置视频标志
  isVideo.value = type === 'video'
  
  // 重置文件相关错误
  if (errors.value.files) {
    errors.value.files = false
  }
}

// 处理文件选择
const handleFileChange = async (e) => {
  const selectedFiles = Array.from(e.target.files)
  if (!selectedFiles.length) return

  // 检查文件类型
  if (noteType.value === 'video') {
    if (!selectedFiles[0].type.startsWith('video/')) {
      message.show({ type: 'error', content: '请选择视频文件' })
      e.target.value = ''
      return
    }
    isVideo.value = true

    // 视频模式下，清空之前的媒体，确保只有一个视频
    releaseMedia()
  } else {
    // 图片模式下，过滤出图片文件
    const imageFiles = selectedFiles.filter(file => file.type.startsWith('image/'))
    if (imageFiles.length !== selectedFiles.length) {
      message.show({ type: 'error', content: '请只选择图片文件' })
      e.target.value = ''
      return
    }
    isVideo.value = false
  }

  // 限制文件数量
  let filesToAdd = []
  if (noteType.value === 'video') {
    if (mediaItems.value.length === 0) {
      filesToAdd = [selectedFiles[0]]
    } else {
      message.show({ type: 'error', content: '只能上传一个视频' })
      e.target.value = ''
      return
    }
  } else {
    const remainingSlots = 8 - mediaItems.value.length
    if (remainingSlots <= 0) {
      message.show({ type: 'error', content: '最多只能上传8张图片' })
      e.target.value = ''
      return
    }
    filesToAdd = selectedFiles.slice(0, remainingSlots)
  }

  // 先以本地项入列（每个文件各自展示进度），上传成功后替换为远程地址
  const newItems = filesToAdd.map(createLocalItem)
  mediaItems.value = [...mediaItems.value, ...newItems]

  // 通过数组读取响应式对象，保证进度更新能触发视图刷新
  const findItem = (file) => mediaItems.value.find((item) => item.kind === 'local' && item.file === file)

  try {
    for (const file of filesToAdd) {
      const item = findItem(file)
      if (!item) continue

      const formData = new FormData()
      formData.append('file', file)

      // 视频模式：禁用 axios 全局 15s 超时，并通过 onUploadProgress 上报真实进度
      const uploadConfig = noteType.value === 'video'
        ? {
            timeout: 0,
            onUploadProgress: (ev) => {
              if (ev.lengthComputable) {
                // 卡 99%，待响应返回后再置 100，避免"100% 但还在等后端"的错觉
                item.progress = Math.min(99, Math.round((ev.loaded / ev.total) * 100))
              }
            }
          }
        : {}

      const res = await uploadFile(formData, uploadConfig)

      if (res.success && res.data) {
        // 上传成功：换成远程地址，进度置满
        const index = mediaItems.value.indexOf(item)
        if (index !== -1) {
          if (item.previewUrl) URL.revokeObjectURL(item.previewUrl)
          mediaItems.value.splice(index, 1, createRemoteItem(res.data))
        }
      } else {
        // 上传失败：从列表中移除该项
        const index = mediaItems.value.indexOf(item)
        if (index !== -1) {
          if (item.previewUrl) URL.revokeObjectURL(item.previewUrl)
          mediaItems.value.splice(index, 1)
        }
        message.show({
          type: 'error',
          content: `文件 ${file.name} 上传失败: ${res.message || '未知错误'}`
        })
      }
    }
  } catch (error) {
    console.error('文件上传出错:', error)
    message.show({ type: 'error', content: '文件上传出错，请重试' })
  } finally {
    // 重置文件输入框，允许重新选择相同文件
    e.target.value = ''
  }
}

// 移除文件
const removeFile = (index) => {
  const item = mediaItems.value[index]
  if (!item) return
  // 本地文件需要释放 blob 预览地址
  if (item.kind === 'local' && item.previewUrl) URL.revokeObjectURL(item.previewUrl)
  mediaItems.value.splice(index, 1)
}

// 监听输入并限制字数
watch(title, (newVal) => {
  if (newVal.length > 20) {
    title.value = newVal.slice(0, 20)
  }
})

watch(content, (newVal) => {
  if (newVal.length > 1000) {
    content.value = newVal.slice(0, 1000)
  }
})

// 修改表单验证
const isValid = computed(() => {
  return mediaItems.value.length > 0 && 
    title.value.trim() && 
    title.value.length <= 20 &&
    content.value.length <= 1000
})

// 话题相关状态
const isTopicInputActive = ref(false)
const topicKeyword = ref('')
const topicInputRef = ref(null)
const selectedTopics = ref([]) // 统一存储所有话题（已有话题和新话题）
const topicSuggestions = ref([])
const isLoadingTopics = ref(false)

// 直接处理话题输入，不使用防抖
const onTopicInput = async () => {
  try {
    // 确保输入框处于激活状态
    isTopicInputActive.value = true
    
    // 如果输入为空，清空建议并返回
    if (!topicKeyword.value.trim()) {
      topicSuggestions.value = []
      isLoadingTopics.value = false
      return
    }
    
    // 设置加载状态
    isLoadingTopics.value = true
    
    // 调用话题搜索接口
    const res = await getTopicList(topicKeyword.value.trim())
    console.log('话题搜索结果:', res) // 添加日志，帮助调试
    
    // 处理返回结果
    if (res.success && res.data) {
      // 过滤掉已选择的话题（包括已有话题和新话题）
      const selectedNames = selectedTopics.value.map(t => t.name.toLowerCase())
      
      topicSuggestions.value = res.data.filter(topic => 
        !selectedNames.includes(topic.name.toLowerCase())
      )
      
      console.log('过滤后的话题建议:', topicSuggestions.value) // 添加日志，帮助调试
    } else {
      console.log('获取话题列表:', res.message)
      topicSuggestions.value = []
    }
  } catch (error) {
    console.error('搜索话题失败:', error)
    topicSuggestions.value = []
  } finally {
    isLoadingTopics.value = false
  }
}

// 选择已有话题
const selectTopic = (topic) => {
  if (selectedTopics.value.length >= 1) {
    message.show({ type: 'warning', content: '当前笔记只能添加一个话题' })
    return
  }
  // 检查是否已经添加过相同的话题
  if (selectedTopics.value.some(t => t.name.toLowerCase() === topic.name.toLowerCase())) {
    return
  }
  
  // 添加到已选话题
  selectedTopics.value = [...selectedTopics.value, topic]
  topicKeyword.value = ''
  topicSuggestions.value = []
  
  // 选择话题后保持输入框激活状态
  isTopicInputActive.value = true
  
  // 选择话题后，让输入框重新获得焦点
  nextTick(() => {
    topicInputRef.value?.focus()
  })
}

// 创建新话题
const createNewTopic = () => {
  if (!topicKeyword.value.trim()) return
  if (selectedTopics.value.length >= 1) {
    message.show({ type: 'warning', content: '当前笔记只能添加一个话题' })
    return
  }
  
  const topicName = topicKeyword.value.trim()
  
  // 检查是否已经添加过相同的话题
  if (selectedTopics.value.some(t => t.name.toLowerCase() === topicName.toLowerCase())) {
    return
  }
  
  // 创建新话题对象
  const newTopic = {
    name: topicName,
    isNew: true // 标记为新话题
  }
  
  // 添加到已选话题
  selectedTopics.value = [...selectedTopics.value, newTopic]
  topicKeyword.value = ''
  topicSuggestions.value = []
  
  // 选择话题后保持输入框激活状态
  isTopicInputActive.value = true
  
  // 选择话题后，让输入框重新获得焦点
  nextTick(() => {
    topicInputRef.value?.focus()
  })
}

// 移除话题
const removeTopic = (topic) => {
  selectedTopics.value = selectedTopics.value.filter(t => t !== topic)
}

// 处理回车
const handleTopicEnter = () => {
  if (topicKeyword.value.trim()) {
    // 如果输入的话题已存在于建议列表中，选择该话题
    if (isTopicExistsInSuggestions.value) {
      const existingTopic = topicSuggestions.value.find(
        topic => topic.name.toLowerCase() === topicKeyword.value.trim().toLowerCase()
      )
      if (existingTopic) {
        selectTopic(existingTopic)
        return
      }
    }
    
    // 否则创建新话题
    createNewTopic()
  }
}

// 处理退格键
const handleTopicBackspace = (e) => {
  if (!topicKeyword.value && selectedTopics.value.length) {
    e.preventDefault()
    selectedTopics.value = selectedTopics.value.slice(0, -1)
  }
}

// 处理话题输入框失焦事件
const handleTopicBlur = () => {
  // 使用延时，以便在点击建议项时能够先执行点击事件
  setTimeout(() => {
    // 只有当不在输入状态时才关闭建议列表
    if (!topicKeyword.value.trim()) {
      isTopicInputActive.value = false
    }
  }, 200)
}

// 计算属性：检查当前输入的话题是否已存在于建议列表中
const isTopicExistsInSuggestions = computed(() => {
  if (!topicKeyword.value.trim()) return false
  
  const input = topicKeyword.value.trim().toLowerCase()
  return topicSuggestions.value.some(topic => 
    topic.name.toLowerCase() === input
  )
})

// 修改错误提示状态为对象
const errors = ref({
  title: false,
  files: false,
  topic: false,
  channel: false
})

// 频道相关状态
const showChannelSelector = ref(false)
const selectedChannel = ref(null)
const channelSelectorRef = ref(null)
const channelTriggerRef = ref(null)

// 添加选择频道的处理函数
const selectChannel = (channel) => {
  selectedChannel.value = channel
  showChannelSelector.value = false
}

// 修改点击外部关闭下拉框函数
const closeChannelSelector = (e) => {
  if (!showChannelSelector.value) return
  
  const target = e.target
  const selectorElement = channelSelectorRef.value
  const triggerElement = channelTriggerRef.value
  
  // 如果点击的不是选择器本身，也不是触发按钮，则关闭选择器
  if (selectorElement && 
      !selectorElement.contains(target) && 
      triggerElement && 
      !triggerElement.contains(target)) {
    showChannelSelector.value = false
  }
}

// 修改发布处理，对接后端接口
const handlePublish = async () => {
  // 重置错误状态
  errors.value = {
    title: !title.value.trim(),
    files: mediaItems.value.length === 0,
    topic: Boolean(selectedChannel.value && selectedTopics.value.length === 0),
    channel: false
  }
  
  // 如果有任何错误，阻止发布
  if (errors.value.title || errors.value.files || errors.value.topic || errors.value.channel) {
    if (errors.value.topic) {
      message.show({ type: 'warning', content: '选择频道时请同时添加话题' })
    }
    return
  }
  
  // 检查字数限制
  if (title.value.length > 20 || content.value.length > 1000) {
    return
  }
  
  // 检查是否所有文件都已上传完成
  if (isUploading.value) {
    message.show({ type: 'warning', content: '文件正在上传中，请等待上传完成' })
    return
  }

  // 检查是否有媒体文件
  if (mediaItems.value.length === 0) {
    message.show({ type: 'warning', content: '请上传文件' })
    return
  }

  // 发布中，忽略重复点击
  if (isPublishing.value) return
  
  try {
    // 数据库当前以单个 topic_id 关联笔记，前端也只提交第一个话题。
    const isVideoNote = noteType.value === 'video'
    const mediaUrls = mediaItems.value.map(mediaUrl)

    const noteData = {
      type: isVideoNote ? 1 : 0, // 0 图文，1视频
      title: title.value.trim(),
      content: content.value.trim()
    }

    const selectedTopic = selectedTopics.value[0]
    if (selectedTopic?.id) {
      noteData.topicId = selectedTopic.id
    } else if (isEditMode.value) {
      // 编辑时允许清空话题：显式传 null，后端会同步清空 topicName
      noteData.topicId = null
    } else if (selectedTopic?.name) {
      noteData.topicName = selectedTopic.name
    }

    // 编辑模式不提交 channelId（后端编辑接口不支持频道）
    if (!isEditMode.value && selectedChannel.value?.id) {
      noteData.channelId = selectedChannel.value.id
    }

    // 根据笔记类型添加不同的文件字段
    if (isVideoNote) {
      // 视频笔记：替换视频即整体替换
      noteData.videoUri = mediaUrls[0]
    } else {
      // 图文笔记
      noteData.imgUris = mediaUrls
    }

    // 编辑模式带上笔记 ID
    if (isEditMode.value) {
      noteData.id = props.editNote.id
    }

    isPublishing.value = true
    const res = isEditMode.value ? await updateNote(noteData) : await publishNote(noteData)

    if (res.success) {
      // 成功：先播放成功过渡动画，再关闭弹窗
      isPublishing.value = false
      isPublishSuccess.value = true
      message.show(successText.value)
      setTimeout(() => {
        isPublishSuccess.value = false
        const wasEdit = isEditMode.value
        const editedNoteId = noteData.id
        onClose()
        if (wasEdit) {
          // 编辑：停留当前主页，交给父组件刷新列表
          emit('success', { id: editedNoteId })
        } else {
          router.push('/discover')
          noteStore.requestDiscoverRefresh()
        }
      }, 750)
    } else {
      // 发布 / 保存失败
      isPublishing.value = false
      message.show(res.message || '未知错误')
    }
  } catch (error) {
    isPublishing.value = false
    console.error(isEditMode.value ? '更新笔记出错:' : '发布笔记出错:', error)
    message.show(isEditMode.value ? '保存失败' : '发布失败')
  }
}

// 修改关闭处理，重置所有状态
const onClose = () => {
  // 发布进行中不允许关闭，避免请求中途中断导致状态错乱
  if (isPublishing.value) return

  emit('update:visible', false)
  title.value = ''
  content.value = ''
  releaseMedia()
  isVideo.value = false
  noteType.value = 'image'
  isTopicInputActive.value = false
  topicKeyword.value = ''
  selectedTopics.value = []
  topicSuggestions.value = []
  // 重置所有错误状态
  errors.value = {
    title: false,
    files: false,
    topic: false,
    channel: false
  }
  selectedChannel.value = null
  isPublishSuccess.value = false
}

// 编辑模式：用笔记详情回填表单（类型、话题、媒体）
const fillFromEditNote = (note) => {
  if (!note) return

  releaseMedia()
  title.value = note.title || ''
  content.value = note.content || ''
  isVideo.value = Number(note.type) === 1
  noteType.value = isVideo.value ? 'video' : 'image'

  // 已有话题：编辑时只能沿用 / 清空，无法新建（后端编辑接口不接受 topicName）
  selectedTopics.value = note.topicId || note.topicName
    ? [{ id: note.topicId ?? null, name: note.topicName || '' }]
    : []
  topicKeyword.value = ''
  topicSuggestions.value = []
  isTopicInputActive.value = false

  const imgUris = Array.isArray(note.imgUris) ? note.imgUris : []
  if (isVideo.value) {
    mediaItems.value = note.videoUri ? [createRemoteItem(note.videoUri)] : []
  } else {
    mediaItems.value = imgUris.slice(0, 8).map(createRemoteItem)
  }

  // 编辑不涉及频道，清掉可能残留的选择
  selectedChannel.value = null
  errors.value = { title: false, files: false, topic: false, channel: false }
}

// 打开弹窗（或切换编辑对象）时回填表单
watch([() => props.visible, () => props.editNote], ([visible, note]) => {
  if (visible && note) {
    fillFromEditNote(note)
  }
})

// 分别监听每个字段，只清除对应的错误
watch(title, () => {
  if (errors.value.title) {
    errors.value.title = false
  }
})

watch(mediaItems, () => {
  if (errors.value.files) {
    errors.value.files = false
  }
})

watch([selectedTopics, selectedChannel], () => {
  if (selectedTopics.value.length > 0 || !selectedChannel.value) {
    errors.value.topic = false
  }
})

// 处理 ESC 按键
const handleEscKey = (e) => {
  if (e.key === 'Escape' && props.visible) {
    emit('update:visible', false)
  }
}

// 组件挂载时添加键盘事件监听
onMounted(() => {
  if (channelStore.channels.length === 0) {
    channelStore.loadChannels()
  }
  document.addEventListener('keydown', handleEscKey)
  document.addEventListener('mousedown', closeChannelSelector)
})

// 组件卸载时移除键盘事件监听
onUnmounted(() => {
  document.removeEventListener('keydown', handleEscKey)
  document.removeEventListener('mousedown', closeChannelSelector)
})

// 拖拽相关状态
const draggedItem = ref(null)
const dragOverIndex = ref(null)

// 开始拖拽
const startDrag = (index) => {
  draggedItem.value = index
}

// 拖拽结束
const endDrag = () => {
  draggedItem.value = null
  dragOverIndex.value = null
}

// 拖拽进入目标区域
const onDragEnter = (index) => {
  if (draggedItem.value === null) return
  dragOverIndex.value = index
}

// 拖拽离开目标区域
const onDragLeave = () => {
  dragOverIndex.value = null
}

// 允许放置
const onDragOver = (e) => {
  e.preventDefault()
}

// 放置处理
const onDrop = (index) => {
  if (draggedItem.value === null) return

  // 按拖拽后的顺序重排媒体项
  const list = [...mediaItems.value]
  const [moved] = list.splice(draggedItem.value, 1)
  list.splice(index, 0, moved)
  mediaItems.value = list

  // 重置拖拽状态
  endDrag()
}
</script>

<style scoped>
/* 优化滚动条样式 */
.overflow-auto {
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.1) transparent;
}

.overflow-auto::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.overflow-auto::-webkit-scrollbar-track {
  background: transparent;
}

.overflow-auto::-webkit-scrollbar-thumb {
  background-color: rgba(0, 0, 0, 0.1);
  border-radius: 3px;
}

/* 输入框 placeholder 样式 */
input::placeholder,
textarea::placeholder {
  color: var(--color-ink-faint);
}

input:focus::placeholder,
textarea:focus::placeholder {
  color: var(--color-line-strong);
}

/* 拖拽相关样式 */
[draggable] {
  user-select: none;
}

.cursor-grab {
  cursor: grab;
}

.cursor-grab:active {
  cursor: grabbing;
}

/* 发布中 / 发布成功 的过渡浮层 */
.publish-state {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: inherit;
  background: rgb(255 255 255 / 0.72);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
}

.publish-state__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.publish-state__text {
  font-size: 15px;
  color: var(--color-ink);
}

.publish-state__spinner {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-pill);
  border: 3px solid rgb(255 36 66 / 0.18);
  border-top-color: var(--color-brand);
  animation: publish-spin 720ms linear infinite;
}

.publish-state__spinner--sm {
  width: 18px;
  height: 18px;
  border-width: 2px;
  border-color: rgb(255 255 255 / 0.45);
  border-top-color: #fff;
}

.publish-state__check {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-brand);
  animation: publish-pop 320ms var(--ease-standard);
}

.publish-state__check svg {
  width: 44px;
  height: 44px;
}

.publish-state-enter-active,
.publish-state-leave-active {
  transition: opacity 200ms var(--ease-standard);
}

.publish-state-enter-from,
.publish-state-leave-to {
  opacity: 0;
}

@keyframes publish-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes publish-pop {
  from {
    transform: scale(0.6);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .publish-state__spinner {
    animation-duration: 1.6s;
  }

  .publish-state-enter-active,
  .publish-state-leave-active {
    transition-duration: 1ms;
  }
}
</style>
