<template>
  <div class="register-container">
    <div class="register-box">
      <div class="register-header">
        <h1>注册账号</h1>
        <p>创建您的LaTeW账号</p>
      </div>
      
      <form @submit.prevent="handleRegister" class="register-form">
        <div class="form-group">
          <label for="username">用户名 <span class="required">*</span></label>
          <input
            id="username"
            v-model="form.username"
            type="text"
            placeholder="请输入用户名"
            required
            :disabled="loading"
          />
        </div>
        
        <div class="form-group">
          <label for="phone">手机号</label>
          <input
            id="phone"
            v-model="form.phone"
            type="tel"
            placeholder="请输入手机号（可选）"
            :disabled="loading"
          />
        </div>
        
        <div class="form-info">
          <p>提示：密码默认为 <strong>123456</strong>，登录后可修改</p>
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
import { createUser } from '../api/user'

export default {
  name: 'Register',
  data() {
    return {
      form: {
        username: '',
        phone: ''
      },
      loading: false,
      errorMessage: '',
      successMessage: ''
    }
  },
  methods: {
    async handleRegister() {
      // 清除之前的消息
      this.errorMessage = ''
      this.successMessage = ''
      
      // 表单验证
      if (!this.form.username.trim()) {
        this.errorMessage = '请输入用户名'
        return
      }
      
      // 手机号验证（如果填写了）
      if (this.form.phone && !/^1[3-9]\d{9}$/.test(this.form.phone)) {
        this.errorMessage = '请输入正确的手机号'
        return
      }
      
      this.loading = true
      
      try {
        const userData = {
          username: this.form.username.trim()
        }
        
        // 如果填写了手机号，则添加到请求数据中
        if (this.form.phone.trim()) {
          userData.phone = this.form.phone.trim()
        }
        
        const response = await createUser(userData)
        
        // 注册成功
        this.successMessage = '注册成功！默认密码为 123456，请前往登录页面登录'
        
        // 清空表单
        this.form = {
          username: '',
          phone: ''
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

.required {
  color: #e74c3c;
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

