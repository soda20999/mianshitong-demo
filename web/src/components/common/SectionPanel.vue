<template>
  <section :class="['section-block', { dark }]">
    <div v-if="title || subtitle" class="head">
      <p v-if="kicker" class="editorial-kicker">{{ kicker }}</p>
      <h3 v-if="title">{{ title }}</h3>
      <p v-if="subtitle">{{ subtitle }}</p>
    </div>
    <div class="body">
      <slot />
    </div>
  </section>
</template>

<script setup>
defineProps({
  dark: {
    type: Boolean,
    default: false
  },
  kicker: {
    type: String,
    default: ""
  },
  title: {
    type: String,
    default: ""
  },
  subtitle: {
    type: String,
    default: ""
  }
});
</script>

<style scoped>
.head {
  margin-bottom: 16px;
}

.body {
  display: grid;
  gap: 12px;
}
</style>
