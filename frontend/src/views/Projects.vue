<template>
  <div class="projects-container">
    <!-- 左侧边栏 -->
    <aside class="sidebar">
      <button class="create-project-btn" @click="handleCreateProject">
        创建新项目
      </button>
      
      <nav class="sidebar-nav">
        <div 
          class="nav-item" 
          :class="{ active: activeView === 'all' }"
          @click="switchView('all')"
        >
          所有项目
        </div>
        <div 
          class="nav-item" 
          :class="{ active: activeView === 'mine' }"
          @click="switchView('mine')"
        >
          您的项目
        </div>
        <div 
          class="nav-item" 
          :class="{ active: activeView === 'shared' }"
          @click="switchView('shared')"
        >
          与您共享的
        </div>
        <div 
          class="nav-item" 
          :class="{ active: activeView === 'archived' }"
          @click="switchView('archived')"
        >
          已归档项目
        </div>
        <div 
          class="nav-item" 
          :class="{ active: activeView === 'deleted' }"
          @click="switchView('deleted')"
        >
          已删除项目
        </div>
      </nav>
      
      <div class="tags-section">
        <div class="tags-header">
          <span>管理标签</span>
          <button class="new-tag-btn">+ 新建标签</button>
        </div>
      </div>
    </aside>
    
    <!-- 主内容区 -->
    <main class="main-content">
      <div class="content-header">
        <h1>{{ getViewTitle() }}</h1>
        <div class="header-actions">
          <div class="user-avatar-container" @click="toggleUserMenu">
            <div class="user-avatar">
              {{ getUserInitial() }}
            </div>
            <div v-if="showUserMenu" class="user-menu" @click.stop>
              <div class="user-menu-header">
                <div class="user-menu-avatar">{{ getUserInitial() }}</div>
                <div class="user-menu-info">
                  <div class="user-menu-name">{{ currentUser?.username || '用户' }}</div>
                  <div class="user-menu-id">ID: {{ currentUser?.userId || '-' }}</div>
                </div>
              </div>
              <div class="user-menu-divider"></div>
              <div class="user-menu-body">
                <div class="user-menu-item">
                  <span class="menu-item-label">用户名:</span>
                  <span class="menu-item-value">{{ currentUser?.username || '-' }}</span>
                </div>
                <div class="user-menu-item">
                  <span class="menu-item-label">用户ID:</span>
                  <span class="menu-item-value">{{ currentUser?.userId || '-' }}</span>
                </div>
              </div>
              <div class="user-menu-divider"></div>
              <div class="user-menu-footer">
                <button class="logout-btn" @click="handleLogout">退出登录</button>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="search-bar">
        <input 
          type="text" 
          v-model="searchQuery"
          placeholder="在所有项目中搜索..."
          class="search-input"
        />
      </div>
      
      <div class="projects-table-container">
        <table class="projects-table">
          <thead>
            <tr>
              <th>
                <input 
                  type="checkbox" 
                  v-model="selectAll"
                  @change="handleSelectAll"
                />
              </th>
              <th>标题</th>
              <th>拥有者</th>
              <th>最近一次修改</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="5" class="loading-cell">
                加载中...
              </td>
            </tr>
            <tr v-else-if="filteredProjects.length === 0">
              <td colspan="5" class="empty-cell">
                暂无项目
              </td>
            </tr>
            <tr 
              v-else
              v-for="project in filteredProjects" 
              :key="project.id"
              @dblclick="openProject(project.id)"
            >
              <td>
                <input 
                  type="checkbox" 
                  :value="project.id"
                  v-model="selectedProjects"
                />
              </td>
              <td class="project-title">
                <span @click="openProject(project.id)">{{ project.name || '未命名项目' }}</span>
              </td>
              <td>你</td>
              <td>
                {{ formatDate(project.updatedAt || project.createdAt) }}
              </td>
              <td class="actions-cell">
                <button 
                  class="action-btn" 
                  title="打开"
                  @click="openProject(project.id)"
                >
                  📁
                </button>
                <button 
                  class="action-btn" 
                  title="下载"
                  @click="downloadProject(project.id)"
                >
                  ⬇️
                </button>
                <button 
                  class="action-btn" 
                  title="PDF"
                  @click="exportPDF(project.id)"
                >
                  📄
                </button>
                <button 
                  class="action-btn delete-btn" 
                  title="删除"
                  @click="deleteProject(project.id)"
                >
                  🗑️
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      
      <div class="projects-footer">
        显示 {{ filteredProjects.length }} 个项目 (共 {{ projects.length }} 个)
      </div>
    </main>
  </div>
</template>

