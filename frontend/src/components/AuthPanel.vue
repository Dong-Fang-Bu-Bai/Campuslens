<template>
  <section class="auth-page">
    <article class="auth-card">
      <div class="auth-brand-header">
        <img src="/logo.png" alt="CampusLens" class="auth-brand-logo" />
        <h2>CampusLens</h2>
        <p>{{ labels.subtitle }}</p>
      </div>
      <div class="auth-tabs">
        <button type="button" :class="{ active: mode === 'login' }" @click="$emit('switch-mode', 'login')">{{ labels.login }}</button>
        <button type="button" :class="{ active: mode === 'register' }" @click="$emit('switch-mode', 'register')">{{ labels.register }}</button>
      </div>
      <form @submit.prevent="$emit('submit')">
        <label>
          {{ labels.username }}
          <input :value="form.username" type="text" autocomplete="off" @input="update('username', $event.target.value.trim())" />
        </label>
        <label>
          {{ labels.password }}
          <input :value="form.password" type="password" autocomplete="new-password" @input="update('password', $event.target.value)" />
        </label>
        <label v-if="mode === 'register'">
          {{ labels.email }}
          <input :value="form.email" type="email" :placeholder="labels.emailPlaceholder" @input="update('email', $event.target.value.trim())" />
        </label>
        <button class="primary-btn" type="submit">
          <span class="btn-text">{{ mode === 'login' ? labels.submitLogin : labels.submitRegister }}</span>
        </button>
        <p v-if="message" :class="['mini-message', error ? 'error-message' : 'success-message']">{{ message }}</p>
      </form>
    </article>
  </section>
</template>

<script setup>
const props = defineProps({
  mode: { type: String, required: true },
  form: { type: Object, required: true },
  message: { type: String, default: '' },
  error: { type: Boolean, default: false },
  labels: { type: Object, required: true }
})

const emit = defineEmits(['switch-mode', 'submit', 'update-form'])

function update(field, value) {
  emit('update-form', { ...props.form, [field]: value })
}
</script>
