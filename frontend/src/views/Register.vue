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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.register-box {
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  padding: 40px;
  width: 100%;
  max-width: 400px;
}

.register-header {
  text-align: center;
  margin-bottom: 30px;
}

.register-header h1 {
  font-size: 28px;
  color: #333;
  margin: 0 0 8px 0;
  font-weight: 600;
}

.register-header p {
  color: #666;
  margin: 0;
  font-size: 14px;
}

.register-form {
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

.form-group input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-group input:disabled {
  background-color: #f5f5f5;
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