<script>
import { getAllProjects, getProjectsByUser, deleteProject as deleteProjectAPI } from '../api/project'
import { getUser, clearAuth } from '../utils/auth'

export default {
  name: 'Projects',
  data() {
    return {
      activeView: 'all', // all, mine, shared, archived, deleted
      projects: [],
      loading: false,
      searchQuery: '',
      selectedProjects: [],
      selectAll: false,
      showUserMenu: false,
      currentUser: null
    }
  },
  computed: {
    filteredProjects() {
      let filtered = this.projects
      
      // 根据视图过滤
      if (this.activeView === 'mine') {
        const user = getUser()
        if (user) {
          filtered = filtered.filter(p => p.userId === user.userId)
        }
      } else if (this.activeView === 'shared') {
        // 共享项目（暂时返回空，后续实现）
        filtered = []
      } else if (this.activeView === 'archived') {
        // 已归档项目（暂时返回空，后续实现）
        filtered = []
      } else if (this.activeView === 'deleted') {
        // 已删除项目（暂时返回空，后续实现）
        filtered = []
      }
      
      // 根据搜索关键词过滤
      if (this.searchQuery.trim()) {
        const query = this.searchQuery.toLowerCase()
        filtered = filtered.filter(p => 
          (p.name || '').toLowerCase().includes(query)
        )
      }
      
      return filtered
    }
  },
  mounted() {
    this.loadProjects()
    this.currentUser = getUser()
    // 点击外部关闭菜单
    document.addEventListener('click', this.handleClickOutside)
  },
  beforeUnmount() {
    document.removeEventListener('click', this.handleClickOutside)
  },
  methods: {
    async loadProjects() {
      this.loading = true
      try {
        // 获取当前用户
        const user = getUser()
        if (!user || !user.userId) {
          // 用户未登录，跳转到登录页面
          this.$router.push('/login')
          return
        }
        
        // 加载当前用户的项目
        const response = await getProjectsByUser(user.userId)
        if (response.data) {
          this.projects = response.data
        }
      } catch (error) {
        console.error('加载项目失败:', error)
        this.projects = []
        
        // 根据错误类型显示不同的提示
        if (error.message.includes('未登录')) {
          alert('请先登录后再访问项目列表')
          this.$router.push('/login')
        } else if (error.message.includes('权限')) {
          alert('没有权限访问该项目列表')
        } else {
          alert('加载项目失败: ' + (error.message || '未知错误'))
        }
      } finally {
        this.loading = false
      }
    },
    switchView(view) {
      this.activeView = view
      // 切换视图时重新加载项目
      this.loadProjects()
    },
    getViewTitle() {
      const titles = {
        all: '所有项目',
        mine: '您的项目',
        shared: '与您共享的',
        archived: '已归档项目',
        deleted: '已删除项目'
      }
      return titles[this.activeView] || '所有项目'
    },
    handleCreateProject() {
      // 跳转到新项目页面
      this.$router.push('/new-project')
    },
    openProject(id) {
      // 跳转到编辑器页面
      this.$router.push(`/editor/${id}`)
    },
    downloadProject(id) {
      // 下载项目
      console.log('下载项目:', id)
    },
    exportPDF(id) {
      // 导出PDF
      console.log('导出PDF:', id)
    },
    async deleteProject(id) {
      if (confirm('确定要删除这个项目吗？')) {
        try {
          await deleteProjectAPI(id)
          this.projects = this.projects.filter(p => p.id !== id)
        } catch (error) {
          console.error('删除项目失败:', error)
          alert('删除失败，请重试')
        }
      }
    },
    handleSelectAll() {
      if (this.selectAll) {
        this.selectedProjects = this.filteredProjects.map(p => p.id)
      } else {
        this.selectedProjects = []
      }
    },
    formatDate(dateString) {
      if (!dateString) return '未知'
      const date = new Date(dateString)
      const now = new Date()
      const diff = now - date
      const days = Math.floor(diff / (1000 * 60 * 60 * 24))
      
      if (days === 0) {
        return '今天'
      } else if (days === 1) {
        return '昨天'
      } else if (days < 7) {
        return `${days} 天前`
      } else if (days < 30) {
        const weeks = Math.floor(days / 7)
        return `${weeks} 周前`
      } else if (days < 365) {
        const months = Math.floor(days / 30)
        return `${months} 个月前`
      } else {
        const years = Math.floor(days / 365)
        return `${years} 年前`
      }
    },
    toggleUserMenu() {
      this.showUserMenu = !this.showUserMenu
      if (this.showUserMenu) {
        // 打开菜单时刷新用户信息
        this.currentUser = getUser()
      }
    },
    handleClickOutside(event) {
      const userMenu = this.$el.querySelector('.user-menu')
      const avatar = this.$el.querySelector('.user-avatar-container')
      if (this.showUserMenu && userMenu && avatar && !avatar.contains(event.target)) {
        this.showUserMenu = false
      }
    },
    getUserInitial() {
      const user = this.currentUser || getUser()
      if (user && user.username) {
        return user.username.charAt(0).toUpperCase()
      }
      return 'U'
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
.projects-container {
  display: flex;
  height: 100vh;
  background-color: #f5f5f5;
}

/* 左侧边栏 */
.sidebar {
  width: 250px;
  background-color: #fff;
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
  padding: 20px;
  overflow-y: auto;
}

.create-project-btn {
  width: 100%;
  padding: 12px;
  background-color: #4caf50;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  margin-bottom: 20px;
  transition: background-color 0.3s;
}

.create-project-btn:hover {
  background-color: #45a049;
}

.sidebar-nav {
  margin-bottom: 20px;
}

.nav-item {
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 4px;
  margin-bottom: 4px;
  color: #666;
  transition: background-color 0.2s;
}

.nav-item:hover {
  background-color: #f0f0f0;
}

.nav-item.active {
  background-color: #e8f5e9;
  color: #4caf50;
  font-weight: 500;
}

.tags-section {
  margin-top: auto;
  padding-top: 20px;
  border-top: 1px solid #e0e0e0;
}

.tags-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 14px;
  color: #666;
}

.new-tag-btn {
  background: none;
  border: none;
  color: #4caf50;
  cursor: pointer;
  font-size: 14px;
  padding: 0;
}

.new-tag-btn:hover {
  text-decoration: underline;
}


/* 主内容区 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #fff;
}

.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 30px;
  border-bottom: 1px solid #e0e0e0;
}

.content-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 500;
  color: #333;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 15px;
}

.user-avatar-container {
  position: relative;
  cursor: pointer;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 500;
  transition: transform 0.2s;
}

.user-avatar:hover {
  transform: scale(1.05);
}

.user-menu {
  position: absolute;
  top: 50px;
  right: 0;
  width: 280px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  z-index: 1000;
  overflow: hidden;
}

.user-menu-header {
  display: flex;
  align-items: center;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.user-menu-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 500;
  margin-right: 12px;
}

.user-menu-info {
  flex: 1;
}

.user-menu-name {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 4px;
}

.user-menu-id {
  font-size: 12px;
  opacity: 0.9;
}

.user-menu-divider {
  height: 1px;
  background: #e0e0e0;
  margin: 0;
}

.user-menu-body {
  padding: 16px 20px;
}

.user-menu-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 14px;
}

.menu-item-label {
  color: #666;
}

.menu-item-value {
  color: #333;
  font-weight: 500;
}

.user-menu-footer {
  padding: 12px 20px;
  background: #f9f9f9;
}

.logout-btn {
  width: 100%;
  padding: 10px;
  background-color: #e74c3c;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.3s;
}

.logout-btn:hover {
  background-color: #c0392b;
}

.search-bar {
  padding: 15px 30px;
  border-bottom: 1px solid #e0e0e0;
}

.search-input {
  width: 100%;
  max-width: 500px;
  padding: 10px 15px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.search-input:focus {
  outline: none;
  border-color: #4caf50;
}

.projects-table-container {
  flex: 1;
  overflow-y: auto;
  padding: 0 30px;
}

.projects-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 20px;
}

.projects-table thead {
  background-color: #f9f9f9;
  position: sticky;
  top: 0;
  z-index: 10;
}

.projects-table th {
  padding: 12px;
  text-align: left;
  font-weight: 500;
  color: #666;
  font-size: 14px;
  border-bottom: 2px solid #e0e0e0;
}

.projects-table td {
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 15px;
  color: #333;
}

.projects-table tbody tr:hover {
  background-color: #f9f9f9;
}

.project-title span {
  font-size: 15px;
  color: #000;
  cursor: pointer;
  text-decoration: none;
  transition: color 0.2s;
}

.project-title span:hover {
  color: #4caf50;
}

.actions-cell {
  display: flex;
  gap: 8px;
}

.action-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 16px;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.action-btn:hover {
  background-color: #f0f0f0;
}

.delete-btn:hover {
  background-color: #ffebee;
}

.loading-cell,
.empty-cell {
  text-align: center;
  padding: 40px;
  color: #999;
}

.projects-footer {
  padding: 15px 30px;
  border-top: 1px solid #e0e0e0;
  font-size: 14px;
  color: #666;
  background-color: #f9f9f9;
}
</style>

