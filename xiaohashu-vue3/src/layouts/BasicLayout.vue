<template>
  <div class="app-shell">
    <AppHeader />

    <div class="app-body">
      <AppSidebar />

      <main class="app-main">
        <div class="app-container">
          <router-view></router-view>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import AppHeader from '@/components/layout/AppHeader.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  padding-top: var(--header-h);
  background: var(--color-canvas);
}

.app-main {
  padding-left: var(--sidebar-w);
  min-width: 0;
}

.app-container {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: 20px 24px 72px;
}

@media (min-width: 1536px) {
  .app-container {
    padding-left: 32px;
    padding-right: 32px;
  }
}

@media (max-width: 1023px) {
  .app-main {
    padding-left: 0;
  }

  .app-container {
    padding: 16px 20px 64px;
  }
}

@media (max-width: 767px) {
  .app-container {
    padding: 12px 16px 56px;
  }
}
</style>
