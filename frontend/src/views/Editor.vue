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
        <select v-model="selectedCompiler" class="compiler-select" title="选择LaTeX编译引擎">
          <option
            v-for="item in compilerOptions"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </option>
        </select>
        <button class="download-btn" @click="downloadPdf">下载PDF</button>
        <button class="download-word-btn" @click="downloadWord">下载Word</button>
      </div>
    </header>
    
    <!-- 主编辑区域 -->
    <main class="editor-main">
      <!-- 左侧组件库 -->
      <section class="editor-sidebar">
        <div class="sidebar-header">
          <h2>组件库</h2>
        </div>
        <div class="component-library">
          <div class="component-list">
            <div 
              v-for="component in components" 
              :key="component.type"
              class="component-item"
              draggable="true"
              @dragstart="(e) => handleDragStart(e, component)"
            >
              <span class="component-icon">{{ component.icon }}</span>
              <span class="component-name">{{ component.name }}</span>
            </div>
          </div>
        </div>
      </section>
      
      <!-- 中间LaTeX代码编辑区 -->
      <section class="editor-left">
        <div class="editor-section-header">
          <h2>LaTeX代码</h2>
        </div>
        <div 
          class="code-editor-container" 
          @drop="handleDrop"
          @dragover.prevent
        >
          <div ref="codeEditorRef" class="code-editor"></div>
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
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { componentConfig } from '../utils/componentTypes'
import { getProjectById, updateProject, compileProject, exportProjectWord } from '../api/project'
import { EditorState } from '@codemirror/state'
import { EditorView, keymap, lineNumbers, highlightActiveLineGutter } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { syntaxHighlighting, defaultHighlightStyle, StreamLanguage } from '@codemirror/language'
import { stex } from '@codemirror/legacy-modes/mode/stex'
import { foldGutter, indentOnInput, bracketMatching } from '@codemirror/language'
import { closeBrackets, closeBracketsKeymap } from '@codemirror/autocomplete'
import { lintGutter, linter } from '@codemirror/lint'

const latexLinter = linter((view) => {
  const doc = view.state.doc.toString()
  const diagnostics = []
  const braceStack = []

  for (let i = 0; i < doc.length; i++) {
    const ch = doc[i]
    if (ch === '{') {
      braceStack.push(i)
    } else if (ch === '}') {
      if (braceStack.length === 0) {
        diagnostics.push({
          from: i,
          to: i + 1,
          severity: 'error',
          message: '检测到多余的右花括号 `}`'
        })
      } else {
        braceStack.pop()
      }
    }
  }

  for (const pos of braceStack) {
    diagnostics.push({
      from: pos,
      to: pos + 1,
      severity: 'warning',
      message: '该左花括号 `{` 没有匹配的 `}`'
    })
  }

  const beginStack = []
  const envRegex = /\\(begin|end)\{([^}]+)\}/g
  let match = envRegex.exec(doc)
  while (match) {
    const tag = match[1]
    const env = match[2]
    const from = match.index
    const to = from + match[0].length

    if (tag === 'begin') {
      beginStack.push({ env, from, to })
    } else if (beginStack.length === 0 || beginStack[beginStack.length - 1].env !== env) {
      diagnostics.push({
        from,
        to,
        severity: 'error',
        message: `环境结束标签不匹配: \\end{${env}}`
      })
    } else {
      beginStack.pop()
    }
    match = envRegex.exec(doc)
  }

  for (const item of beginStack) {
    diagnostics.push({
      from: item.from,
      to: item.to,
      severity: 'warning',
      message: `环境未闭合: \\begin{${item.env}}`
    })
  }

  return diagnostics
})

