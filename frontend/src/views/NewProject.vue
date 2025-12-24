<template>
  <div class="new-project-container">
    <div class="new-project-box">
      <div class="header">
        <h1>创建新项目</h1>
        <button class="close-btn" @click="goBack">×</button>
      </div>
      
      <div class="options-container">
        <!-- 空白项目 -->
        <div 
          class="option-card" 
          :class="{ active: selectedOption === 'blank' }"
          @click="selectOption('blank')"
        >
          <div class="option-icon">📄</div>
          <h3>空白项目</h3>
          <p>创建一个全新的空白LaTeX文档</p>
        </div>
        
        <!-- 使用模板 -->
        <div 
          class="option-card" 
          :class="{ active: selectedOption === 'template' }"
          @click="selectOption('template')"
        >
          <div class="option-icon">📋</div>
          <h3>使用模板</h3>
          <p>从模板库中选择一个模板开始</p>
        </div>
        
        <!-- 导入PDF -->
        <div 
          class="option-card" 
          :class="{ active: selectedOption === 'import' }"
          @click="selectOption('import')"
        >
          <div class="option-icon">📥</div>
          <h3>导入PDF</h3>
          <p>上传PDF文件并转换为LaTeX文档</p>
        </div>
      </div>
      
      <!-- 模板选择区域 -->
      <div v-if="selectedOption === 'template'" class="template-section">
        <div class="section-header">
          <h2>选择模板</h2>
          <div class="category-filter">
            <button 
              v-for="category in categories" 
              :key="category.value"
              class="category-btn"
              :class="{ active: selectedCategory === category.value }"
              @click="selectCategory(category.value)"
            >
              {{ category.label }}
            </button>
          </div>
        </div>
        
        <div v-if="loadingTemplates" class="loading">
          加载模板中...
        </div>
        
        <div v-else-if="filteredTemplates.length === 0" class="empty-templates">
          暂无模板
        </div>
        
        <div v-else class="templates-grid">
          <div 
            v-for="template in filteredTemplates" 
            :key="template.id"
            class="template-card"
            :class="{ selected: selectedTemplate?.id === template.id }"
            @click="selectTemplate(template)"
          >
            <div class="template-preview">
              <div v-if="template.previewImage" class="preview-image">
                <img :src="template.previewImage" :alt="template.name" />
              </div>
              <div v-else class="preview-placeholder">
                📄
              </div>
            </div>
            <div class="template-info">
              <h4>{{ template.name }}</h4>
              <p>{{ template.description || '无描述' }}</p>
              <div class="template-meta">
                <span class="category-tag">{{ getCategoryLabel(template.category) }}</span>
                <span class="usage-count">使用 {{ template.usageCount || 0 }} 次</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- PDF导入区域 -->
      <div v-if="selectedOption === 'import'" class="import-section">
        <div class="upload-area" 
             :class="{ 'drag-over': isDragOver }"
             @drop.prevent="handleDrop"
             @dragover.prevent="isDragOver = true"
             @dragleave.prevent="isDragOver = false"
        >
          <input 
            ref="fileInput"
            type="file" 
            accept=".pdf"
            @change="handleFileSelect"
            style="display: none"
          />
          <div class="upload-content">
            <div class="upload-icon">📥</div>
            <p class="upload-text">点击或拖拽PDF文件到这里</p>
            <p class="upload-hint">支持PDF格式文件</p>
            <button class="upload-btn" @click="$refs.fileInput.click()">
              选择文件
            </button>
          </div>
        </div>
        
        <div v-if="selectedFile" class="file-info">
          <div class="file-name">
            <span>📄</span>
            <span>{{ selectedFile.name }}</span>
            <button class="remove-file" @click="removeFile">×</button>
          </div>
        </div>
      </div>
      
      <!-- 项目名称输入 -->
      <div v-if="selectedOption !== null" class="project-name-section">
        <label for="project-name">项目名称</label>
        <input 
          id="project-name"
          v-model="projectName"
          type="text"
          placeholder="请输入项目名称"
          class="project-name-input"
        />
      </div>
      
      <!-- 操作按钮 -->
      <div class="actions">
        <button class="cancel-btn" @click="goBack">取消</button>
        <button 
          class="create-btn" 
          :disabled="!canCreate"
          @click="handleCreate"
        >
          {{ creating ? '创建中...' : '创建' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { createProject } from '../api/project'
import { getAllTemplates, getTemplatesByCategory } from '../api/template'
import { getUser } from '../utils/auth'

export default {
  name: 'NewProject',
  data() {
    return {
      selectedOption: null, // 'blank', 'template', 'import'
      selectedTemplate: null,
      selectedCategory: 'all',
      templates: [],
      loadingTemplates: false,
      projectName: '',
      selectedFile: null,
      isDragOver: false,
      creating: false,
      categories: [
        { value: 'all', label: '全部' },
        { value: 'GENERAL', label: '通用' },
        { value: 'CONFERENCE', label: '会议论文' },
        { value: 'JOURNAL', label: '期刊论文' },
        { value: 'THESIS', label: '学位论文' },
        { value: 'BOOK', label: '书籍' }
      ]
    }
  },
  computed: {
    filteredTemplates() {
      if (this.selectedCategory === 'all') {
        return this.templates
      }
      return this.templates.filter(t => t.category === this.selectedCategory)
    },
    canCreate() {
      if (!this.selectedOption) return false
      if (!this.projectName.trim()) return false
      
      if (this.selectedOption === 'template' && !this.selectedTemplate) {
        return false
      }
      
      if (this.selectedOption === 'import' && !this.selectedFile) {
        return false
      }
      
      return true
    }
  },
  mounted() {
    if (this.selectedOption === 'template') {
      this.loadTemplates()
    }
  },
  watch: {
    selectedOption(newVal) {
      if (newVal === 'template') {
        this.loadTemplates()
      }
    }
  },
  methods: {
    selectOption(option) {
      this.selectedOption = option
      this.selectedTemplate = null
      this.selectedFile = null
      this.projectName = ''
    },
    selectCategory(category) {
      this.selectedCategory = category
    },
    async loadTemplates() {
      this.loadingTemplates = true
      try {
        const response = await getAllTemplates()
        if (response.data) {
          this.templates = response.data
        }
      } catch (error) {
        console.error('加载模板失败:', error)
        this.templates = []
      } finally {
        this.loadingTemplates = false
      }
    },
    selectTemplate(template) {
      this.selectedTemplate = template
      if (!this.projectName) {
        this.projectName = template.name
      }
    },
    handleFileSelect(event) {
      const file = event.target.files[0]
      if (file) {
        if (file.type !== 'application/pdf') {
          alert('请选择PDF文件')
          return
        }
        this.selectedFile = file
        if (!this.projectName) {
          this.projectName = file.name.replace('.pdf', '')
        }
      }
    },
    handleDrop(event) {
      this.isDragOver = false
      const file = event.dataTransfer.files[0]
      if (file) {
        if (file.type !== 'application/pdf') {
          alert('请选择PDF文件')
          return
        }
        this.selectedFile = file
        if (!this.projectName) {
          this.projectName = file.name.replace('.pdf', '')
        }
      }
    },
    removeFile() {
      this.selectedFile = null
      if (this.$refs.fileInput) {
        this.$refs.fileInput.value = ''
      }
    },
    getCategoryLabel(category) {
      const cat = this.categories.find(c => c.value === category)
      return cat ? cat.label : category
    },
    async handleCreate() {
      if (!this.canCreate || this.creating) return
      
      this.creating = true
      const user = getUser()
      
      if (!user || !user.userId) {
        alert('请先登录')
        this.creating = false
        return
      }
      
      try {
        let projectId = null
        let projectContent = ''
        
        if (this.selectedOption === 'blank') {
          // 创建空白项目
          projectContent = '\\documentclass{article}\n\\begin{document}\n\n\\end{document}'
        } else if (this.selectedOption === 'template') {
          // 使用模板创建项目
          projectContent = this.selectedTemplate.content || '\\documentclass{article}\n\\begin{document}\n\n\\end{document}'
        } else if (this.selectedOption === 'import') {
          // 导入PDF文件
          // 注意：这里需要后端支持PDF导入，目前先提示
          alert('PDF导入功能正在开发中，请先使用空白项目或模板')
          this.creating = false
          return
        }
        
        // 创建项目（userId由后端从token中获取）
        const response = await createProject({
          name: this.projectName,
          content: projectContent
        })
        
        if (response.data && response.data.id) {
          projectId = response.data.id
          // 跳转到项目列表或编辑器页面
          this.$router.push('/projects')
        } else {
          alert('创建项目失败：未返回项目ID')
        }
      } catch (error) {
        console.error('创建项目失败:', error)
        alert('创建项目失败: ' + (error.message || '未知错误'))
      } finally {
        this.creating = false
      }
    },
    goBack() {
      this.$router.push('/projects')
    }
  }
}
</script>

<style scoped>
.new-project-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.new-project-box {
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  padding: 40px;
  width: 100%;
  max-width: 1000px;
  max-height: 90vh;
  overflow-y: auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e0e0e0;
}

.header h1 {
  margin: 0;
  font-size: 28px;
  color: #333;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  font-size: 32px;
  color: #999;
  cursor: pointer;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #f5f5f5;
  color: #333;
}

.options-container {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 30px;
}

.option-card {
  border: 2px solid #e0e0e0;
  border-radius: 12px;
  padding: 30px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  background: white;
}

.option-card:hover {
  border-color: #667eea;
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
}

.option-card.active {
  border-color: #667eea;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
}

.option-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.option-card h3 {
  margin: 0 0 8px 0;
  font-size: 18px;
  color: #333;
  font-weight: 600;
}

.option-card p {
  margin: 0;
  font-size: 14px;
  color: #666;
}

.template-section,
.import-section {
  margin-top: 30px;
  padding-top: 30px;
  border-top: 1px solid #e0e0e0;
}

.section-header {
  margin-bottom: 20px;
}

.section-header h2 {
  margin: 0 0 16px 0;
  font-size: 20px;
  color: #333;
  font-weight: 600;
}

.category-filter {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.category-btn {
  padding: 8px 16px;
  border: 1px solid #ddd;
  border-radius: 20px;
  background: white;
  color: #666;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.category-btn:hover {
  border-color: #667eea;
  color: #667eea;
}

.category-btn.active {
  background: #667eea;
  color: white;
  border-color: #667eea;
}

.loading,
.empty-templates {
  text-align: center;
  padding: 40px;
  color: #999;
}

.templates-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 20px;
}

.template-card {
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  background: white;
}

.template-card:hover {
  border-color: #667eea;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.template-card.selected {
  border-color: #667eea;
  background: rgba(102, 126, 234, 0.05);
}

.template-preview {
  width: 100%;
  height: 150px;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.preview-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-placeholder {
  font-size: 48px;
}

.template-info {
  padding: 16px;
}

.template-info h4 {
  margin: 0 0 8px 0;
  font-size: 16px;
  color: #333;
  font-weight: 600;
}

.template-info p {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #666;
  line-height: 1.4;
}

.template-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}

.category-tag {
  background: #e8f5e9;
  color: #4caf50;
  padding: 4px 8px;
  border-radius: 4px;
}

.usage-count {
  color: #999;
}

.upload-area {
  border: 2px dashed #ddd;
  border-radius: 12px;
  padding: 60px 20px;
  text-align: center;
  transition: all 0.3s;
  background: #fafafa;
}

.upload-area.drag-over {
  border-color: #667eea;
  background: rgba(102, 126, 234, 0.05);
}

.upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.upload-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.upload-text {
  font-size: 18px;
  color: #333;
  margin: 0 0 8px 0;
  font-weight: 500;
}

.upload-hint {
  font-size: 14px;
  color: #999;
  margin: 0 0 20px 0;
}

.upload-btn {
  padding: 10px 24px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
}

.upload-btn:hover {
  background: #5568d3;
}

.file-info {
  margin-top: 20px;
  padding: 16px;
  background: #f5f5f5;
  border-radius: 8px;
}

.file-name {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #333;
}

.remove-file {
  margin-left: auto;
  background: none;
  border: none;
  color: #e74c3c;
  cursor: pointer;
  font-size: 20px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background 0.2s;
}

.remove-file:hover {
  background: rgba(231, 76, 60, 0.1);
}

.project-name-section {
  margin-top: 30px;
  padding-top: 30px;
  border-top: 1px solid #e0e0e0;
}

.project-name-section label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.project-name-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  box-sizing: border-box;
}

.project-name-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 30px;
  padding-top: 30px;
  border-top: 1px solid #e0e0e0;
}

.cancel-btn,
.create-btn {
  padding: 12px 24px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.cancel-btn {
  background: #f5f5f5;
  color: #666;
}

.cancel-btn:hover {
  background: #e0e0e0;
}

.create-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.create-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.create-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>

