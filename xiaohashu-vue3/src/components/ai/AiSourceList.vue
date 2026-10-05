<template>
  <div class="ai-sources">
    <p class="ai-sources__head">
      <svg class="ai-sources__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path
          d="M6 4.5h9.5L19 8v11.5H6V4.5Z"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linejoin="round"
        />
        <path d="M9 12h7M9 15.5h5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
      </svg>
      引用笔记 · {{ notes.length }} 篇
    </p>

    <ul class="ai-sources__list">
      <li v-for="(note, index) in notes" :key="note.noteId || index">
        <button type="button" class="ai-source" @click="$emit('open', note)">
          <span class="ai-source__index st-num" aria-hidden="true">{{ index + 1 }}</span>

          <span class="ai-source__body">
            <span class="ai-source__title">{{ note.title || `笔记 ${note.noteId}` }}</span>

            <span class="ai-source__meta">
              <span v-if="note.topicName" class="ai-source__topic"># {{ note.topicName }}</span>
              <span v-if="matchLabel(note)" class="ai-source__score">{{ matchLabel(note) }}</span>
            </span>

            <span v-if="note.reason" class="ai-source__reason">{{ note.reason }}</span>
          </span>

          <svg class="ai-source__arrow" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M9 6l6 6-6 6"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
      </li>
    </ul>
  </div>
</template>

<script setup>
defineProps({
  // NoteRefVO[]：noteId / title / topicName / creatorId / score / judgeScore / reason
  notes: {
    type: Array,
    default: () => []
  }
})

defineEmits(['open'])

const matchLabel = (note) => {
  if (note?.judgeScore != null) return `匹配度 ${note.judgeScore}%`
  if (note?.score != null) return `相似度 ${Math.round(note.score * 100)}%`
  return ''
}
</script>

<style scoped>
.ai-sources {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-line-strong);
}

.ai-sources__head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--color-ink-faint);
}

.ai-sources__icon {
  width: 15px;
  height: 15px;
}

.ai-sources__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ai-source {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-control);
  background: var(--color-canvas-sunken);
  text-align: left;
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    border-color var(--motion-fast) var(--ease-standard);
}

.ai-source:hover {
  border-color: var(--color-line-strong);
  background: var(--color-canvas-deep);
}

.ai-source__index {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  border-radius: var(--radius-pill);
  background: var(--color-paper);
  color: var(--color-ink-soft);
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

.ai-source__body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.ai-source__title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.ai-source__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-ink-faint);
}

.ai-source__topic {
  color: var(--color-ink-soft);
}

.ai-source__score {
  color: var(--color-brand);
}

.ai-source__reason {
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-ink-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.ai-source__arrow {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  margin-top: 2px;
  color: var(--color-ink-faint);
}
</style>