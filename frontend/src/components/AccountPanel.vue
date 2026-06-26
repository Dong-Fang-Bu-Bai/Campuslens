<template>
  <section class="account-page">
    <header class="account-hero">
      <div class="avatar-editor-shell">
        <UserAvatar
          :avatar-url="profile?.avatarUrl"
          :seed="avatarSeed"
          :alt="labels.avatarAlt"
          :edit-label="labels.changeAvatar"
          :size="78"
          editable
          @edit="openAvatarPicker"
        />
        <input ref="avatarInput" class="avatar-file-input" type="file" accept="image/jpeg,image/png" tabindex="-1" aria-hidden="true" @change="onAvatarSelected" />
      </div>
      <div class="account-identity">
        <p class="eyebrow">{{ labels.kicker }}</p>
        <h3>{{ profile?.username || '—' }}</h3>
        <p>{{ labels.subtitle }}</p>
        <div class="account-chips">
          <span>{{ profile?.admin ? labels.admin : labels.user }}</span>
          <span class="status-chip"><i></i>{{ labels.active }}</span>
        </div>
      </div>
      <button type="button" class="account-refresh" :disabled="loading" @click="$emit('refresh')">
        <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M20 11a8.1 8.1 0 1 0 2.2 5.5"/><path d="M20 4v7h-7"/></svg>
        {{ labels.refresh }}
      </button>
      <p v-if="localAvatarError && !cropFile" class="hero-message error">{{ localAvatarError }}</p>
    </header>

    <div class="account-layout">
      <aside class="account-overview account-card">
        <div class="account-card-heading">
          <span class="account-icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21a8 8 0 0 0-16 0"/><circle cx="12" cy="7" r="4"/></svg>
          </span>
          <div><p>{{ labels.profileKicker }}</p><h4>{{ labels.profileTitle }}</h4></div>
        </div>
        <dl class="account-details">
          <div><dt>{{ labels.username }}</dt><dd>{{ profile?.username || '—' }}</dd></div>
          <div><dt>{{ labels.email }}</dt><dd>{{ profile?.email || labels.noEmail }}</dd></div>
          <div><dt>{{ labels.role }}</dt><dd>{{ profile?.admin ? labels.admin : labels.user }}</dd></div>
          <div><dt>{{ labels.userId }}</dt><dd>#{{ profile?.userId || '—' }}</dd></div>
        </dl>
        <div class="account-note">
          <svg xmlns="http://www.w3.org/2000/svg" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10"/><path d="m9 12 2 2 4-4"/></svg>
          <p><strong>{{ labels.securityTitle }}</strong><span>{{ labels.securityDesc }}</span></p>
        </div>
      </aside>

      <div class="account-settings">
        <section class="account-card account-form email-card">
          <div class="account-card-heading">
            <span class="account-icon">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-10 6L2 7"/></svg>
            </span>
            <div><p>{{ labels.contactKicker }}</p><h4>{{ labels.emailTitle }}</h4></div>
          </div>
          <p class="account-form-desc">{{ profile?.email ? labels.emailExistsDesc : labels.emailMissingDesc }}</p>

          <div v-if="!emailEditing" class="email-summary">
            <div>
              <span>{{ profile?.email ? labels.currentEmail : labels.email }}</span>
              <strong>{{ profile?.email || labels.noEmail }}</strong>
            </div>
            <button type="button" class="account-primary" @click="beginEmailEdit">
              {{ profile?.email ? labels.changeEmail : labels.addEmail }}
            </button>
          </div>

          <form v-else @submit.prevent="$emit('update-email', email)">
            <label>
              <span>{{ profile?.email ? labels.newEmail : labels.email }}</span>
              <input v-model.trim="email" required type="email" maxlength="160" autocomplete="email" :placeholder="labels.emailPlaceholder" />
            </label>
            <div class="account-form-footer">
              <p v-if="emailMessage" :class="emailError ? 'form-message error' : 'form-message success'">{{ emailMessage }}</p>
              <button type="button" class="account-secondary" :disabled="emailSubmitting" @click="cancelEmailEdit">{{ labels.cancel }}</button>
              <button class="account-primary" :disabled="emailSubmitting">{{ emailSubmitting ? labels.saving : labels.saveEmail }}</button>
            </div>
          </form>
          <p v-if="!emailEditing && emailMessage" :class="emailError ? 'form-message standalone error' : 'form-message standalone success'">{{ emailMessage }}</p>
        </section>

        <form class="account-card account-form" @submit.prevent="submitPassword">
          <div class="account-card-heading">
            <span class="account-icon">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="18" height="11" x="3" y="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            </span>
            <div><p>{{ labels.passwordKicker }}</p><h4>{{ labels.passwordTitle }}</h4></div>
          </div>
          <p class="account-form-desc">{{ labels.passwordDesc }}</p>
          <div class="password-grid">
            <label><span>{{ labels.currentPassword }}</span><input v-model="password.currentPassword" required type="password" autocomplete="current-password" :placeholder="labels.currentPasswordPlaceholder" /></label>
            <label><span>{{ labels.newPassword }}</span><input v-model="password.newPassword" required minlength="8" type="password" autocomplete="new-password" :placeholder="labels.newPasswordPlaceholder" /></label>
            <label><span>{{ labels.confirmPassword }}</span><input v-model="password.confirmPassword" required minlength="8" type="password" autocomplete="new-password" :placeholder="labels.confirmPasswordPlaceholder" /></label>
          </div>
          <div class="account-form-footer">
            <p v-if="visiblePasswordMessage" :class="visiblePasswordError ? 'form-message error' : 'form-message success'">{{ visiblePasswordMessage }}</p>
            <button class="account-primary" :disabled="passwordSubmitting">{{ passwordSubmitting ? labels.saving : labels.changePassword }}</button>
          </div>
        </form>

        <section class="account-card session-card">
          <div><strong>{{ labels.sessionTitle }}</strong><p>{{ labels.sessionDesc }}</p></div>
          <button type="button" @click="$emit('logout')">{{ labels.logout }}</button>
        </section>
      </div>
    </div>

    <AvatarCropper
      v-if="cropFile"
      :file="cropFile"
      :labels="labels.cropper"
      :submitting="avatarSubmitting"
      :message="avatarMessage || localAvatarError"
      :error="avatarError || Boolean(localAvatarError)"
      @cancel="closeCropper"
      @save="$emit('update-avatar', $event)"
    />
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import AvatarCropper from './AvatarCropper.vue'
import UserAvatar from './UserAvatar.vue'

