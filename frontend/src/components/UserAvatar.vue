<template>
  <component
    :is="editable ? 'button' : 'span'"
    class="user-avatar"
    :class="{ editable }"
    :style="{ '--avatar-size': `${size}px` }"
    :type="editable ? 'button' : undefined"
    :aria-label="editable ? editLabel : alt"
    @click="editable && $emit('edit')"
  >
    <img v-if="avatarUrl" :src="avatarUrl" :alt="alt" />
    <svg v-else viewBox="0 0 100 100" role="img" :aria-label="alt">
      <rect width="100" height="100" rx="50" class="avatar-base" />
      <rect
        v-for="pixel in pixels"
        :key="`${pixel.x}-${pixel.y}`"
        :x="pixel.x * 16 + 10"
        :y="pixel.y * 16 + 10"
        width="16"
        height="16"
        rx="2"
        class="avatar-pixel"
      />
    </svg>
    <span v-if="editable" class="edit-overlay" aria-hidden="true">
      <svg xmlns="http://www.w3.org/2000/svg" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"/></svg>
    </span>
  </component>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  avatarUrl: { type: String, default: '' },
  seed: { type: String, default: 'campuslens-user' },
  alt: { type: String, default: '用户头像' },
  size: { type: Number, default: 44 },
  editable: { type: Boolean, default: false },
  editLabel: { type: String, default: '更改头像' }
})

defineEmits(['edit'])

const pixels = computed(() => {
  let hash = 2166136261
  for (const char of props.seed) {
    hash ^= char.charCodeAt(0)
    hash = Math.imul(hash, 16777619)
  }
  const result = []
  for (let y = 0; y < 5; y += 1) {
    for (let x = 0; x < 3; x += 1) {
      hash = Math.imul(hash ^ (hash >>> 13), 1274126177)
      if ((hash >>> 0) % 3 !== 0) {
        result.push({ x, y })
        if (x !== 2) result.push({ x: 4 - x, y })
      }
    }
  }
  return result
})
</script>

<style scoped>
.user-avatar { position: relative; display: inline-grid; place-items: center; flex: 0 0 auto; width: var(--avatar-size); height: var(--avatar-size); padding: 0; overflow: hidden; border: 1px solid rgba(var(--bg-particle-click-rgb), .42); border-radius: 50%; background: rgba(var(--bg-particle-click-rgb), .12); color: var(--color-accent-light); box-shadow: inset 0 1px rgba(255,255,255,.14), 0 8px 22px rgba(0,0,0,.2); }
.user-avatar img, .user-avatar svg { display: block; width: 100%; height: 100%; object-fit: cover; }
.avatar-base { fill: color-mix(in srgb, var(--color-accent) 18%, var(--bg-card)); }
.avatar-pixel { fill: currentColor; }
.user-avatar.editable { cursor: pointer; appearance: none; }
.edit-overlay { position: absolute; inset: 0; display: grid; place-items: center; border-radius: 50%; background: rgba(2,6,23,.68); color: #fff; opacity: 0; transform: scale(.92); transition: opacity .2s ease, transform .2s ease; }
.user-avatar.editable:hover .edit-overlay, .user-avatar.editable:focus-visible .edit-overlay { opacity: 1; transform: scale(1); }
.user-avatar.editable:focus-visible { outline: 3px solid rgba(var(--bg-particle-click-rgb), .25); outline-offset: 3px; }
@media (hover: none) { .user-avatar.editable .edit-overlay { inset: auto 0 0 auto; width: 34%; height: 34%; opacity: 1; transform: none; background: var(--color-accent); } }
</style>
