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
            username: response.data.username
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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-box {
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  padding: 40px;
  width: 100%;
  max-width: 400px;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h1 {
  font-size: 32px;
  color: #333;
  margin: 0 0 8px 0;
  font-weight: 600;
}

.login-header p {
  color: #666;
  margin: 0;
  font-size: 14px;
}

.login-form {
  display: flex;
  flex-direction: column;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: #333;
  font-size: 14px;
  font-weight: 500;
}

.form-group input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.3s;
  box-sizing: border-box;
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
  background: #f0f3ff;
  border: 1px solid #d5d9ff;
  color: #4b5bd6;
  border-radius: 8px;
  padding: 0 12px;
  height: 44px;
  cursor: pointer;
  white-space: nowrap;
}

.code-send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.form-group input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-group input:disabled {
  background-color: #f5f5f5;
  cursor: not-allowed;
}

.error-message {
  color: #e74c3c;
  font-size: 14px;
  margin-bottom: 16px;
  padding: 10px;
  background-color: #fee;
  border-radius: 6px;
  border: 1px solid #fcc;
}

.success-message {
  color: #27ae60;
  font-size: 14px;
  margin-bottom: 16px;
  padding: 10px;
  background-color: #d4edda;
  border-radius: 6px;
  border: 1px solid #c3e6cb;
}

.submit-btn {
  width: 100%;
  padding: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  margin-top: 10px;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.form-footer {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: #666;
}

.form-footer .link {
  color: #667eea;
  text-decoration: none;
  margin-left: 5px;
  font-weight: 500;
}

.form-footer .link:hover {
  text-decoration: underline;
}
</style>

