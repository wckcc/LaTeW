<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1>LaTeW</h1>
        <p>LaTeX在线编辑器</p>
      </div>
      
      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label for="email">邮箱</label>
          <input
            id="email"
            v-model="form.email"
            type="email"
            placeholder="请输入邮箱"
            required
            :disabled="loading"
          />
        </div>
        
        <div class="form-group">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="form.password"
            type="password"
            placeholder="请输入密码（默认123456）"
            required
            :disabled="loading"
          />
        </div>
        
        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>
        
        <button type="submit" class="submit-btn" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
        
        <div class="form-footer">
          <span>还没有账号？</span>
          <router-link to="/register" class="link">立即注册</router-link>
        </div>
      </form>
    </div>
  </div>
</template>

<script>
import { login } from '../api/user'
import { setToken, setUser } from '../utils/auth'

export default {
  name: 'Login',
  data() {
    return {
      form: {
        email: '',
        password: ''
      },
      loading: false,
      errorMessage: ''
    }
  },
  methods: {
    async handleLogin() {
      // 清除之前的错误信息
      this.errorMessage = ''
      
      // 表单验证
      if (!this.form.email.trim()) {
        this.errorMessage = '请输入邮箱'
        return
      }
      if (!this.form.password.trim()) {
        this.errorMessage = '请输入密码'
        return
      }
      
      this.loading = true
      
      try {
        const response = await login({
          email: this.form.email.trim(),
          password: this.form.password.trim()
        })
        
        // 保存token和用户信息
        if (response.data) {
          setToken(response.data.token)
          setUser({
            userId: response.data.userId,
            username: response.data.username,
            role: response.data.role || 'user'
          })

          // 登录成功，跳转到项目管理页面
          this.$router.push('/projects')
        }
      } catch (error) {
        this.errorMessage = error.message || '登录失败，请检查邮箱和密码'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 32px;
}

.login-box {
  background: var(--fluent-surface);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  border: 1px solid var(--fluent-border);
  border-radius: var(--fluent-radius-lg);
  box-shadow: var(--fluent-shadow);
  padding: 40px;
  width: 100%;
  max-width: 430px;
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.login-header h1 {
  font-size: 34px;
  color: var(--fluent-text-1);
  margin: 0 0 10px 0;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.login-header p {
  color: var(--fluent-text-2);
  margin: 0;
  font-size: 14px;
}

.login-form {
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
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.75);
}

.code-input-wrapper {
  position: relative;
  display: flex;
  gap: 10px;
}

.code-input-wrapper input {
  flex: 1;
  padding: 12px 14px;
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

