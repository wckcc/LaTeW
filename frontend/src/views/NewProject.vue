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
        
        <!-- 导入项目（zip） -->
        <div 
          class="option-card" 
          :class="{ active: selectedOption === 'importZip' }"
          @click="selectOption('importZip')"
        >
          <div class="option-icon">📦</div>
          <h3>导入项目</h3>
          <p>上传 LaTeX 项目 zip 包（含 .tex 及依赖文件）</p>
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
            <span class="file-size">({{ formatFileSize(selectedFile.size) }})</span>
            <button class="remove-file" @click="removeFile">×</button>
          </div>
          <div class="file-hint">
            <p>💡 提示：系统将使用AI自动识别PDF内容并转换为LaTeX文档</p>
          </div>
        </div>
      </div>
      
      <!-- zip 导入项目区域 -->
      <div v-if="selectedOption === 'importZip'" class="import-section">
        <div class="upload-area" 
             :class="{ 'drag-over': isZipDragOver }"
             @drop.prevent="handleZipDrop"
             @dragover.prevent="isZipDragOver = true"
             @dragleave.prevent="isZipDragOver = false"
        >
          <input 
            ref="zipFileInput"
            type="file" 
            accept=".zip,application/zip,application/x-zip-compressed"
            @change="handleZipFileSelect"
            style="display: none"
          />
          <div class="upload-content">
            <div class="upload-icon">📦</div>
            <p class="upload-text">点击或拖拽 zip 文件到这里</p>
            <p class="upload-hint">需为 .zip 格式，包内至少包含一个 .tex 文件（与系统「导入模板」解压方式一致）</p>
            <button class="upload-btn" @click="$refs.zipFileInput.click()">
              选择文件
            </button>
          </div>
        </div>
        
        <div v-if="selectedZipFile" class="file-info">
          <div class="file-name">
            <span>📦</span>
            <span>{{ selectedZipFile.name }}</span>
            <span class="file-size">({{ formatFileSize(selectedZipFile.size) }})</span>
            <button class="remove-file" @click="removeZipFile">×</button>
          </div>
          <div class="file-hint">
            <p>主入口优先使用包内的 main.tex，其次 document.tex，否则使用 zip 中遇到的第一个 .tex。</p>
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
          {{ creating ? (selectedOption === 'import' ? 'AI识别中...' : selectedOption === 'importZip' ? '导入中...' : '创建中...') : '创建' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { createProject, createProjectFromPdf, importProjectFromZip } from '../api/project'
import { getAllTemplates } from '../api/template'
import { getUser } from '../utils/auth'

export default {
  name: 'NewProject',
  data() {
    return {
      selectedOption: null, // 'blank', 'template', 'import', 'importZip'
      selectedTemplate: null,
      selectedCategory: 'all',
      templates: [],
      loadingTemplates: false,
      projectName: '',
      selectedFile: null,
      selectedZipFile: null,
      isDragOver: false,
      isZipDragOver: false,
      creating: false,
      categories: [{ value: 'all', label: '全部' }]
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
      
      if (this.selectedOption === 'importZip' && !this.selectedZipFile) {
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
      this.selectedZipFile = null
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
    isZipFile(file) {
      if (!file || !file.name) return false
      return /\.zip$/i.test(file.name)
    },
    handleZipFileSelect(event) {
      const file = event.target.files[0]
      if (file) {
        if (!this.isZipFile(file)) {
          alert('请上传 .zip 格式文件')
          return
        }
        this.selectedZipFile = file
        if (!this.projectName) {
          this.projectName = file.name.replace(/\.zip$/i, '')
        }
      }
    },
    handleZipDrop(event) {
      this.isZipDragOver = false
      const file = event.dataTransfer.files[0]
      if (file) {
        if (!this.isZipFile(file)) {
          alert('请上传 .zip 格式文件')
          return
        }
        this.selectedZipFile = file
        if (!this.projectName) {
          this.projectName = file.name.replace(/\.zip$/i, '')
        }
      }
    },
    removeZipFile() {
      this.selectedZipFile = null
      if (this.$refs.zipFileInput) {
        this.$refs.zipFileInput.value = ''
      }
    },
    getCategoryLabel(category) {
      const cat = this.categories.find(c => c.value === category)
      return cat ? cat.label : category
    },
    formatFileSize(bytes) {
      if (bytes === 0) return '0 Bytes'
      const k = 1024
      const sizes = ['Bytes', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
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
          // 使用模板创建项目（需带 %TEMPLATE_SOURCE_PATH= 才能复制 zip 中的图片/ cls 等依赖）
          const fallback = '\\documentclass{article}\n\\begin{document}\n\n\\end{document}'
          const templateContent = this.selectedTemplate.content || fallback
          const raw = (templateContent || '').replace(/\r\n/g, '\n')
          if (raw.trim().startsWith('%TEMPLATE_SOURCE_PATH=')) {
            projectContent = templateContent
          } else {
            const sourcePath = (this.selectedTemplate.sourcePath || '').trim()
            projectContent = sourcePath
              ? `%TEMPLATE_SOURCE_PATH=${sourcePath}\n${templateContent}`
              : templateContent
          }
        } else if (this.selectedOption === 'import') {
          // 导入PDF文件，使用AI识别并转换为LaTeX
          if (!this.selectedFile) {
            alert('请先选择PDF文件')
            this.creating = false
            return
          }
          
          try {
            // 调用API从PDF创建项目
            const response = await createProjectFromPdf(this.selectedFile, this.projectName)
            
            if (response.data && response.data.id) {
              projectId = response.data.id
              // 跳转到项目列表
              this.$router.push('/projects')
            } else {
              alert('创建项目失败：未返回项目ID')
            }
          } catch (error) {
            console.error('从PDF创建项目失败:', error)
            alert('从PDF创建项目失败: ' + (error.message || '未知错误'))
          } finally {
            this.creating = false
          }
          return
        } else if (this.selectedOption === 'importZip') {
          if (!this.selectedZipFile) {
            alert('请先选择 zip 文件')
            this.creating = false
            return
          }
          if (!this.isZipFile(this.selectedZipFile)) {
            alert('请上传 .zip 格式文件')
            this.creating = false
            return
          }
          try {
            const response = await importProjectFromZip(this.selectedZipFile, this.projectName)
            if (response.data && response.data.id) {
              this.$router.push('/projects')
            } else {
              alert('导入项目失败：未返回项目ID')
            }
          } catch (error) {
            console.error('导入项目失败:', error)
            alert('导入项目失败: ' + (error.message || '未知错误'))
          } finally {
            this.creating = false
          }
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
  padding: 32px 20px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.new-project-box {
  background: var(--fluent-surface);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  border-radius: var(--fluent-radius-lg);
  border: 1px solid var(--fluent-border);
  box-shadow: var(--fluent-shadow);
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
  border-bottom: 1px solid rgba(132, 160, 207, 0.16);
}

.header h1 {
  margin: 0;
  font-size: 28px;
  color: var(--fluent-text-1);
  font-weight: 700;
}

.close-btn {
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(132, 160, 207, 0.2);
  font-size: 32px;
  color: var(--fluent-text-2);
  cursor: pointer;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: rgba(240, 247, 255, 0.92);
  color: var(--fluent-accent);
}

.options-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.option-card {
  border: 1px solid rgba(132, 160, 207, 0.16);
  border-radius: 20px;
  padding: 30px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s ease;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: 0 10px 24px rgba(71, 104, 158, 0.08);
}

.option-card:hover {
  border-color: rgba(15, 108, 189, 0.2);
  transform: translateY(-2px);
  box-shadow: 0 18px 34px rgba(71, 104, 158, 0.14);
}

.option-card.active {
  border-color: rgba(15, 108, 189, 0.24);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.82), rgba(223, 236, 255, 0.74));
}

.option-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.option-card h3 {
  margin: 0 0 8px 0;
  font-size: 18px;
  color: var(--fluent-text-1);
  font-weight: 600;
}

.option-card p {
  margin: 0;
  font-size: 14px;
  color: var(--fluent-text-2);
}

.template-section,
.import-section {
  margin-top: 30px;
  padding-top: 30px;
  border-top: 1px solid rgba(132, 160, 207, 0.16);
}

.section-header {
  margin-bottom: 20px;
}

.section-header h2 {
  margin: 0 0 16px 0;
  font-size: 20px;
  color: var(--fluent-text-1);
  font-weight: 700;
}

.category-filter {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.category-btn {
  padding: 8px 16px;
  border: 1px solid rgba(132, 160, 207, 0.18);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  color: var(--fluent-text-2);
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.category-btn:hover {
  border-color: rgba(15, 108, 189, 0.22);
  color: var(--fluent-accent);
}

.category-btn.active {
  background: linear-gradient(135deg, #0f6cbd 0%, #4f8cff 100%);
  color: white;
  border-color: transparent;
}

.loading,
.empty-templates {
  text-align: center;
  padding: 40px;
  color: var(--fluent-text-3);
}

.templates-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 20px;
}

.template-card {
  border: 1px solid rgba(132, 160, 207, 0.16);
  border-radius: 18px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  background: rgba(255, 255, 255, 0.64);
}

.template-card:hover {
  border-color: rgba(15, 108, 189, 0.22);
  transform: translateY(-2px);
  box-shadow: 0 18px 34px rgba(71, 104, 158, 0.14);
}

.template-card.selected {
  border-color: rgba(15, 108, 189, 0.24);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.82), rgba(223, 236, 255, 0.72));
}

.template-preview {
  width: 100%;
  height: 150px;
  background: rgba(239, 245, 255, 0.9);
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
  color: var(--fluent-text-1);
  font-weight: 600;
}

.template-info p {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: var(--fluent-text-2);
  line-height: 1.4;
}

.template-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}

.category-tag {
  background: rgba(15, 108, 189, 0.1);
  color: var(--fluent-accent);
  padding: 4px 8px;
  border-radius: 999px;
}

.usage-count {
  color: var(--fluent-text-3);
}

.upload-area {
  border: 1px dashed rgba(125, 151, 194, 0.42);
  border-radius: 24px;
  padding: 60px 20px;
  text-align: center;
  transition: all 0.3s;
  background: rgba(255, 255, 255, 0.54);
}

.upload-area.drag-over {
  border-color: rgba(15, 108, 189, 0.32);
  background: rgba(223, 236, 255, 0.68);
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
  color: var(--fluent-text-1);
  margin: 0 0 8px 0;
  font-weight: 500;
}

.upload-hint {
  font-size: 14px;
  color: var(--fluent-text-3);
  margin: 0 0 20px 0;
}

.upload-btn {
  padding: 10px 24px;
  background: linear-gradient(135deg, #0f6cbd 0%, #4f8cff 100%);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.36);
  border-radius: 14px;
  cursor: pointer;
  font-size: 14px;
  box-shadow: 0 14px 24px rgba(15, 108, 189, 0.18);
}

.upload-btn:hover {
  background: linear-gradient(135deg, #115ea3 0%, #3d7ff2 100%);
}

.file-info {
  margin-top: 20px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 18px;
  border: 1px solid rgba(132, 160, 207, 0.16);
}

.file-name {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--fluent-text-1);
}

.file-size {
  color: var(--fluent-text-3);
  font-size: 12px;
}

.file-hint {
  margin-top: 12px;
  padding: 12px;
  background: rgba(223, 236, 255, 0.7);
  border-radius: 14px;
  border: 1px solid rgba(15, 108, 189, 0.12);
}

.file-hint p {
  margin: 0;
  font-size: 13px;
  color: var(--fluent-accent);
  line-height: 1.5;
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
  background: rgba(231, 76, 60, 0.12);
}

.project-name-section {
  margin-top: 30px;
  padding-top: 30px;
  border-top: 1px solid rgba(132, 160, 207, 0.16);
}

.project-name-section label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: var(--fluent-text-2);
  font-weight: 600;
}

.project-name-input {
  width: 100%;
  min-height: 46px;
  padding: 12px 16px;
  border: 1px solid rgba(125, 151, 194, 0.3);
  border-radius: 16px;
  font-size: 14px;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.72);
}

.project-name-input:focus {
  outline: none;
  border-color: rgba(15, 108, 189, 0.5);
  box-shadow: 0 0 0 3px rgba(15, 108, 189, 0.14);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 30px;
  padding-top: 30px;
  border-top: 1px solid rgba(132, 160, 207, 0.16);
}

.cancel-btn,
.create-btn {
  padding: 12px 24px;
  border-radius: 14px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.cancel-btn {
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid rgba(132, 160, 207, 0.18);
  color: var(--fluent-text-2);
}

.cancel-btn:hover {
  background: rgba(240, 247, 255, 0.92);
}

.create-btn {
  background: linear-gradient(135deg, #0f6cbd 0%, #4f8cff 100%);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.35);
  box-shadow: 0 16px 28px rgba(15, 108, 189, 0.22);
}

.create-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 20px 32px rgba(15, 108, 189, 0.28);
}

.create-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>

