<template>
  <section class="auth-page">
    <article class="auth-card">
      <div class="auth-tabs">
        <button type="button" :class="{ active: mode === 'login' }" @click="$emit('switch-mode', 'login')">登录</button>
        <button type="button" :class="{ active: mode === 'register' }" @click="$emit('switch-mode', 'register')">注册</button>
      </div>
      <form @submit.prevent="$emit('submit')">
        <label>
          用户名
          <input :value="form.username" type="text" autocomplete="off" @input="update('username', $event.target.value.trim())" />
        </label>
        <label>
          密码
          <input :value="form.password" type="password" autocomplete="new-password" @input="update('password', $event.target.value)" />
        </label>
        <label v-if="mode === 'register'">
          邮箱
          <input :value="form.email" type="email" placeholder="选填，用于绑定" @input="update('email', $event.target.value.trim())" />
        </label>
        <button class="primary-btn" type="submit">
          <span class="btn-text">{{ mode === 'login' ? '登录' : '注册并登录' }}</span>
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
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['switch-mode', 'submit', 'update-form'])

function update(field, value) {
  emit('update-form', { ...props.form, [field]: value })
}
</script>
