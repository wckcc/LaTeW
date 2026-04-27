<template>
  <div class="profile-container">
    <!-- 返回按钮 -->
    <div class="back-header">
      <button class="back-btn" @click="goBack">
        ← 返回项目列表
      </button>
    </div>

    <div class="profile-card">
      <h1 class="page-title">个人中心</h1>

      <!-- 头像区域 -->
      <div class="avatar-section">
        <div class="avatar-wrapper">
          <img 
            v-if="userInfo.avatar" 
            :src="userInfo.avatar" 
            alt="用户头像" 
            class="avatar-image"
          />
          <div v-else class="avatar-placeholder">
            {{ getUserInitial() }}
          </div>
          
          <!-- 上传遮罩层 -->
          <div class="avatar-overlay" @click="triggerFileInput">
            <span class="overlay-text">更换头像</span>
          </div>
        </div>
        
        <input 
          ref="fileInput"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          @change="handleFileChange"
          class="file-input"
        />
        
        <p class="avatar-hint">点击头像更换，支持 JPG、PNG、GIF、WEBP 格式，最大 5MB</p>
      </div>

      <!-- 用户信息区域 -->
      <div class="info-section">
        <div class="info-item">
          <label>用户ID</label>
          <span class="info-value">{{ userInfo.id || '-' }}</span>
        </div>
        <div class="info-item">
          <label>用户名</label>
          <div class="username-edit-wrap">
            <template v-if="editingUsername">
              <input
                v-model="usernameInput"
                class="username-input"
                maxlength="32"
                placeholder="请输入用户名"
              />
              <button class="mini-btn save" @click="handleSaveUsername">保存</button>
              <button class="mini-btn cancel" @click="cancelEditUsername">取消</button>
            </template>
            <template v-else>
              <span class="info-value">{{ userInfo.username || '-' }}</span>
              <button class="mini-btn edit" @click="startEditUsername">修改</button>
            </template>
          </div>
        </div>
        <div class="info-item">
          <label>邮箱</label>
          <span class="info-value">{{ userInfo.email || '未绑定' }}</span>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-section">
        <button class="logout-btn" @click="handleLogout">退出登录</button>
      </div>
    </div>

    <!-- 上传进度弹窗 -->
    <div v-if="uploading" class="upload-modal">
      <div class="upload-modal-content">
        <div class="spinner"></div>
        <p>头像上传中...</p>
      </div>
    </div>

    <!-- 提示消息 -->
    <div v-if="message.show" :class="['message-toast', message.type]">
      {{ message.text }}
    </div>
  </div>
</template>

<script>
import { uploadAvatar, getUserById, updateUsername } from '../api/user'
import { getUser, setUser, clearAuth } from '../utils/auth'

export default {
  name: 'Profile',
  data() {
    return {
      userInfo: {
        id: null,
        username: '',
        email: '',
        avatar: ''
      },
      editingUsername: false,
      usernameInput: '',
      uploading: false,
      message: {
        show: false,
        text: '',
        type: 'success'
      }
    }
  },
  mounted() {
    this.loadUserInfo()
  },
  methods: {
    async loadUserInfo() {
      const localUser = getUser()
      if (!localUser || !localUser.userId) {
        this.$router.push('/login')
        return
      }

      try {
        const res = await getUserById(localUser.userId)
        if (res.data) {
          this.userInfo = {
            id: res.data.id,
            username: res.data.username,
            email: res.data.email,
            avatar: res.data.avatar
          }
          this.usernameInput = res.data.username || ''
          // 更新本地存储的用户信息
          setUser({
            userId: res.data.id,
            username: res.data.username,
            avatar: res.data.avatar,
            role: res.data.role || localUser.role || 'user'
          })
        }
      } catch (error) {
        console.error('获取用户信息失败:', error)
        // 使用本地缓存的信息
        this.userInfo = {
          id: localUser.userId,
          username: localUser.username,
          email: '',
          avatar: localUser.avatar || ''
        }
        this.usernameInput = localUser.username || ''
      }
    },

    startEditUsername() {
      this.usernameInput = this.userInfo.username || ''
      this.editingUsername = true
    },

    cancelEditUsername() {
      this.editingUsername = false
      this.usernameInput = this.userInfo.username || ''
    },

    async handleSaveUsername() {
      const nextName = (this.usernameInput || '').trim()
      if (!nextName) {
        this.showMessage('用户名不能为空', 'error')
        return
      }
      if (nextName.length < 2 || nextName.length > 32) {
        this.showMessage('用户名长度需在2-32个字符之间', 'error')
        return
      }
      try {
        const res = await updateUsername(this.userInfo.id, nextName)
        if (res.data) {
          this.userInfo.username = res.data.username || nextName
          this.editingUsername = false
          const localUser = getUser() || {}
          setUser({
            ...localUser,
            userId: this.userInfo.id,
            username: this.userInfo.username,
            avatar: this.userInfo.avatar,
            role: localUser.role || 'user'
          })
          this.showMessage('用户名修改成功', 'success')
        }
      } catch (error) {
        this.showMessage(error.message || '用户名修改失败', 'error')
      }
    },

    getUserInitial() {
      if (this.userInfo.username) {
        return this.userInfo.username.charAt(0).toUpperCase()
      }
      return 'U'
    },

    triggerFileInput() {
      this.$refs.fileInput.click()
    },

    async handleFileChange(event) {
      const file = event.target.files[0]
      if (!file) return

      // 重置 input，允许重复选择同一文件
      event.target.value = ''

      // 验证文件类型
      const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
      if (!allowedTypes.includes(file.type)) {
        this.showMessage('不支持的文件格式，请选择 JPG、PNG、GIF 或 WEBP 格式', 'error')
        return
      }

      // 验证文件大小 (5MB)
      const maxSize = 5 * 1024 * 1024
      if (file.size > maxSize) {
        this.showMessage('文件大小不能超过 5MB', 'error')
        return
      }

      // 上传头像
      this.uploading = true
      try {
        const res = await uploadAvatar(this.userInfo.id, file)
        if (res.data) {
          this.userInfo.avatar = res.data
          // 更新本地存储
          const localUser = getUser()
          setUser({
            ...localUser,
            avatar: res.data
          })
          this.showMessage('头像上传成功', 'success')
        }
      } catch (error) {
        console.error('头像上传失败:', error)
        this.showMessage(error.message || '头像上传失败，请重试', 'error')
      } finally {
        this.uploading = false
      }
    },

    showMessage(text, type = 'success') {
      this.message = { show: true, text, type }
      setTimeout(() => {
        this.message.show = false
      }, 3000)
    },

    goBack() {
      this.$router.push('/projects')
    },

    handleLogout() {
      if (confirm('确定要退出登录吗？')) {
        clearAuth()
        this.$router.push('/login')
      }
    }
  }
}
</script>