const props = defineProps({
  profile: { type: Object, default: null }, loading: { type: Boolean, default: false },
  emailSubmitting: { type: Boolean, default: false }, passwordSubmitting: { type: Boolean, default: false }, avatarSubmitting: { type: Boolean, default: false },
  emailMessage: { type: String, default: '' }, emailError: { type: Boolean, default: false },
  passwordMessage: { type: String, default: '' }, passwordError: { type: Boolean, default: false },
  avatarMessage: { type: String, default: '' }, avatarError: { type: Boolean, default: false },
  labels: { type: Object, required: true }
})

const emit = defineEmits(['refresh', 'update-email', 'update-password', 'update-avatar', 'logout'])
const email = ref('')
const emailEditing = ref(false)
const localPasswordMessage = ref('')
const password = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const avatarInput = ref(null)
const cropFile = ref(null)
const localAvatarError = ref('')

const avatarSeed = computed(() => `${props.profile?.userId || 'user'}:${props.profile?.username || 'campuslens'}`)
const visiblePasswordMessage = computed(() => localPasswordMessage.value || props.passwordMessage)
const visiblePasswordError = computed(() => Boolean(localPasswordMessage.value) || props.passwordError)

watch(() => props.profile?.email, value => { email.value = value || '' }, { immediate: true })
watch(() => props.emailMessage, value => { if (value && !props.emailError) emailEditing.value = false })
watch(() => props.avatarMessage, value => { if (value && !props.avatarError) closeCropper() })
watch(() => props.passwordMessage, value => {
  if (value && !props.passwordError) Object.assign(password, { currentPassword: '', newPassword: '', confirmPassword: '' })
})

