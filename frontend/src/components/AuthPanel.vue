<template>
  <section class="auth-page">
    <article class="auth-card">
      <div class="auth-brand-header">
        <img src="/logo.png" alt="CampusLens" class="auth-brand-logo" />
        <h2>CampusLens</h2>
        <p>{{ modeSubtitle }}</p>
      </div>
      <div class="auth-tabs">
        <button type="button" :class="{ active: mode === 'login' }" @click="$emit('switch-mode', 'login')">{{ labels.login }}</button>
        <button type="button" :class="{ active: mode === 'register' }" @click="$emit('switch-mode', 'register')">{{ labels.register }}</button>
      </div>
      <form @submit.prevent="$emit('submit')">
        <label v-if="mode === 'login' || mode === 'register'">
          {{ labels.username }}
          <input :value="form.username" required type="text" autocomplete="username" @input="update('username', $event.target.value.trim())" />
        </label>
        <label v-if="mode === 'login' || mode === 'register'">
          {{ labels.password }}
          <input :value="form.password" required minlength="8" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" @input="update('password', $event.target.value)" />
        </label>
        <button v-if="mode === 'login'" class="auth-text-action" type="button" @click="$emit('switch-mode', 'forgot')">
          {{ labels.forgotPassword }}
        </button>
        <label v-if="mode === 'register'">
          {{ labels.email }}
          <input :value="form.email" required type="email" :placeholder="labels.emailPlaceholder" autocomplete="email" @input="update('email', $event.target.value.trim())" />
        </label>
        <label v-if="mode === 'forgot' || mode === 'reset'">
          {{ labels.boundEmail }}
          <input :value="form.email" required type="email" autocomplete="email" :readonly="mode === 'reset'" :placeholder="labels.boundEmailPlaceholder" @input="update('email', $event.target.value.trim())" />
        </label>
        <label v-if="mode === 'reset'">
          {{ labels.verificationCode }}
          <input :value="form.code" required type="text" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" autocomplete="one-time-code" :placeholder="labels.codePlaceholder" @input="update('code', $event.target.value.replace(/\D/g, '').slice(0, 6))" />
        </label>
        <label v-if="mode === 'reset'">
          {{ labels.newPassword }}
          <input :value="form.newPassword" required minlength="8" type="password" autocomplete="new-password" @input="update('newPassword', $event.target.value)" />
        </label>
        <label v-if="mode === 'reset'">
          {{ labels.confirmPassword }}
          <input :value="form.confirmPassword" required minlength="8" type="password" autocomplete="new-password" @input="update('confirmPassword', $event.target.value)" />
        </label>
        <button class="primary-btn" type="submit">
          <span class="btn-text">{{ submitLabel }}</span>
        </button>
        <button v-if="mode === 'forgot' || mode === 'reset'" class="auth-back-action" type="button" @click="$emit('switch-mode', 'login')">
          {{ labels.backToLogin }}
        </button>
        <p v-if="message" :class="['mini-message', error ? 'error-message' : 'success-message']">{{ message }}</p>
      </form>
    </article>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  mode: { type: String, required: true },
  form: { type: Object, required: true },
  message: { type: String, default: '' },
  error: { type: Boolean, default: false },
  labels: { type: Object, required: true }
})

const emit = defineEmits(['switch-mode', 'submit', 'update-form'])

const modeSubtitle = computed(() => {
  if (props.mode === 'forgot') return props.labels.forgotSubtitle
  if (props.mode === 'reset') return props.labels.resetSubtitle
  return props.labels.subtitle
})

const submitLabel = computed(() => ({
  login: props.labels.submitLogin,
  register: props.labels.submitRegister,
  forgot: props.labels.sendCode,
  reset: props.labels.resetPassword
})[props.mode])

function update(field, value) {
  emit('update-form', { ...props.form, [field]: value })
}
</script>