<style scoped>
.profile-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
  box-sizing: border-box;
}

.back-header {
  max-width: 500px;
  margin: 0 auto 20px;
}

.back-btn {
  background: rgba(255, 255, 255, 0.2);
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.3s;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.profile-card {
  max-width: 500px;
  margin: 0 auto;
  background: white;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

.page-title {
  text-align: center;
  font-size: 28px;
  color: #333;
  margin: 0 0 30px;
  font-weight: 600;
}

/* 头像区域 */
.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 30px;
}

.avatar-wrapper {
  position: relative;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
}

.avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  font-weight: 500;
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}

.overlay-text {
  color: white;
  font-size: 14px;
  font-weight: 500;
}

.file-input {
  display: none;
}

.avatar-hint {
  margin-top: 12px;
  font-size: 12px;
  color: #999;
  text-align: center;
}

/* 用户信息区域 */
.info-section {
  border-top: 1px solid #eee;
  padding-top: 25px;
  margin-bottom: 30px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 0;
  border-bottom: 1px solid #f5f5f5;
}

.info-item:last-child {
  border-bottom: none;
}

.info-item label {
  font-size: 14px;
  color: #666;
}

.info-value {
  font-size: 15px;
  color: #333;
  font-weight: 500;
}

.username-edit-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.username-input {
  width: 180px;
  height: 32px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  padding: 0 10px;
  font-size: 14px;
}

.username-input:focus {
  outline: none;
  border-color: #667eea;
}

.mini-btn {
  border: none;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
}

.mini-btn.edit {
  background: #eef2ff;
  color: #4b5bd6;
}

.mini-btn.save {
  background: #e8f7ee;
  color: #1f8b4c;
}

.mini-btn.cancel {
  background: #f5f5f5;
  color: #666;
}

/* 操作按钮 */
.action-section {
  display: flex;
  justify-content: center;
}

.logout-btn {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.logout-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(231, 76, 60, 0.4);
}

/* 上传进度弹窗 */
.upload-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.upload-modal-content {
  background: white;
  padding: 30px 50px;
  border-radius: 12px;
  text-align: center;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 15px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.upload-modal-content p {
  margin: 0;
  color: #333;
  font-size: 14px;
}

/* 提示消息 */
.message-toast {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 30px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  z-index: 2000;
  animation: slideDown 0.3s ease;
}

.message-toast.success {
  background: #d4edda;
  color: #155724;
  border: 1px solid #c3e6cb;
}

.message-toast.error {
  background: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

/* 响应式适配 */
@media (max-width: 540px) {
  .profile-card {
    padding: 30px 20px;
  }

  .avatar-wrapper {
    width: 100px;
    height: 100px;
  }

  .avatar-placeholder {
    font-size: 40px;
  }
}
</style>
