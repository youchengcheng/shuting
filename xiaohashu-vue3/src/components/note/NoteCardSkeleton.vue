<template>
  <div class="note-skeleton" aria-hidden="true">
    <div class="note-skeleton__media st-skeleton"></div>

    <div class="note-skeleton__body">
      <span class="note-skeleton__line st-skeleton"></span>
      <span class="note-skeleton__line note-skeleton__line--short st-skeleton"></span>

      <div class="note-skeleton__meta">
        <span class="note-skeleton__avatar st-skeleton"></span>
        <span class="note-skeleton__name st-skeleton"></span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.note-skeleton {
  padding: 10px;
  background: var(--color-paper);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.note-skeleton__media {
  width: 100%;
  aspect-ratio: 3 / 4;
  border-radius: var(--radius-media);
}

.note-skeleton__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 2px 2px;
}

.note-skeleton__line {
  height: 11px;
  border-radius: 5px;
}

.note-skeleton__line--short {
  width: 58%;
}

.note-skeleton__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.note-skeleton__avatar {
  width: 20px;
  height: 20px;
  border-radius: var(--radius-pill);
  flex-shrink: 0;
}

.note-skeleton__name {
  width: 56px;
  height: 10px;
  border-radius: 5px;
}
</style>
