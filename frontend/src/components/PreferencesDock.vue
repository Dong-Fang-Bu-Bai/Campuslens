<template>
  <div class="preferences-dock">
    <button type="button" @click="$emit('cycle-theme')" :title="labels.theme">
      <span class="pref-icon">◐</span>{{ themeLabel }}
    </button>
    <button type="button" @click="$emit('toggle-language')" :title="labels.language">
      <span class="pref-icon">文</span>{{ languageLabel }}
    </button>
  </div>
</template>

<script setup>
defineProps({
  themeLabel: { type: String, required: true },
  languageLabel: { type: String, required: true },
  labels: { type: Object, required: true }
})

defineEmits(['cycle-theme', 'toggle-language'])
</script>
