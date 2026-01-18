<template>
  <div class="editor-container">
    <!-- 顶部导航栏 -->
    <header class="editor-header">
      <div class="header-left">
        <button class="back-btn" @click="goBack">&larr; 返回项目列表</button>
        <h1 class="project-title">{{ projectName || '未命名项目' }}</h1>
      </div>
      <div class="header-right">
        <button class="save-btn" @click="saveProject">保存</button>
        <button class="compile-btn" @click="compileLatex">编译</button>
        <button class="download-btn" @click="downloadPdf">下载PDF</button>
      </div>
    </header>
    
    <!-- 主编辑区域 -->
    <main class="editor-main">
      <!-- 左侧LaTeX代码编辑区 -->
      <section class="editor-left">
        <div class="editor-section-header">
          <h2>LaTeX代码</h2>
        </div>
        <div class="code-editor-container">
          <textarea 
            v-model="latexCode" 
            class="code-editor" 
            placeholder="在此输入LaTeX代码..."
            rows="20"
          ></textarea>
        </div>
      </section>
      
      <!-- 右侧PDF预览区 -->
      <section class="editor-right">
        <div class="editor-section-header">
          <h2>PDF预览</h2>
          <span class="preview-status">{{ previewStatus }}</span>
        </div>
        <div class="pdf-preview-container">
          <div v-if="loading" class="pdf-loading">
            <div class="loading-spinner"></div>
            <p>正在加载PDF...</p>
          </div>
          <div v-else-if="pdfUrl" class="pdf-viewer">
            <iframe :src="pdfUrl" width="100%" height="100%"></iframe>
          </div>
          <div v-else class="pdf-placeholder">
            <p>编译LaTeX代码后将在此显示PDF预览</p>
            <button class="compile-now-btn" @click="compileLatex">立即编译</button>
          </div>
        </div>
      </section>
    </main>
    
    <!-- 底部状态栏 -->
    <footer class="editor-footer">
      <div class="status-left">
        <span class="cursor-position">行: {{ cursorLine }}, 列: {{ cursorColumn }}</span>
        <span class="word-count">字数: {{ wordCount }}</span>
      </div>
      <div class="status-right">
        <span class="last-saved">最后保存: {{ lastSavedTime }}</span>
      </div>
    </footer>
  </div>
</template>

<script>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProjectById, updateProject, compileProject } from '../api/project'

export default {
  name: 'Editor',
  setup() {
    const route = useRoute()
    const router = useRouter()
    
    // 项目信息
    const projectId = ref(route.params.id)
    const projectName = ref('')
    
    // 编辑器内容
    const latexCode = ref('\\documentclass{article}\\begin{document}\\section{Hello World}\\This is a LaTeX document.\\end{document}')
    const pdfUrl = ref('')
    
    // 状态信息
    const loading = ref(false)
    const previewStatus = ref('未编译')
    const cursorLine = ref(1)
    const cursorColumn = ref(1)
    const wordCount = ref(0)
    const lastSavedTime = ref('从未')
    
    // 计算属性
    const updateWordCount = () => {
      wordCount.value = latexCode.value ? latexCode.value.length : 0
    }
    
    // 方法
    const goBack = () => {
      router.push('/projects')
    }
    
    const loadProject = async () => {
      loading.value = true
      try {
        const response = await getProjectById(projectId.value)
        if (response.data) {
          projectName.value = response.data.name
          latexCode.value = response.data.content || ''
          updateWordCount()
        }
      } catch (error) {
        console.error('加载项目失败:', error)
        alert('加载项目失败: ' + (error.message || '未知错误'))
      } finally {
        loading.value = false
      }
    }
    
    const saveProject = async () => {
      try {
        // 调用更新项目的API
        await updateProject(projectId.value, { content: latexCode.value })
        lastSavedTime.value = new Date().toLocaleString()
        alert('项目保存成功！')
      } catch (error) {
        console.error('保存项目失败:', error)
        alert('保存项目失败: ' + (error.message || '未知错误'))
      }
    }
    
    const compileLatex = async () => {
      loading.value = true
      previewStatus.value = '编译中...'
      try {
        // 先保存项目内容
        await updateProject(projectId.value, { content: latexCode.value })
        
        // 调用编译API
        const response = await compileProject(projectId.value, 'pdflatex')
        
        if (response && response.data) {
          const compileResult = response.data
          
          if ((compileResult.status === 'SUCCESS' || compileResult.status === 'WARNING') && compileResult.pdfPath) {
            // 编译成功（或带警告），设置PDF URL
            // pdfPath 已经是 /api/pdf/xxx.pdf 格式，直接使用
            pdfUrl.value = compileResult.pdfPath
            previewStatus.value = compileResult.status === 'WARNING' ? '编译完成（有警告）' : '编译成功'
            
            // 显示编译信息
            const compileTime = compileResult.compileTimeMs ? (compileResult.compileTimeMs / 1000).toFixed(2) : '0'
            console.log(`编译完成，耗时: ${compileTime}秒`)
            
            // 如果有警告，显示警告信息
            if (compileResult.status === 'WARNING' && compileResult.errorMessage) {
              console.warn('编译警告:', compileResult.errorMessage)
              // 可以选择是否显示警告弹窗，这里只记录到控制台
              // alert('编译警告: ' + compileResult.errorMessage)
            }
          } else {
            // 编译失败
            previewStatus.value = '编译失败'
            const errorMsg = compileResult.errorMessage || compileResult.logContent || '编译失败，请查看日志'
            alert('编译失败: ' + errorMsg)
            console.error('编译日志:', compileResult.logContent)
            // 清空PDF URL
            pdfUrl.value = ''
          }
        } else {
          previewStatus.value = '编译失败'
          alert('编译失败: 未收到有效响应')
        }
      } catch (error) {
        console.error('编译失败:', error)
        previewStatus.value = '编译失败'
        const errorMsg = error.response?.data?.message || error.message || '未知错误'
        alert('编译失败: ' + errorMsg)
        pdfUrl.value = ''
      } finally {
        loading.value = false
      }
    }
    
    const downloadPdf = () => {
      if (!pdfUrl.value) {
        alert('请先编译生成PDF！')
        return
      }
      // 创建下载链接
      const link = document.createElement('a')
      link.href = pdfUrl.value
      link.download = `${projectName.value || 'latex-document'}.pdf`
      link.target = '_blank' // 在新窗口打开，确保能访问跨域资源
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    }
    
    const handleCursorPosition = (e) => {
      const textarea = e.target
      const start = textarea.selectionStart
      const text = latexCode.value
      
      // 计算行号和列号
      const lines = text.substring(0, start).split('\n')
      cursorLine.value = lines.length
      cursorColumn.value = lines[lines.length - 1].length + 1
    }
    
    // 生命周期钩子
    onMounted(() => {
      loadProject()
    })
    
    return {
      projectName,
      latexCode,
      pdfUrl,
      loading,
      previewStatus,
      cursorLine,
      cursorColumn,
      wordCount,
      lastSavedTime,
      goBack,
      saveProject,
      compileLatex,
      downloadPdf,
      handleCursorPosition,
      updateWordCount
    }
  }
}
</script>