export default {
  name: 'Editor',
  setup() {
    const route = useRoute()
    const router = useRouter()

    const projectId = ref(route.params.id)
    const projectName = ref('')

    const latexCode = ref('\\documentclass{article}\\begin{document}\\section{Hello World}\\This is a LaTeX document.\\end{document}')
    const pdfUrl = ref('')
    const codeEditorRef = ref(null)
    const codeEditorView = ref(null)
    const autoCompileTimer = ref(null)
    const isCompiling = ref(false)
    const pendingCompile = ref(false)
    const selectedCompiler = ref('pdflatex')
    const compilerOptions = ref([
      { value: 'pdflatex', label: 'pdfLaTeX' },
      { value: 'xelatex', label: 'xeLaTeX' },
      { value: 'lualatex', label: 'luaLaTeX' }
    ])

    const loading = ref(false)
    const previewStatus = ref('未编译')
    const cursorLine = ref(1)
    const cursorColumn = ref(1)
    const wordCount = ref(0)
    const lastSavedTime = ref('从未')

    const components = ref(Object.entries(componentConfig).map(([type, config]) => ({
      type,
      name: config.name,
      icon: config.icon,
      latexTemplate: config.latexTemplate
    })))

    const updateEditorStatus = (state) => {
      const text = state.doc.toString()
      latexCode.value = text
      wordCount.value = text.length
      const cursorPos = state.selection.main.head
      const line = state.doc.lineAt(cursorPos)
      cursorLine.value = line.number
      cursorColumn.value = cursorPos - line.from + 1
    }

    const updateWordCount = () => {
      wordCount.value = latexCode.value ? latexCode.value.length : 0
    }

    const clearAutoCompileTimer = () => {
      if (autoCompileTimer.value) {
        clearTimeout(autoCompileTimer.value)
        autoCompileTimer.value = null
      }
    }

    const scheduleAutoCompile = () => {
      clearAutoCompileTimer()
      autoCompileTimer.value = setTimeout(() => {
        compileLatex({ silent: true })
      }, 2000)
    }

    const createCodeEditor = () => {
      if (!codeEditorRef.value) {
        return
      }

      const state = EditorState.create({
        doc: latexCode.value || '',
        extensions: [
          lineNumbers(),
          highlightActiveLineGutter(),
          foldGutter(),
          history(),
          keymap.of([...defaultKeymap, ...historyKeymap, ...closeBracketsKeymap]),
          indentOnInput(),
          bracketMatching(),
          closeBrackets(),
          StreamLanguage.define(stex),
          syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
          latexLinter,
          lintGutter(),
          EditorView.lineWrapping,
          EditorView.updateListener.of((update) => {
            if (update.docChanged || update.selectionSet) {
              updateEditorStatus(update.state)
            }
            if (update.docChanged) {
              scheduleAutoCompile()
            }
          })
        ]
      })

      codeEditorView.value = new EditorView({
        state,
        parent: codeEditorRef.value
      })

      updateEditorStatus(codeEditorView.value.state)
    }

    const setEditorContent = (content) => {
      const editorView = codeEditorView.value
      if (!editorView) {
        latexCode.value = content || ''
        updateWordCount()
        return
      }

      const current = editorView.state.doc.toString()
      const next = content || ''
      if (current === next) {
        return
      }

      editorView.dispatch({
        changes: { from: 0, to: current.length, insert: next },
        selection: { anchor: Math.min(editorView.state.selection.main.head, next.length) }
      })
    }

    const handleDragStart = (event, component) => {
      event.dataTransfer.setData('application/json', JSON.stringify(component))
    }

    const insertTemplateAtPosition = (text, insertPos, template) => {
      const before = text.slice(0, insertPos)
      const after = text.slice(insertPos)
      return {
        nextText: `${before}${template}${after}`,
        caretPos: before.length + template.length
      }
    }

    const handleDrop = (event) => {
      event.preventDefault()
      const componentData = JSON.parse(event.dataTransfer.getData('application/json'))
      const editorView = codeEditorView.value

      if (!editorView || !componentData || !componentData.latexTemplate) {
        return
      }

      const text = editorView.state.doc.toString()
      const dropPos = editorView.posAtCoords({ x: event.clientX, y: event.clientY })
      const insertPos = dropPos == null ? editorView.state.selection.main.head : dropPos
      const { nextText, caretPos } = insertTemplateAtPosition(text, insertPos, componentData.latexTemplate)

      editorView.dispatch({
        changes: { from: 0, to: text.length, insert: nextText },
        selection: { anchor: caretPos }
      })
      editorView.focus()
    }

    const goBack = () => {
      router.push('/projects')
    }

    const loadProject = async () => {
      loading.value = true
      try {
        const response = await getProjectById(projectId.value)
        if (response.data) {
          projectName.value = response.data.name
          setEditorContent(response.data.content || '')
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
        await updateProject(projectId.value, { content: latexCode.value })
        lastSavedTime.value = new Date().toLocaleString()
        alert('项目保存成功！')
      } catch (error) {
        console.error('保存项目失败:', error)
        alert('保存项目失败: ' + (error.message || '未知错误'))
      }
    }

    const compileLatex = async ({ silent = false } = {}) => {
      if (isCompiling.value) {
        pendingCompile.value = true
        return
      }

      isCompiling.value = true
      loading.value = true
      previewStatus.value = '编译中...'
      try {
        await updateProject(projectId.value, { content: latexCode.value })
        const response = await compileProject(projectId.value, selectedCompiler.value)

        if (response && response.data) {
          const compileResult = response.data
          if ((compileResult.status === 'SUCCESS' || compileResult.status === 'WARNING') && compileResult.pdfPath) {
            pdfUrl.value = compileResult.pdfPath
            previewStatus.value = compileResult.status === 'WARNING' ? '编译完成（有警告）' : '编译成功'
            const compileTime = compileResult.compileTimeMs ? (compileResult.compileTimeMs / 1000).toFixed(2) : '0'
            console.log(`编译完成，耗时: ${compileTime}秒`)

            if (compileResult.status === 'WARNING' && compileResult.errorMessage) {
              console.warn('编译警告:', compileResult.errorMessage)
            }
          } else {
            previewStatus.value = '编译失败'
            const errorMsg = compileResult.errorMessage || compileResult.logContent || '编译失败，请查看日志'
            if (!silent) {
              alert('编译失败: ' + errorMsg)
            }
            console.error('编译日志:', compileResult.logContent)
            pdfUrl.value = ''
          }
        } else {
          previewStatus.value = '编译失败'
          if (!silent) {
            alert('编译失败: 未收到有效响应')
          }
        }
      } catch (error) {
        console.error('编译失败:', error)
        previewStatus.value = '编译失败'
        const errorMsg = error.response?.data?.message || error.message || '未知错误'
        if (!silent) {
          alert('编译失败: ' + errorMsg)
        }
        pdfUrl.value = ''
      } finally {
        isCompiling.value = false
        loading.value = false

        if (pendingCompile.value) {
          pendingCompile.value = false
          scheduleAutoCompile()
        }
      }
    }

    const downloadPdf = () => {
      if (!pdfUrl.value) {
        alert('请先编译生成PDF！')
        return
      }
      const link = document.createElement('a')
      link.href = pdfUrl.value
      link.download = `${projectName.value || 'latex-document'}.pdf`
      link.target = '_blank'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    }

    const downloadWord = async () => {
      try {
        await updateProject(projectId.value, { content: latexCode.value })
        const response = await exportProjectWord(projectId.value)
        const blob = new Blob(
          [response.data],
          { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }
        )
        const fileName = `${projectName.value || 'latex-document'}.docx`
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = fileName
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
      } catch (error) {
        console.error('导出Word失败:', error)
        let errorMsg = '导出Word失败'
        try {
          const data = error?.response?.data
          if (data instanceof Blob) {
            const text = await data.text()
            if (text) {
              errorMsg = text
            }
          } else if (typeof data === 'string' && data.trim()) {
            errorMsg = data.trim()
          } else if (error?.message) {
            errorMsg = error.message
          }
        } catch (parseError) {
          if (error?.message) {
            errorMsg = error.message
          }
        }
        alert(errorMsg)
      }
    }

    const handleCursorPosition = () => {
      if (codeEditorView.value) {
        updateEditorStatus(codeEditorView.value.state)
      }
    }

    onMounted(() => {
      createCodeEditor()
      loadProject()
    })

    onBeforeUnmount(() => {
      clearAutoCompileTimer()
      if (codeEditorView.value) {
        codeEditorView.value.destroy()
        codeEditorView.value = null
      }
    })

    return {
      projectName,
      selectedCompiler,
      compilerOptions,
      latexCode,
      pdfUrl,
      codeEditorRef,
      loading,
      previewStatus,
      cursorLine,
      cursorColumn,
      wordCount,
      lastSavedTime,
      components,
      goBack,
      saveProject,
      compileLatex,
      downloadPdf,
      downloadWord,
      handleCursorPosition,
      updateWordCount,
      handleDragStart,
      handleDrop
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
  align-items: center;
  gap: 10px;
}

.compiler-select {
  height: 35px;
  padding: 0 12px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 4px;
  background-color: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s, border-color 0.2s;
}

.compiler-select:hover {
  background-color: rgba(255, 255, 255, 0.2);
}

.compiler-select:focus {
  outline: none;
  border-color: rgba(255, 255, 255, 0.55);
}

.compiler-select option {
  color: #333;
  background-color: #fff;
}

.save-btn, .compile-btn, .download-btn, .download-word-btn {
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

.download-word-btn {
  background-color: #8e44ad;
  color: white;
}

.download-word-btn:hover {
  background-color: #7d3c98;
}

/* 主编辑区域 */
.editor-main {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 左侧组件库 */
.editor-sidebar {
  width: 240px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e0e0e0;
  background-color: #f8f9fa;
  overflow-y: auto;
}

.sidebar-header {
  padding: 10px 20px;
  background-color: white;
  border-bottom: 1px solid #e0e0e0;
}

.sidebar-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.component-library {
  flex: 1;
  padding: 10px;
}

.component-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.component-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background-color: white;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  cursor: grab;
  transition: all 0.2s;
}

.component-item:hover {
  background-color: #e3f2fd;
  border-color: #2196f3;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.component-item:active {
  cursor: grabbing;
}

.component-icon {
  font-size: 18px;
  width: 24px;
  text-align: center;
}

.component-name {
  font-size: 14px;
  color: #333;
  flex: 1;
}

/* 中间LaTeX编辑区 */
.editor-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e0e0e0;
  background-color: #f5f5f5;
}

.code-editor-container {
  flex: 1;
  padding: 20px;
  overflow: hidden;
  min-height: 0;
}

.code-editor-container.drag-over {
  background-color: rgba(33, 150, 243, 0.1);
  border: 2px dashed #2196f3;
  border-radius: 4px;
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
  border: 1px solid #ddd;
  border-radius: 4px;
  background-color: white;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.1), 0 0 0 2px rgba(33, 150, 243, 0.05);
  overflow: hidden;
}

:deep(.cm-editor) {
  height: 100%;
  font-family: 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.5;
}

:deep(.cm-scroller) {
  overflow: auto;
}

:deep(.cm-content),
:deep(.cm-gutter) {
  padding-top: 10px;
  padding-bottom: 10px;
}

:deep(.cm-focused) {
  outline: none;
}

:deep(.cm-focused .cm-content),
:deep(.cm-focused .cm-gutter) {
  background-color: #fff;
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