function beginEmailEdit() { email.value = props.profile?.email || ''; emailEditing.value = true }
function cancelEmailEdit() { email.value = props.profile?.email || ''; emailEditing.value = false }
function openAvatarPicker() { localAvatarError.value = ''; avatarInput.value?.click() }
function onAvatarSelected(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type)) { localAvatarError.value = props.labels.avatarTypeError; return }
  if (file.size > 5 * 1024 * 1024) { localAvatarError.value = props.labels.avatarSizeError; return }
  localAvatarError.value = ''
  cropFile.value = file
}
function closeCropper() { if (props.avatarSubmitting) return; cropFile.value = null; localAvatarError.value = '' }
function submitPassword() {
  localPasswordMessage.value = ''
  if (password.newPassword !== password.confirmPassword) { localPasswordMessage.value = props.labels.passwordMismatch; return }
  emit('update-password', { currentPassword: password.currentPassword, newPassword: password.newPassword })
}
</script>

<style scoped>
.account-page { width: min(1180px, 100%); margin: 0 auto; display: grid; gap: 20px; }
.account-hero, .account-card { border: 1px solid var(--border-light); background: color-mix(in srgb, var(--bg-card) 92%, transparent); backdrop-filter: blur(22px); box-shadow: var(--shadow-sm); }
.account-hero { position: relative; display: grid; grid-template-columns: auto minmax(0,1fr) auto; align-items: center; gap: 20px; padding: 28px 30px; overflow: hidden; border-radius: var(--radius-lg); }
.account-hero::after { content: ''; position: absolute; right: -70px; top: -110px; width: 260px; height: 260px; border-radius: 50%; background: radial-gradient(circle, rgba(var(--bg-particle-click-rgb),.18), transparent 68%); pointer-events: none; }
.avatar-editor-shell, .account-identity, .account-refresh { position: relative; z-index: 1; }
.avatar-file-input { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.account-identity { min-width: 0; }
.account-identity h3 { margin: 3px 0 5px; color: var(--text-primary); font-size: clamp(24px,3vw,34px); }
.account-identity > p:not(.eyebrow) { margin: 0; color: var(--text-secondary); line-height: 1.6; }
.account-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.account-chips span { display: inline-flex; align-items: center; gap: 6px; min-height: 27px; padding: 0 10px; border: 1px solid rgba(var(--bg-particle-click-rgb),.25); border-radius: 999px; background: rgba(var(--bg-particle-click-rgb),.08); color: var(--color-accent-light); font-size: 11px; font-weight: 800; }
.account-chips i { width: 6px; height: 6px; border-radius: 50%; background: #34d399; box-shadow: 0 0 10px #34d399; }
.account-refresh { display: inline-flex; align-items: center; gap: 8px; min-height: 40px; padding: 0 14px; border: 1px solid var(--border-light); border-radius: 11px; background: rgba(255,255,255,.04); color: var(--text-primary); font-weight: 800; cursor: pointer; }
.hero-message { grid-column: 1/-1; z-index: 1; margin: 0; font-size: 12px; }.hero-message.error { color: #fda4af; }
.account-layout { display: grid; grid-template-columns: minmax(260px,.72fr) minmax(0,1.5fr); gap: 20px; align-items: start; }
.account-card { border-radius: 18px; padding: 24px; }.account-overview { position: sticky; top: 24px; }
.account-card-heading { display: flex; align-items: center; gap: 12px; }.account-icon { display: grid; place-items: center; flex: 0 0 auto; width: 40px; height: 40px; border-radius: 12px; background: rgba(var(--bg-particle-click-rgb),.11); color: var(--color-accent-light); }
.account-card-heading p { margin: 0 0 3px; color: var(--text-secondary); font-size: 10px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }.account-card-heading h4 { margin: 0; color: var(--text-primary); font-size: 17px; }
.account-details { display: grid; gap: 0; margin: 22px 0; }.account-details div { display: grid; grid-template-columns: 86px minmax(0,1fr); gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--border-light); }.account-details dt { color: var(--text-secondary); font-size: 12px; }.account-details dd { margin: 0; color: var(--text-primary); font-size: 13px; font-weight: 800; overflow-wrap: anywhere; text-align: right; }
.account-note { display: flex; gap: 11px; padding: 14px; border: 1px solid rgba(52,211,153,.18); border-radius: 13px; background: rgba(52,211,153,.07); color: #34d399; }.account-note svg { flex: 0 0 auto; margin-top: 2px; }.account-note p { display: grid; gap: 4px; margin: 0; }.account-note strong { color: var(--text-primary); font-size: 12px; }.account-note span { color: var(--text-secondary); font-size: 11px; line-height: 1.55; }
.account-settings { display: grid; gap: 16px; }.account-form-desc { margin: 16px 0 18px; color: var(--text-secondary); font-size: 12.5px; line-height: 1.65; }.account-form label { display: grid; gap: 8px; }.account-form label > span, .email-summary span { color: var(--text-secondary); font-size: 11px; font-weight: 800; }
.account-form input { width: 100%; min-height: 46px; padding: 0 14px; border: 1px solid var(--border-light); border-radius: 12px; outline: 0; background: rgba(3,7,18,.2); color: var(--text-primary); transition: border-color .2s,box-shadow .2s; }.account-form input:focus { border-color: rgba(var(--bg-particle-click-rgb),.55); box-shadow: 0 0 0 3px rgba(var(--bg-particle-click-rgb),.1); }
.email-summary { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 14px; border: 1px solid var(--border-light); border-radius: 13px; background: rgba(255,255,255,.025); }.email-summary div { display: grid; gap: 6px; min-width: 0; }.email-summary strong { color: var(--text-primary); overflow-wrap: anywhere; }
.password-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14px; }.password-grid label:first-child { grid-column: 1/-1; }
.account-form-footer { display: flex; align-items: center; justify-content: flex-end; gap: 10px; min-height: 42px; margin-top: 18px; }.account-primary,.account-secondary { min-height: 42px; padding: 0 18px; border-radius: 11px; font-weight: 900; cursor: pointer; }.account-primary { border: 0; background: var(--color-accent); color: #fff; box-shadow: 0 10px 24px rgba(var(--bg-particle-click-rgb),.18); }.account-secondary { border: 1px solid var(--border-light); background: rgba(255,255,255,.04); color: var(--text-primary); }.account-primary:disabled,.account-secondary:disabled,.account-refresh:disabled { cursor: wait; opacity: .58; }
.form-message { flex: 1; margin: 0; font-size: 12px; line-height: 1.5; }.form-message.standalone { margin-top: 12px; }.form-message.success { color: #34d399; }.form-message.error { color: #fda4af; }
.session-card { display: flex; align-items: center; justify-content: space-between; gap: 20px; }.session-card strong { color: var(--text-primary); font-size: 14px; }.session-card p { margin: 5px 0 0; color: var(--text-secondary); font-size: 12px; line-height: 1.55; }.session-card button { flex: 0 0 auto; min-height: 40px; padding: 0 16px; border: 1px solid rgba(248,113,113,.28); border-radius: 11px; background: rgba(248,113,113,.08); color: #fda4af; font-weight: 800; cursor: pointer; }
@media (max-width:900px) { .account-layout { grid-template-columns: 1fr; }.account-overview { position: static; } }
@media (max-width:620px) { .account-hero { grid-template-columns: auto minmax(0,1fr); gap: 14px; padding: 20px 17px; }.account-refresh { grid-column: 1/-1; justify-content: center; }.account-card { padding: 18px 16px; }.password-grid { grid-template-columns: 1fr; }.password-grid label:first-child { grid-column: auto; }.account-form-footer,.session-card,.email-summary { align-items: stretch; flex-direction: column; }.account-primary,.account-secondary,.session-card button { width: 100%; } }
</style>