<style scoped>
.editor-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  font-family: Arial, sans-serif;
}

/* 顶部导航栏 */
.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background-color: #2c3e50;
  color: white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.back-btn {
  background: none;
  border: none;
  color: white;
  font-size: 18px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.back-btn:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.project-title {
  margin: 0;
  font-size: 22px;
  font-weight: 500;
  color: #000;
  transition: color 0.2s;
}

.header-right {
  display: flex;
  gap: 10px;
}

.save-btn, .compile-btn, .download-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: background-color 0.2s;
}

.save-btn {
  background-color: #4caf50;
  color: white;
}

.save-btn:hover {
  background-color: #45a049;
}

.compile-btn {
  background-color: #2196f3;
  color: white;
}

.compile-btn:hover {
  background-color: #1976d2;
}

.download-btn {
  background-color: #ff9800;
  color: white;
}

.download-btn:hover {
  background-color: #f57c00;
}

/* 主编辑区域 */
.editor-main {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 左侧LaTeX编辑区 */
.editor-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e0e0e0;
  background-color: #f5f5f5;
}

/* 右侧PDF预览区 */
.editor-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: white;
}

.editor-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background-color: white;
  border-bottom: 1px solid #e0e0e0;
}

.editor-section-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.preview-status {
  font-size: 14px;
  color: #666;
  padding: 4px 8px;
  border-radius: 12px;
  background-color: #f0f0f0;
}

.code-editor-container {
  flex: 1;
  padding: 20px;
  overflow: hidden;
}

.code-editor {
  width: 100%;
  height: 100%;
  padding: 15px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  background-color: white;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.1);
}

.code-editor:focus {
  outline: none;
  border-color: #2196f3;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.1), 0 0 0 2px rgba(33, 150, 243, 0.2);
}

.pdf-preview-container {
  flex: 1;
  padding: 20px;
  overflow: auto;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #fafafa;
}

.pdf-viewer {
  width: 100%;
  height: 100%;
  background-color: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.pdf-viewer iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.pdf-loading {
  text-align: center;
  color: #666;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #2196f3;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 15px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.pdf-placeholder {
  text-align: center;
  color: #999;
  padding: 40px;
}

.compile-now-btn {
  margin-top: 15px;
  padding: 8px 16px;
  background-color: #2196f3;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.compile-now-btn:hover {
  background-color: #1976d2;
}

/* 底部状态栏 */
.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 20px;
  background-color: #f5f5f5;
  border-top: 1px solid #e0e0e0;
  font-size: 12px;
  color: #666;
}

.status-left, .status-right {
  display: flex;
  gap: 15px;
}
</style>