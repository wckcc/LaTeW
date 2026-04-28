<template>
  <div class="register-container">
    <div class="register-box">
      <div class="register-header">
        <h1>注册账号</h1>
        <p>创建您的LaTeW账号</p>
      </div>
      
      <form @submit.prevent="handleRegister" class="register-form">
        <div class="form-group">
          <label for="email">邮箱</label>
          <input
            id="email"
            v-model="form.email"
            type="email"
            placeholder="请输入邮箱"
            :disabled="loading"
            required
          />
        </div>
        
        <div class="form-group">
          <label for="code">验证码</label>
          <div class="code-input-wrapper">
            <input
              id="code"
              v-model="form.code"
              type="text"
              inputmode="numeric"
              placeholder="请输入6位验证码"
              :disabled="loading || sendingCode"
              required
            />
            <button
              type="button"
              class="code-send-btn"
              @click="handleSendCode"
              :disabled="loading || sendingCode"
              tabindex="-1"
            >
              {{ sendingCode ? '发送中...' : '发送验证码' }}
            </button>
          </div>
        </div>
        
        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>
        
        <div v-if="successMessage" class="success-message">
          {{ successMessage }}
        </div>
        
        <button type="submit" class="submit-btn" :disabled="loading">
          {{ loading ? '注册中...' : '注册' }}
        </button>
        
        <div class="form-footer">
          <span>已有账号？</span>
          <router-link to="/login" class="link">立即登录</router-link>
        </div>
      </form>
    </div>
  </div>
</template>

<script>
import { createUser, sendEmailCode } from '../api/user'

export default {
  name: 'Register',
  data() {
    return {
      form: {
        email: '',
        code: ''
      },
      loading: false,
      sendingCode: false,
      errorMessage: '',
      successMessage: ''
    }
  },
  methods: {
    async handleSendCode() {
      this.errorMessage = ''
      this.successMessage = ''

      const email = this.form.email.trim()
      if (!email) {
        this.errorMessage = '请输入邮箱'
        return
      }

      this.sendingCode = true
      try {
        await sendEmailCode(email)
        this.successMessage = '验证码已发送至您的邮箱，请查收'
      } catch (error) {
        this.errorMessage = error.message || '发送验证码失败'
      } finally {
        this.sendingCode = false
      }
    },
    async handleRegister() {
      // 清除之前的消息
      this.errorMessage = ''
      this.successMessage = ''
      
      // 表单验证
      if (!this.form.email.trim()) {
        this.errorMessage = '请输入邮箱'
        return
      }
      if (!this.form.code.trim()) {
        this.errorMessage = '请输入验证码'
        return
      }
      
      this.loading = true
      
      try {
        const userData = {
          email: this.form.email.trim(),
          code: this.form.code.trim()
        }
        
        const response = await createUser(userData)
        
        // 注册成功
        this.successMessage = '注册成功！请前往登录页面，使用邮箱和密码登录（默认密码123456）'
        
        // 清空表单
        this.form = {
          email: '',
          code: ''
        }
        
        // 3秒后跳转到登录页面
        setTimeout(() => {
          this.$router.push('/login')
        }, 3000)
      } catch (error) {
        this.errorMessage = error.message || '注册失败，请重试'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 32px;
}

.register-box {
  background: var(--fluent-surface);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  border-radius: var(--fluent-radius-lg);
  border: 1px solid var(--fluent-border);
  box-shadow: var(--fluent-shadow);
  padding: 40px;
  width: 100%;
  max-width: 440px;
}

.register-header {
  text-align: center;
  margin-bottom: 30px;
}

.register-header h1 {
  font-size: 28px;
  color: var(--fluent-text-1);
  margin: 0 0 10px 0;
  font-weight: 700;
}

.register-header p {
  color: var(--fluent-text-2);
  margin: 0;
  font-size: 14px;
}

.register-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-group {
  margin-bottom: 0;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: var(--fluent-text-2);
  font-size: 13px;
  font-weight: 600;
}

.form-group input {
  width: 100%;
  min-height: 46px;
  padding: 12px 16px;
  border: 1px solid rgba(125, 151, 194, 0.3);
  border-radius: 14px;
  font-size: 14px;
  transition: all 0.2s ease;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.72);
}

.form-group input:focus {
  outline: none;
  border-color: rgba(15, 108, 189, 0.5);
  box-shadow: 0 0 0 3px rgba(15, 108, 189, 0.14);
  background: rgba(255, 255, 255, 0.92);
}

.form-group input:disabled {
  background-color: rgba(244, 247, 253, 0.8);
  cursor: not-allowed;
}

.form-info {
  background-color: #f0f7ff;
  border: 1px solid #b3d9ff;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 20px;
}

.form-info p {
  margin: 0;
  font-size: 13px;
  color: #0066cc;
  line-height: 1.5;
}

.form-info strong {
  color: #004499;
}

.error-message {
  color: #7e2b2e;
  font-size: 14px;
  padding: 12px 14px;
  background: rgba(250, 223, 224, 0.78);
  border-radius: 14px;
  border: 1px solid rgba(196, 43, 28, 0.16);
}

.success-message {
  color: #145f32;
  font-size: 14px;
  padding: 12px 14px;
  background-color: rgba(218, 242, 221, 0.85);
  border-radius: 14px;
  border: 1px solid rgba(16, 124, 16, 0.14);
}

.code-input-wrapper {
  position: relative;
  display: flex;
  gap: 10px;
}

.code-input-wrapper input {
  flex: 1;
}

.code-send-btn {
  flex: 0 0 auto;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(125, 151, 194, 0.3);
  color: var(--fluent-accent);
  border-radius: 14px;
  padding: 0 12px;
  height: 46px;
  cursor: pointer;
  white-space: nowrap;
  box-shadow: 0 8px 20px rgba(84, 117, 172, 0.12);
}

.code-send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.submit-btn {
  width: 100%;
  min-height: 48px;
  padding: 12px;
  background: linear-gradient(135deg, #0f6cbd 0%, #4f8cff 100%);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 16px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 16px 28px rgba(15, 108, 189, 0.22);
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 20px 32px rgba(15, 108, 189, 0.28);
  background: linear-gradient(135deg, #115ea3 0%, #3d7ff2 100%);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.form-footer {
  text-align: center;
  margin-top: 2px;
  font-size: 14px;
  color: var(--fluent-text-2);
}

.form-footer .link {
  color: var(--fluent-accent);
  text-decoration: none;
  margin-left: 5px;
  font-weight: 600;
}

.form-footer .link:hover {
  text-decoration: underline;
}
</style>

