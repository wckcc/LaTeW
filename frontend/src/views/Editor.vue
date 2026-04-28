<template>
  <div class="editor-container">
    <!-- 顶部导航栏 -->
    <header class="editor-header">
      <div class="header-left">
        <button class="back-btn" @click="goBack">&larr; 返回项目列表</button>
        <h1 class="project-title">{{ projectName || '未命名项目' }}</h1>
        <button class="import-latex-btn" @click="triggerImportLatex">导入LaTeX</button>
      </div>
      <div class="header-center">
        <button class="ai-assistant-btn" @click="openAiDialog">AI助手</button>
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
        <button class="download-latex-btn" @click="downloadLatex">下载LaTeX</button>
        <input
          ref="latexFileInputRef"
          type="file"
          accept=".tex,text/plain"
          class="latex-file-input"
          @change="handleLatexImport"
        />
        <input
          ref="imageFileInputRef"
          type="file"
          accept="image/*"
          class="latex-file-input"
          @change="handleImageInsert"
        />
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
              class="component-group"
            >
              <div
                class="component-item"
                :class="{ expandable: component.type === formulaType || component.type === imageType || component.type === symbolType, expanded: (component.type === formulaType && formulaExpanded) || (component.type === imageType && imageExpanded) || (component.type === symbolType && symbolExpanded) }"
                :draggable="component.draggable"
                @click="handleComponentClick(component)"
                @dragstart="(e) => handleDragStart(e, component)"
              >
                <span class="component-icon">{{ component.icon }}</span>
                <span class="component-name">{{ component.name }}</span>
                <span v-if="component.type === formulaType" class="expand-indicator">{{ formulaExpanded ? '▾' : '▸' }}</span>
                <span v-if="component.type === imageType" class="expand-indicator">{{ imageExpanded ? '▾' : '▸' }}</span>
                <span v-if="component.type === symbolType" class="expand-indicator">{{ symbolExpanded ? '▾' : '▸' }}</span>
              </div>
              <div v-if="component.type === imageType && imageExpanded" class="formula-options">
                <button class="formula-option" @click.stop="triggerLocalImageInsert">
                  <span class="formula-option-name">本地插入</span>
                  <code class="formula-option-preview">从本地选择图片文件并生成 \\includegraphics 代码</code>
                </button>
                <button class="formula-option" @click.stop="promptImageUrlInsert">
                  <span class="formula-option-name">URL插入</span>
                  <code class="formula-option-preview">输入图片 URL 或可访问路径并插入</code>
                </button>
              </div>
              <div v-if="component.type === formulaType && formulaExpanded" class="formula-options">
                <div v-for="group in formulaGroups" :key="group.key" class="formula-group">
                  <div class="formula-group-title">{{ group.title }}</div>
                  <button
                    v-for="option in group.items"
                    :key="option.key"
                    class="formula-option"
                    draggable="true"
                    @dragstart="(e) => handleFormulaDragStart(e, option)"
                  >
                    <span class="formula-option-name">{{ option.name }}</span>
                    <code class="formula-option-preview">{{ option.preview }}</code>
                  </button>
                </div>
              </div>
              <div v-if="component.type === symbolType && symbolExpanded" class="formula-options">
                <div v-for="group in symbolGroups" :key="group.key" class="formula-group">
                  <div class="formula-group-title">{{ group.title }}</div>
                  <button
                    v-for="option in group.items"
                    :key="option.key"
                    class="formula-option"
                    @click.stop="insertSymbolOption(option)"
                  >
                    <span class="formula-option-name">{{ option.name }}</span>
                    <code class="formula-option-preview">{{ option.preview }}</code>
                  </button>
                </div>
              </div>
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
          <div v-else-if="hasCompileErrorReport" class="compile-error-panel">
            <h3>编译错误报告</h3>
            <pre>{{ displayCompileReport }}</pre>
            <div class="compile-error-actions">
              <button class="analyze-report-btn" @click="openAiAndAnalyzeError" :disabled="aiLoading">
                {{ aiLoading ? '分析中...' : '分析错误报告' }}
              </button>
            </div>
          </div>
          <div v-else-if="pdfUrl" class="pdf-viewer">
            <div class="pdf-toolbar" v-if="!pdfIframeFallback">
              <button class="pdf-tool-btn" @click="prevPdfPage" :disabled="pdfPageNumber <= 1">‹</button>
              <button class="pdf-tool-btn" @click="nextPdfPage" :disabled="pdfPageNumber >= pdfPageCount">›</button>
              <span class="pdf-page-indicator">{{ pdfPageNumber }} / {{ pdfPageCount || 1 }}</span>
              <button class="pdf-tool-btn" @click="zoomOutPdf">−</button>
              <button class="pdf-tool-btn" @click="zoomInPdf">+</button>
              <span class="pdf-zoom-indicator">{{ Math.round(pdfScale * 100) }}%</span>
            </div>
            <div class="pdf-canvas-wrap" v-if="!pdfIframeFallback">
              <canvas ref="pdfCanvasRef"></canvas>
            </div>
            <iframe v-else :src="pdfUrl" width="100%" height="100%" class="pdf-fallback-iframe"></iframe>
          </div>
          <div v-else class="pdf-placeholder">
            <p>编译LaTeX代码后将在此显示PDF预览</p>
            <button class="compile-now-btn" @click="compileLatex">立即编译</button>
          </div>
        </div>
      </section>
    </main>

    <div v-if="showAiDialog" class="ai-modal-overlay" @click.self="closeAiDialog">
      <div class="ai-modal">
        <div class="ai-modal-header">
          <h3>AI助手</h3>
          <button class="ai-close-btn" @click="closeAiDialog">×</button>
        </div>
        <div class="ai-modal-body">
          <div v-if="aiMessages.length === 0" class="ai-empty">请输入问题，例如“帮我优化这一段 LaTeX 排版”</div>
          <div v-for="(msg, idx) in aiMessages" :key="idx" class="ai-message" :class="msg.role">
            <div class="ai-message-role">{{ msg.role === 'user' ? '你' : 'AI' }}</div>
            <pre class="ai-message-content">{{ msg.content }}</pre>
          </div>
        </div>
        <div class="ai-modal-footer">
          <textarea
            v-model="aiInput"
            class="ai-input"
            placeholder="输入你的问题..."
            :disabled="aiLoading"
          />
          <button class="ai-send-btn" @click="sendAiMessage" :disabled="aiLoading || !aiInput.trim()">
            {{ aiLoading ? '发送中...' : '发送' }}
          </button>
        </div>
      </div>
    </div>
    
    <!-- 底部状态栏 -->
    <footer class="editor-footer">
      <div class="status-right">
        <span class="last-saved">最后保存: {{ lastSavedTime }}</span>
      </div>
    </footer>
  </div>
</template>

<script>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ComponentType, componentConfig } from '../utils/componentTypes'
import { getProjectById, updateProject, compileProject, exportProjectWord, exportProjectLatex, uploadProjectImage, uploadProjectImageByUrl } from '../api/project'
import { processWithAI } from '../api/ai'
import { EditorState } from '@codemirror/state'
import { EditorView, keymap, lineNumbers, highlightActiveLineGutter } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { syntaxHighlighting, defaultHighlightStyle, StreamLanguage } from '@codemirror/language'
import { stex } from '@codemirror/legacy-modes/mode/stex'
import { foldGutter, indentOnInput, bracketMatching } from '@codemirror/language'
import { closeBrackets, closeBracketsKeymap } from '@codemirror/autocomplete'
import { lintGutter, linter } from '@codemirror/lint'
import * as pdfjsLib from 'pdfjs-dist'
import pdfjsWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfjsWorker

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
    const pdfCanvasRef = ref(null)
    const latexFileInputRef = ref(null)
    const imageFileInputRef = ref(null)
    const codeEditorView = ref(null)
    const pdfDocRef = ref(null)
    const pdfRenderTaskRef = ref(null)
    const pdfLoadingTaskRef = ref(null)
    const pdfPageNumber = ref(1)
    const pdfPageCount = ref(1)
    const pdfScale = ref(1.2)
    const pdfIframeFallback = ref(false)
    const pendingImageInsertPos = ref(null)
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
    const compileError = ref('')
    const lastCompileReport = ref('')
    const cursorLine = ref(1)
    const cursorColumn = ref(1)
    const wordCount = ref(0)
    const lastSavedTime = ref('从未')
    const showAiDialog = ref(false)
    const aiInput = ref('')
    const aiLoading = ref(false)
    const aiMessages = ref([])
    const hasCompileErrorReport = computed(() =>
      previewStatus.value === '编译失败' || Boolean((compileError.value || lastCompileReport.value || '').trim())
    )
    const displayCompileReport = computed(() =>
      (compileError.value || lastCompileReport.value || '编译失败，请查看日志').trim()
    )

    const components = ref(Object.entries(componentConfig).map(([type, config]) => ({
      type,
      name: config.name,
      icon: config.icon,
      latexTemplate: config.latexTemplate,
      draggable: type !== ComponentType.FORMULA && type !== ComponentType.IMAGE && type !== ComponentType.SYMBOL
    })))
    const formulaType = ComponentType.FORMULA
    const imageType = ComponentType.IMAGE
    const symbolType = ComponentType.SYMBOL
    const formulaExpanded = ref(false)
    const imageExpanded = ref(false)
    const symbolExpanded = ref(false)
    const formulaGroups = ref([
      {
        key: 'basic',
        title: '1. 基础运算符',
        items: [
          { key: 'basic-arith', name: '加减乘除', preview: 'a+b-c, a*b, a/b', latex: '\\[\\n  a+b-c,\\quad a\\cdot b,\\quad \\frac{a}{b}\\n\\]\n' },
          { key: 'basic-power', name: '幂次与下标', preview: 'x^2, x_i', latex: '\\[\\n  x^2,\\quad x_i\\n\\]\n' },
          { key: 'basic-root', name: '根号', preview: '\\sqrt{x}, \\sqrt[n]{x}', latex: '\\[\\n  \\sqrt{x},\\quad \\sqrt[n]{x}\\n\\]\n' },
          { key: 'basic-paren', name: '自动括号', preview: '\\left( ... \\right)', latex: '\\[\\n  \\left( \\frac{a+b}{c} \\right)\\n\\]\n' }
        ]
      },
      {
        key: 'calculus',
        title: '2. 求和/积分/极限',
        items: [
          { key: 'sum', name: '求和', preview: '\\sum_{i=1}^n i', latex: '\\[\\n  \\sum_{i=1}^n i\\n\\]\n' },
          { key: 'int1', name: '单重积分', preview: '\\int_0^1 x^2\\,dx', latex: '\\[\\n  \\int_0^1 x^2\\,dx\\n\\]\n' },
          { key: 'int2', name: '二重积分', preview: '\\iint_D f(x,y)\\,dxdy', latex: '\\[\\n  \\iint_D f(x,y)\\,dxdy\\n\\]\n' },
          { key: 'int3', name: '三重积分', preview: '\\iiint_V f(x,y,z)\\,dxdydz', latex: '\\[\\n  \\iiint_V f(x,y,z)\\,dxdydz\\n\\]\n' },
          { key: 'limit', name: '极限', preview: '\\lim_{x\\to0}\\frac{\\sin x}{x}', latex: '\\[\\n  \\lim_{x\\to0}\\frac{\\sin x}{x}\\n\\]\n' }
        ]
      },
      {
        key: 'relation',
        title: '3. 关系符号',
        items: [
          { key: 'rel-eq', name: '等号/不等号', preview: '=, \\neq, \\approx, \\sim', latex: '\\[\\n  a=b,\\ a\\neq b,\\ a\\approx b,\\ a\\sim b\\n\\]\n' },
          { key: 'rel-compare', name: '大小比较', preview: '<, >, \\leq, \\geq', latex: '\\[\\n  a<b,\\ a>b,\\ a\\leq b,\\ a\\geq b\\n\\]\n' },
          { key: 'rel-set', name: '成员与子集', preview: '\\in, \\notin, \\subset, \\subseteq', latex: '\\[\\n  x\\in A,\\ x\\notin B,\\ A\\subset B,\\ A\\subseteq B\\n\\]\n' }
        ]
      },
      {
        key: 'logic',
        title: '4. 逻辑与集合',
        items: [
          { key: 'logic-union', name: '并集/交集', preview: '\\cup, \\cap', latex: '\\[\\n  A\\cup B,\\quad A\\cap B\\n\\]\n' },
          { key: 'logic-empty', name: '空集', preview: '\\emptyset', latex: '\\[\\n  A\\cap B=\\emptyset\\n\\]\n' },
          { key: 'logic-op', name: '逻辑符号', preview: '\\land, \\lor, \\lnot, \\Rightarrow, \\Leftrightarrow', latex: '\\[\\n  p\\land q,\\ p\\lor q,\\ \\lnot p,\\ p\\Rightarrow q,\\ p\\Leftrightarrow q\\n\\]\n' }
        ]
      },
      {
        key: 'function',
        title: '5. 常用函数',
        items: [
          { key: 'fn-tri', name: '三角函数', preview: '\\sin x, \\cos x, \\tan x', latex: '\\[\\n  \\sin x,\\ \\cos x,\\ \\tan x\\n\\]\n' },
          { key: 'fn-log', name: '对数/指数', preview: '\\ln x, \\log x, e^x', latex: '\\[\\n  \\ln x,\\ \\log x,\\ e^x\\n\\]\n' },
          { key: 'fn-maxmin', name: '最大最小', preview: '\\max_{x\\in X}f(x), \\min f(x)', latex: '\\[\\n  \\max_{x\\in X} f(x),\\quad \\min f(x)\\n\\]\n' },
          { key: 'fn-mean', name: '平均/期望', preview: '\\bar{x}, \\mu, E[X]', latex: '\\[\\n  \\bar{x},\\ \\mu,\\ E[X]\\n\\]\n' }
        ]
      },
      {
        key: 'matrix',
        title: '6. 矩阵与向量',
        items: [
          { key: 'm-matrix', name: '矩阵', preview: '\\begin{matrix}...\\end{matrix}', latex: '\\[\\n  \\begin{matrix}\\n    a & b \\\\\\n    c & d\\n  \\end{matrix}\\n\\]\n' },
          { key: 'm-pmatrix', name: '括号矩阵', preview: '\\begin{pmatrix}...\\end{pmatrix}', latex: '\\[\\n  \\begin{pmatrix}\\n    a & b \\\\\\n    c & d\\n  \\end{pmatrix}\\n\\]\n' },
          { key: 'm-vector', name: '向量', preview: '\\vec{v}, \\mathbf{v}', latex: '\\[\\n  \\vec{v},\\quad \\mathbf{v}\\n\\]\n' }
        ]
      },
      {
        key: 'derivative',
        title: '7. 导数与偏导',
        items: [
          { key: 'd-1', name: '一阶导数', preview: '\\frac{dy}{dx}', latex: '\\[\\n  \\frac{dy}{dx}\\n\\]\n' },
          { key: 'd-2', name: '二阶导数', preview: '\\frac{d^2y}{dx^2}', latex: '\\[\\n  \\frac{d^2y}{dx^2}\\n\\]\n' },
          { key: 'd-partial', name: '偏导数', preview: '\\frac{\\partial z}{\\partial x}', latex: '\\[\\n  \\frac{\\partial z}{\\partial x}\\n\\]\n' },
          { key: 'd-grad', name: '梯度/散度/旋度', preview: '\\nabla f, \\nabla\\cdot\\vec{F}, \\nabla\\times\\vec{F}', latex: '\\[\\n  \\nabla f,\\ \\nabla\\cdot\\vec{F},\\ \\nabla\\times\\vec{F}\\n\\]\n' }
        ]
      },
      {
        key: 'advanced',
        title: '8. 高级运算',
        items: [
          { key: 'adv-arg', name: '极大/极小', preview: '\\arg\\max_x f(x), \\arg\\min_x f(x)', latex: '\\[\\n  \\arg\\max_x f(x),\\quad \\arg\\min_x f(x)\\n\\]\n' },
          { key: 'adv-prob', name: '概率符号', preview: 'P(A), E[X], Var(X)', latex: '\\[\\n  P(A),\\ E[X],\\ Var(X)\\n\\]\n' },
          { key: 'adv-bigset', name: '多重集合运算', preview: '\\bigcup_{i=1}^n A_i, \\bigcap_{i=1}^n B_i', latex: '\\[\\n  \\bigcup_{i=1}^n A_i,\\quad \\bigcap_{i=1}^n B_i\\n\\]\n' }
        ]
      }
    ])
    const symbolGroups = ref([
      {
        key: 'greek',
        title: '1. 希腊字母',
        items: [
          { key: 'g-alpha', name: 'α', preview: '\\alpha', latex: '\\alpha ' },
          { key: 'g-beta', name: 'β', preview: '\\beta', latex: '\\beta ' },
          { key: 'g-gamma', name: 'γ', preview: '\\gamma', latex: '\\gamma ' },
          { key: 'g-delta', name: 'δ', preview: '\\delta', latex: '\\delta ' },
          { key: 'g-epsilon', name: 'ε', preview: '\\epsilon', latex: '\\epsilon ' },
          { key: 'g-theta', name: 'θ', preview: '\\theta', latex: '\\theta ' },
          { key: 'g-lambda', name: 'λ', preview: '\\lambda', latex: '\\lambda ' },
          { key: 'g-mu', name: 'μ', preview: '\\mu', latex: '\\mu ' },
          { key: 'g-pi', name: 'π', preview: '\\pi', latex: '\\pi ' },
          { key: 'g-sigma', name: 'σ', preview: '\\sigma', latex: '\\sigma ' },
          { key: 'g-omega', name: 'ω', preview: '\\omega', latex: '\\omega ' }
        ]
      },
      {
        key: 'arith',
        title: '2. 算术运算符',
        items: [
          { key: 'a-plus', name: '加号', preview: '+', latex: '+ ' },
          { key: 'a-minus', name: '减号', preview: '-', latex: '- ' },
          { key: 'a-times', name: '乘号', preview: '\\times', latex: '\\times ' },
          { key: 'a-cdot', name: '点乘', preview: '\\cdot', latex: '\\cdot ' },
          { key: 'a-div', name: '除号', preview: '\\div', latex: '\\div ' },
          { key: 'a-pm', name: '正负', preview: '\\pm', latex: '\\pm ' },
          { key: 'a-mp', name: '负正', preview: '\\mp', latex: '\\mp ' },
          { key: 'a-propto', name: '正比', preview: '\\propto', latex: '\\propto ' },
          { key: 'a-inf', name: '无穷', preview: '\\infty', latex: '\\infty ' }
        ]
      },
      {
        key: 'relation',
        title: '3. 关系符号',
        items: [
          { key: 'r-eq', name: '等号', preview: '=', latex: '= ' },
          { key: 'r-neq', name: '不等号', preview: '\\neq', latex: '\\neq ' },
          { key: 'r-approx', name: '约等于', preview: '\\approx', latex: '\\approx ' },
          { key: 'r-sim', name: '相似', preview: '\\sim', latex: '\\sim ' },
          { key: 'r-leq', name: '小于等于', preview: '\\leq', latex: '\\leq ' },
          { key: 'r-geq', name: '大于等于', preview: '\\geq', latex: '\\geq ' },
          { key: 'r-ll', name: '远小于', preview: '\\ll', latex: '\\ll ' },
          { key: 'r-gg', name: '远大于', preview: '\\gg', latex: '\\gg ' },
          { key: 'r-equiv', name: '恒等', preview: '\\equiv', latex: '\\equiv ' }
        ]
      },
      {
        key: 'set-logic',
        title: '4. 集合与逻辑',
        items: [
          { key: 's-in', name: '属于', preview: '\\in', latex: '\\in ' },
          { key: 's-notin', name: '不属于', preview: '\\notin', latex: '\\notin ' },
          { key: 's-subset', name: '子集', preview: '\\subset', latex: '\\subset ' },
          { key: 's-subseteq', name: '子集等于', preview: '\\subseteq', latex: '\\subseteq ' },
          { key: 's-cup', name: '并集', preview: '\\cup', latex: '\\cup ' },
          { key: 's-cap', name: '交集', preview: '\\cap', latex: '\\cap ' },
          { key: 's-empty', name: '空集', preview: '\\emptyset', latex: '\\emptyset ' },
          { key: 's-land', name: '逻辑与', preview: '\\land', latex: '\\land ' },
          { key: 's-lor', name: '逻辑或', preview: '\\lor', latex: '\\lor ' },
          { key: 's-lnot', name: '逻辑非', preview: '\\lnot', latex: '\\lnot ' },
          { key: 's-imply', name: '蕴含', preview: '\\Rightarrow', latex: '\\Rightarrow ' },
          { key: 's-iff', name: '等价', preview: '\\Leftrightarrow', latex: '\\Leftrightarrow ' }
        ]
      },
      {
        key: 'arrow',
        title: '5. 箭头',
        items: [
          { key: 'ar-left', name: '左箭头', preview: '\\leftarrow', latex: '\\leftarrow ' },
          { key: 'ar-right', name: '右箭头', preview: '\\rightarrow', latex: '\\rightarrow ' },
          { key: 'ar-both', name: '双向箭头', preview: '\\leftrightarrow', latex: '\\leftrightarrow ' },
          { key: 'ar-up', name: '上箭头', preview: '\\uparrow', latex: '\\uparrow ' },
          { key: 'ar-down', name: '下箭头', preview: '\\downarrow', latex: '\\downarrow ' },
          { key: 'ar-left2', name: '反推箭头', preview: '\\Leftarrow', latex: '\\Leftarrow ' },
          { key: 'ar-right2', name: '推导箭头', preview: '\\Rightarrow', latex: '\\Rightarrow ' },
          { key: 'ar-both2', name: '等价箭头', preview: '\\Leftrightarrow', latex: '\\Leftrightarrow ' }
        ]
      },
      {
        key: 'operators',
        title: '6. 运算集符号',
        items: [
          { key: 'o-sum', name: '求和', preview: '\\sum', latex: '\\sum ' },
          { key: 'o-int', name: '积分', preview: '\\int', latex: '\\int ' },
          { key: 'o-iint', name: '二重积分', preview: '\\iint', latex: '\\iint ' },
          { key: 'o-iiint', name: '三重积分', preview: '\\iiint', latex: '\\iiint ' },
          { key: 'o-lim', name: '极限', preview: '\\lim', latex: '\\lim ' },
          { key: 'o-max', name: '最大值', preview: '\\max', latex: '\\max ' },
          { key: 'o-min', name: '最小值', preview: '\\min', latex: '\\min ' },
          { key: 'o-partial', name: '偏导', preview: '\\partial', latex: '\\partial ' },
          { key: 'o-nabla', name: '梯度', preview: '\\nabla', latex: '\\nabla ' },
          { key: 'o-factorial', name: '阶乘', preview: '!', latex: '! ' }
        ]
      },
      {
        key: 'delim',
        title: '7. 括号与定界符',
        items: [
          { key: 'd-paren', name: '小括号', preview: '( )', latex: '( )' },
          { key: 'd-bracket', name: '中括号', preview: '[ ]', latex: '[ ]' },
          { key: 'd-brace', name: '大括号', preview: '\\{ \\}', latex: '\\{ \\}' },
          { key: 'd-abs', name: '绝对值', preview: '\\lvert x \\rvert', latex: '\\lvert x \\rvert ' },
          { key: 'd-norm', name: '范数', preview: '\\lVert x \\rVert', latex: '\\lVert x \\rVert ' },
          { key: 'd-vec', name: '向量符号', preview: '\\vec{}', latex: '\\vec{}' }
        ]
      }
    ])

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

    const clearPdfTasks = () => {
      if (pdfRenderTaskRef.value?.cancel) {
        try {
          pdfRenderTaskRef.value.cancel()
        } catch (e) {}
      }
      pdfRenderTaskRef.value = null
      if (pdfLoadingTaskRef.value?.destroy) {
        try {
          pdfLoadingTaskRef.value.destroy()
        } catch (e) {}
      }
      pdfLoadingTaskRef.value = null
    }

    const renderPdfPage = async () => {
      if (!pdfDocRef.value || !pdfCanvasRef.value) return
      try {
        const page = await pdfDocRef.value.getPage(pdfPageNumber.value)
        const viewport = page.getViewport({ scale: pdfScale.value })
        const canvas = pdfCanvasRef.value
        const context = canvas.getContext('2d')
        if (!context) {
          throw new Error('无法获取 Canvas 上下文')
        }
        canvas.width = Math.ceil(viewport.width)
        canvas.height = Math.ceil(viewport.height)

        if (pdfRenderTaskRef.value?.cancel) {
          try {
            pdfRenderTaskRef.value.cancel()
          } catch (e) {}
        }
        const task = page.render({ canvasContext: context, viewport })
        pdfRenderTaskRef.value = task
        await task.promise
        pdfRenderTaskRef.value = null
      } catch (error) {
        console.error('渲染PDF页面失败，切换到 iframe 预览:', error)
        pdfIframeFallback.value = true
      }
    }

    const loadPdfPreview = async (url) => {
      if (!url) {
        clearPdfTasks()
        pdfDocRef.value = null
        pdfPageNumber.value = 1
        pdfPageCount.value = 1
        pdfIframeFallback.value = false
        return
      }
      clearPdfTasks()
      try {
        pdfIframeFallback.value = false
        const loadingTask = pdfjsLib.getDocument(url)
        pdfLoadingTaskRef.value = loadingTask
        const doc = await loadingTask.promise
        pdfDocRef.value = doc
        pdfPageCount.value = doc.numPages || 1
        pdfPageNumber.value = 1
        await nextTick()
        await renderPdfPage()
      } catch (error) {
        console.error('加载 PDF.js 失败，切换到 iframe 预览:', error)
        pdfIframeFallback.value = true
      }
    }

    const prevPdfPage = async () => {
      if (pdfPageNumber.value <= 1) return
      pdfPageNumber.value -= 1
      await renderPdfPage()
    }

    const nextPdfPage = async () => {
      if (pdfPageNumber.value >= pdfPageCount.value) return
      pdfPageNumber.value += 1
      await renderPdfPage()
    }

    const zoomOutPdf = async () => {
      if (pdfScale.value <= 0.6) return
      pdfScale.value = Math.max(0.6, Number((pdfScale.value - 0.1).toFixed(2)))
      await renderPdfPage()
    }

    const zoomInPdf = async () => {
      if (pdfScale.value >= 2.5) return
      pdfScale.value = Math.min(2.5, Number((pdfScale.value + 0.1).toFixed(2)))
      await renderPdfPage()
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
      if (!component || !component.draggable || !component.latexTemplate) {
        return
      }
      event.dataTransfer.setData('application/json', JSON.stringify(component))
    }

    const handleFormulaDragStart = (event, option) => {
      if (!option || !option.latex) {
        return
      }
      event.dataTransfer.setData('application/json', JSON.stringify({
        type: 'formula_option',
        latexTemplate: option.latex
      }))
    }

    const insertTemplateAtPosition = (text, insertPos, template) => {
      const before = text.slice(0, insertPos)
      const after = text.slice(insertPos)
      return {
        nextText: `${before}${template}${after}`,
        caretPos: before.length + template.length
      }
    }

    const createImageLatex = (pathOrName) => {
      const rawPath = (pathOrName || 'image.png').trim()
      let safePath = rawPath
      if (rawPath.startsWith('/api/images/')) {
        safePath = rawPath.substring('/api/images/'.length).split('?')[0]
      }
      return `\\begin{figure}
    \\centering
    \\includegraphics[width=0.5\\linewidth]{${safePath}}
    \\caption{Enter Caption}
    \\label{fig:placeholder}
\\end{figure}
`
    }

    const insertTemplateByPosition = (insertPos, template) => {
      const editorView = codeEditorView.value
      if (!editorView || !template) {
        return
      }
      const text = editorView.state.doc.toString()
      const { nextText, caretPos } = insertTemplateAtPosition(text, insertPos, template)
      editorView.dispatch({
        changes: { from: 0, to: text.length, insert: nextText },
        selection: { anchor: caretPos }
      })
      editorView.focus()
    }

    const handleComponentClick = (component) => {
      if (!component) return
      if (component.type === formulaType) {
        formulaExpanded.value = !formulaExpanded.value
        if (formulaExpanded.value) {
          imageExpanded.value = false
          symbolExpanded.value = false
        }
        return
      }
      if (component.type === imageType) {
        imageExpanded.value = !imageExpanded.value
        if (imageExpanded.value) {
          formulaExpanded.value = false
          symbolExpanded.value = false
        }
        return
      }
      if (component.type === symbolType) {
        symbolExpanded.value = !symbolExpanded.value
        if (symbolExpanded.value) {
          formulaExpanded.value = false
          imageExpanded.value = false
        }
      }
    }

    const insertSymbolOption = (option) => {
      if (!option || !option.latex) {
        return
      }
      const editorView = codeEditorView.value
      if (!editorView) return
      const insertPos = editorView.state.selection.main.head
      insertTemplateByPosition(insertPos, option.latex)
    }

    const captureImageInsertPosition = () => {
      const editorView = codeEditorView.value
      if (!editorView) return null
      const insertPos = editorView.state.selection.main.head
      pendingImageInsertPos.value = insertPos
      return insertPos
    }

    const triggerLocalImageInsert = () => {
      const insertPos = captureImageInsertPosition()
      if (insertPos == null) return
      if (imageFileInputRef.value) {
        imageFileInputRef.value.value = ''
        imageFileInputRef.value.click()
      }
    }

    const promptImageUrlInsert = () => {
      const insertPos = captureImageInsertPosition()
      if (insertPos == null) return
      const url = window.prompt('请输入图片 URL 或已可访问路径', 'https://example.com/image.png')
      if (!url || !url.trim()) {
        return
      }
      let normalizedUrl = url.trim()
      if (normalizedUrl.startsWith('//')) {
        normalizedUrl = `https:${normalizedUrl}`
      } else if (/^www\./i.test(normalizedUrl)) {
        normalizedUrl = `https://${normalizedUrl}`
      }
      if (!/^https?:\/\//i.test(normalizedUrl)) {
        alert('URL图片插入失败: 请输入完整的 http/https 图片URL')
        pendingImageInsertPos.value = null
        return
      }
      const editorView = codeEditorView.value
      const fallbackPos = editorView ? editorView.state.selection.main.head : 0
      uploadProjectImageByUrl(Number(projectId.value), normalizedUrl)
        .then((res) => {
          const imagePath = res?.data || ''
          const normalizedPath = imagePath.startsWith('/api/images/')
            ? imagePath.substring('/api/images/'.length).split('?')[0]
            : imagePath
          if (!normalizedPath) {
            alert('URL图片处理失败：未返回可用路径')
            return
          }
          const latex = createImageLatex(normalizedPath)
          insertTemplateByPosition(insertPos == null ? fallbackPos : insertPos, latex)
        })
        .catch((error) => {
          console.error('URL图片上传失败:', error)
          alert('URL图片插入失败: ' + (error.message || '未知错误'))
        })
        .finally(() => {
          pendingImageInsertPos.value = null
        })
    }

    const handleImageInsert = (event) => {
      const file = event?.target?.files?.[0]
      if (!file) return
      const insertPos = pendingImageInsertPos.value
      const editorView = codeEditorView.value
      const fallbackPos = editorView ? editorView.state.selection.main.head : 0

      uploadProjectImage(Number(projectId.value), file)
        .then((res) => {
          const imagePath = res?.data || file.name
          const normalizedPath = imagePath.startsWith('/api/images/')
            ? imagePath.substring('/api/images/'.length).split('?')[0]
            : imagePath
          const latex = createImageLatex(normalizedPath)
          insertTemplateByPosition(insertPos == null ? fallbackPos : insertPos, latex)
        })
        .catch((error) => {
          console.error('上传图片失败:', error)
          alert('本地图片上传失败: ' + (error.message || '未知错误'))
        })
        .finally(() => {
          pendingImageInsertPos.value = null
        })
    }

    const handleDrop = (event) => {
      event.preventDefault()
      const componentData = JSON.parse(event.dataTransfer.getData('application/json'))
      const editorView = codeEditorView.value

      if (!editorView || !componentData) {
        return
      }

      const template = componentData.latexTemplate
      if (!template) {
        return
      }

      const text = editorView.state.doc.toString()
      const dropPos = editorView.posAtCoords({ x: event.clientX, y: event.clientY })
      const insertPos = dropPos == null ? editorView.state.selection.main.head : dropPos
      const { nextText, caretPos } = insertTemplateAtPosition(text, insertPos, template)

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
      compileError.value = ''
      // 开始新一轮编译时清空旧预览，避免失败后仍显示旧 PDF
      pdfUrl.value = ''
      try {
        await updateProject(projectId.value, { content: latexCode.value })
        const response = await compileProject(projectId.value, selectedCompiler.value)

        if (response && response.data) {
          const compileResult = response.data
          if ((compileResult.status === 'SUCCESS' || compileResult.status === 'WARNING') && compileResult.pdfPath) {
            pdfUrl.value = compileResult.pdfPath
            compileError.value = ''
            lastCompileReport.value = ''
            previewStatus.value = compileResult.status === 'WARNING' ? '编译完成（有警告）' : '编译成功'
            const compileTime = compileResult.compileTimeMs ? (compileResult.compileTimeMs / 1000).toFixed(2) : '0'
            console.log(`编译完成，耗时: ${compileTime}秒`)

            if (compileResult.status === 'WARNING' && compileResult.errorMessage) {
              console.warn('编译警告:', compileResult.errorMessage)
            }
          } else {
            previewStatus.value = '编译失败'
            const errorMsg = compileResult.errorMessage || compileResult.logContent || '编译失败，请查看日志'
            const logDetail = compileResult.logContent ? `\n\n--- 编译日志 ---\n${compileResult.logContent}` : ''
            compileError.value = `${errorMsg}${logDetail}`
            lastCompileReport.value = compileError.value
            if (!silent) {
              alert('编译失败: ' + errorMsg)
            }
            console.error('编译日志:', compileResult.logContent)
            pdfUrl.value = ''
          }
        } else {
          previewStatus.value = '编译失败'
          compileError.value = '编译失败：未收到有效响应'
          lastCompileReport.value = compileError.value
          if (!silent) {
            alert('编译失败: 未收到有效响应')
          }
        }
      } catch (error) {
        console.error('编译失败:', error)
        previewStatus.value = '编译失败'
        const errorMsg = error.response?.data?.message || error.message || '未知错误'
        compileError.value = `编译失败: ${errorMsg}`
        lastCompileReport.value = compileError.value
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

    const downloadLatex = async () => {
      try {
        await updateProject(projectId.value, { content: latexCode.value })
        const response = await exportProjectLatex(projectId.value)
        const blob = new Blob([response.data], { type: 'application/x-tex;charset=UTF-8' })
        const fileName = `${projectName.value || 'latex-document'}.tex`
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = fileName
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
      } catch (error) {
        console.error('导出LaTeX失败:', error)
        let errorMsg = '导出LaTeX失败'
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

    const triggerImportLatex = () => {
      if (latexFileInputRef.value) {
        latexFileInputRef.value.value = ''
        latexFileInputRef.value.click()
      }
    }

    const handleLatexImport = async (event) => {
      const file = event?.target?.files?.[0]
      if (!file) return
      if (!file.name.toLowerCase().endsWith('.tex')) {
        alert('请导入 .tex 格式文件')
        return
      }
      if (!confirm('导入后将覆盖当前项目内容，是否继续？')) {
        return
      }

      try {
        const content = await file.text()
        setEditorContent(content || '')
        await updateProject(projectId.value, { content: content || '' })
        pdfUrl.value = ''
        previewStatus.value = '未编译'
        lastSavedTime.value = new Date().toLocaleString()
        alert('LaTeX 文件导入成功，已覆盖当前项目')
      } catch (error) {
        console.error('导入LaTeX失败:', error)
        alert('导入LaTeX失败: ' + (error.message || '未知错误'))
      }
    }

    const openAiDialog = () => {
      showAiDialog.value = true
    }

    const closeAiDialog = () => {
      showAiDialog.value = false
    }

    const sendAiMessage = async () => {
      const question = aiInput.value.trim()
      if (!question || aiLoading.value) return
      aiMessages.value.push({ role: 'user', content: question })
      aiInput.value = ''
      aiLoading.value = true
      try {
        const context = latexCode.value?.slice(0, 12000) || ''
        const response = await processWithAI({
          projectId: Number(projectId.value),
          type: 'CHAT',
          content: `用户问题：${question}\n\n当前LaTeX内容：\n${context}`
        })
        const result = response?.data?.result || response?.data?.suggestion || 'AI未返回内容'
        aiMessages.value.push({ role: 'assistant', content: result })
      } catch (error) {
        aiMessages.value.push({ role: 'assistant', content: `请求失败：${error.message || '未知错误'}` })
      } finally {
        aiLoading.value = false
      }
    }

    const analyzeCompileError = async () => {
      if (aiLoading.value) return
      const report = ((compileError.value || lastCompileReport.value) || '').trim()
      if (!report) {
        alert('当前没有可分析的错误报告，请先触发一次编译错误')
        return
      }

      aiMessages.value.push({
        role: 'user',
        content: '请分析当前编译错误报告并给出修复建议'
      })
      aiLoading.value = true
      try {
        const response = await processWithAI({
          projectId: Number(projectId.value),
          type: 'ERROR_ANALYSIS',
          content: `编译错误报告：\n${report}\n\n当前LaTeX内容：\n${latexCode.value?.slice(0, 12000) || ''}`
        })
        const result = response?.data?.result || response?.data?.suggestion || 'AI未返回分析结果'
        aiMessages.value.push({ role: 'assistant', content: result })
      } catch (error) {
        aiMessages.value.push({ role: 'assistant', content: `分析失败：${error.message || '未知错误'}` })
      } finally {
        aiLoading.value = false
      }
    }

    const openAiAndAnalyzeError = async () => {
      openAiDialog()
      await analyzeCompileError()
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

    watch(pdfUrl, async (val) => {
      try {
        await loadPdfPreview(val)
      } catch (error) {
        console.error('加载PDF预览失败:', error)
      }
    })

    onBeforeUnmount(() => {
      clearAutoCompileTimer()
      if (codeEditorView.value) {
        codeEditorView.value.destroy()
        codeEditorView.value = null
      }
      clearPdfTasks()
    })

    return {
      projectName,
      selectedCompiler,
      compilerOptions,
      latexCode,
      pdfUrl,
      codeEditorRef,
      pdfCanvasRef,
      latexFileInputRef,
      imageFileInputRef,
      loading,
      previewStatus,
      compileError,
      lastCompileReport,
      hasCompileErrorReport,
      displayCompileReport,
      showAiDialog,
      aiInput,
      aiLoading,
      aiMessages,
      cursorLine,
      cursorColumn,
      wordCount,
      lastSavedTime,
      components,
      formulaType,
      imageType,
      symbolType,
      formulaExpanded,
      imageExpanded,
      symbolExpanded,
      formulaGroups,
      symbolGroups,
      goBack,
      saveProject,
      compileLatex,
      downloadPdf,
      downloadWord,
      downloadLatex,
      triggerImportLatex,
      handleLatexImport,
      handleImageInsert,
      openAiDialog,
      closeAiDialog,
      sendAiMessage,
      analyzeCompileError,
      openAiAndAnalyzeError,
      handleCursorPosition,
      updateWordCount,
      handleDragStart,
      handleFormulaDragStart,
      insertSymbolOption,
      handleComponentClick,
      triggerLocalImageInsert,
      promptImageUrlInsert,
      handleDrop,
      pdfPageNumber,
      pdfPageCount,
      pdfScale,
      pdfIframeFallback,
      prevPdfPage,
      nextPdfPage,
      zoomOutPdf,
      zoomInPdf
    }
  }
}
</script>

<style scoped>
.editor-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  overflow: hidden;
  padding: 18px;
  gap: 14px;
}

/* 顶部导航栏 */
.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 18px;
  background: var(--fluent-surface);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  border: 1px solid var(--fluent-border);
  color: var(--fluent-text-1);
  box-shadow: var(--fluent-shadow);
  border-radius: 22px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.header-center {
  flex: 1 1 180px;
  display: flex;
  justify-content: center;
}

.back-btn {
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(132, 160, 207, 0.18);
  color: var(--fluent-text-1);
  font-size: 14px;
  cursor: pointer;
  padding: 10px 14px;
  border-radius: 14px;
  transition: all 0.2s ease;
  font-weight: 600;
}

.back-btn:hover {
  background: rgba(240, 247, 255, 0.92);
  color: var(--fluent-accent);
}

.project-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--fluent-text-1);
  letter-spacing: 0.2px;
  transition: color 0.2s ease;
}

.project-title:hover {
  color: var(--fluent-accent);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
  flex-wrap: wrap;
}

.compiler-select {
  height: 40px;
  padding: 0 12px;
  border: 1px solid rgba(132, 160, 207, 0.18);
  border-radius: 14px;
  background-color: rgba(255, 255, 255, 0.76);
  color: var(--fluent-text-1);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s, border-color 0.2s;
}

.compiler-select:hover {
  background-color: rgba(240, 247, 255, 0.92);
}

.compiler-select:focus {
  outline: none;
  border-color: rgba(15, 108, 189, 0.38);
  box-shadow: 0 0 0 3px rgba(15, 108, 189, 0.12);
}

.compiler-select option {
  color: #333;
  background-color: #fff;
}

.save-btn, .compile-btn, .download-btn, .download-word-btn, .download-latex-btn, .import-latex-btn {
  min-height: 40px;
  padding: 8px 14px;
  border: 1px solid transparent;
  border-radius: 14px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
  box-shadow: 0 10px 22px rgba(71, 104, 158, 0.1);
}

.save-btn {
  background: linear-gradient(135deg, #107c10 0%, #16a637 100%);
  color: white;
}

.save-btn:hover {
  transform: translateY(-1px);
}

.compile-btn {
  background: linear-gradient(135deg, #0f6cbd 0%, #4f8cff 100%);
  color: white;
}

.compile-btn:hover {
  transform: translateY(-1px);
}

.download-btn {
  background: linear-gradient(135deg, #876400 0%, #c37d00 100%);
  color: white;
}

.download-btn:hover {
  transform: translateY(-1px);
}

.download-word-btn {
  background: linear-gradient(135deg, #5c2e91 0%, #7f56d9 100%);
  color: white;
}

.download-word-btn:hover {
  transform: translateY(-1px);
}

.download-latex-btn {
  background: linear-gradient(135deg, #0b6a6e 0%, #1f9ca4 100%);
  color: white;
}

.download-latex-btn:hover {
  transform: translateY(-1px);
}

.import-latex-btn {
  background: rgba(255, 255, 255, 0.7);
  color: var(--fluent-text-1);
  border-color: rgba(132, 160, 207, 0.18);
}

.import-latex-btn:hover {
  background: rgba(240, 247, 255, 0.92);
  color: var(--fluent-accent);
}

.latex-file-input {
  display: none;
}

.ai-assistant-btn {
  background: linear-gradient(135deg, #0f6cbd 0%, #6f8cff 100%);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 14px;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 14px 24px rgba(15, 108, 189, 0.18);
}

.ai-assistant-btn:hover {
  transform: translateY(-1px);
}

.ai-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(18, 24, 38, 0.26);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000;
  padding: 20px;
}

.ai-modal {
  width: min(820px, 92vw);
  height: min(70vh, 620px);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(28px) saturate(150%);
  -webkit-backdrop-filter: blur(28px) saturate(150%);
  border-radius: 24px;
  box-shadow: var(--fluent-shadow-hover);
  border: 1px solid var(--fluent-border);
  display: flex;
  flex-direction: column;
}

.ai-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(132, 160, 207, 0.16);
}

.ai-close-btn {
  border: 1px solid rgba(132, 160, 207, 0.18);
  background: rgba(255, 255, 255, 0.62);
  font-size: 22px;
  cursor: pointer;
  color: var(--fluent-text-2);
  border-radius: 12px;
  width: 36px;
  height: 36px;
}

.ai-modal-body {
  flex: 1;
  overflow: auto;
  padding: 12px 16px;
  background: rgba(247, 250, 255, 0.54);
}

.ai-empty {
  color: var(--fluent-text-3);
  font-size: 14px;
}

.ai-message {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 16px;
  border: 1px solid rgba(132, 160, 207, 0.12);
}

.ai-message.user {
  background: rgba(223, 236, 255, 0.78);
}

.ai-message.assistant {
  background: rgba(255, 255, 255, 0.72);
}

.ai-message-role {
  font-size: 12px;
  color: var(--fluent-text-2);
  margin-bottom: 4px;
}

.ai-message-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.45;
  font-family: Consolas, 'Courier New', monospace;
}

.ai-modal-footer {
  border-top: 1px solid rgba(132, 160, 207, 0.16);
  padding: 10px 12px;
  display: flex;
  gap: 10px;
}

.ai-input {
  flex: 1;
  min-height: 74px;
  resize: vertical;
  border: 1px solid rgba(132, 160, 207, 0.22);
  border-radius: 16px;
  padding: 8px 10px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.78);
}

.ai-send-btn {
  align-self: flex-end;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 14px;
  background: linear-gradient(135deg, #0f6cbd 0%, #4f8cff 100%);
  color: white;
  padding: 9px 14px;
  cursor: pointer;
  font-weight: 600;
}

.ai-send-btn:disabled {
  background: #95b8f5;
  cursor: not-allowed;
}

/* 主编辑区域 */
.editor-main {
  display: flex;
  flex: 1;
  overflow: hidden;
  gap: 14px;
  min-height: 0;
}

/* 左侧组件库 */
.editor-sidebar {
  width: 240px;
  display: flex;
  flex-direction: column;
  background: var(--fluent-surface);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  border: 1px solid var(--fluent-border);
  border-radius: 22px;
  box-shadow: var(--fluent-shadow);
  overflow-y: auto;
}

.sidebar-header {
  padding: 10px 20px;
  background-color: rgba(255, 255, 255, 0.3);
  border-bottom: 1px solid rgba(132, 160, 207, 0.14);
}

.sidebar-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--fluent-text-1);
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

.component-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.component-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background-color: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(132, 160, 207, 0.16);
  border-radius: 14px;
  cursor: grab;
  transition: all 0.2s;
}

.component-item:hover {
  background-color: rgba(223, 236, 255, 0.74);
  border-color: rgba(15, 108, 189, 0.2);
  transform: translateY(-1px);
  box-shadow: 0 12px 22px rgba(71, 104, 158, 0.12);
}

.component-item:active {
  cursor: grabbing;
}

.component-item.expandable {
  cursor: pointer;
}

.component-item.expanded {
  border-color: rgba(15, 108, 189, 0.22);
  background-color: rgba(223, 236, 255, 0.82);
}

.component-icon {
  font-size: 18px;
  width: 24px;
  text-align: center;
}

.component-name {
  font-size: 14px;
  color: var(--fluent-text-1);
  flex: 1;
  font-weight: 600;
}

.expand-indicator {
  font-size: 14px;
  color: var(--fluent-text-2);
}

.formula-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-left: 8px;
}

.formula-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.formula-group-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--fluent-text-2);
  padding: 4px 2px;
}

.formula-option {
  border: 1px solid rgba(132, 160, 207, 0.16);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.74);
  padding: 8px;
  text-align: left;
  cursor: grab;
  transition: all 0.2s ease;
}


.formula-option:hover {
  border-color: rgba(15, 108, 189, 0.2);
  background: rgba(240, 247, 255, 0.92);
}

.formula-option:active {
  cursor: grabbing;
}

.formula-option-name {
  display: block;
  font-size: 12px;
  color: var(--fluent-text-2);
  margin-bottom: 4px;
}

.formula-option-preview {
  font-size: 12px;
  color: var(--fluent-text-1);
  white-space: pre-wrap;
  word-break: break-word;
}

/* 中间LaTeX编辑区 */
.editor-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--fluent-surface);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  border: 1px solid var(--fluent-border);
  border-radius: 22px;
  box-shadow: var(--fluent-shadow);
  min-width: 0;
}

.code-editor-container {
  flex: 1;
  padding: 20px;
  overflow: hidden;
  min-height: 0;
}

.code-editor-container.drag-over {
  background-color: rgba(223, 236, 255, 0.6);
  border-radius: 18px;
}

/* 右侧PDF预览区 */
.editor-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--fluent-surface);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  border: 1px solid var(--fluent-border);
  border-radius: 22px;
  box-shadow: var(--fluent-shadow);
  min-width: 0;
}

.editor-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background-color: rgba(255, 255, 255, 0.3);
  border-bottom: 1px solid rgba(132, 160, 207, 0.14);
}

.editor-section-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--fluent-text-1);
}

.preview-status {
  font-size: 14px;
  color: var(--fluent-text-2);
  padding: 4px 8px;
  border-radius: 999px;
  background-color: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(132, 160, 207, 0.16);
}

.code-editor-container {
  flex: 1;
  padding: 20px;
  overflow: hidden;
}

.code-editor {
  width: 100%;
  height: 100%;
  border: 1px solid rgba(132, 160, 207, 0.16);
  border-radius: 18px;
  background-color: rgba(255, 255, 255, 0.92);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
  overflow: hidden;
}

:deep(.cm-editor) {
  height: 100%;
  font-family: Consolas, 'Courier New', monospace;
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

::deep(.cm-gutters) {
  background: rgba(244, 247, 253, 0.95);
  color: var(--fluent-text-3);
  border-right: 1px solid rgba(132, 160, 207, 0.12);
}

::deep(.cm-activeLine),
::deep(.cm-activeLineGutter) {
  background: rgba(223, 236, 255, 0.6);
}

.pdf-preview-container {
  flex: 1;
  padding: 20px;
  overflow: auto;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: rgba(247, 250, 255, 0.4);
}

.pdf-viewer {
  width: 100%;
  height: 100%;
  background-color: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(132, 160, 207, 0.16);
  border-radius: 18px;
  box-shadow: 0 16px 30px rgba(71, 104, 158, 0.12);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.pdf-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(132, 160, 207, 0.14);
  background: rgba(244, 247, 253, 0.8);
}

.pdf-tool-btn {
  border: 1px solid rgba(132, 160, 207, 0.18);
  background: rgba(255, 255, 255, 0.82);
  border-radius: 10px;
  width: 28px;
  height: 28px;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: var(--fluent-text-1);
}

.pdf-tool-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pdf-page-indicator,
.pdf-zoom-indicator {
  font-size: 13px;
  color: var(--fluent-text-2);
  min-width: 50px;
  text-align: center;
}

.pdf-canvas-wrap {
  flex: 1;
  overflow: auto;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 12px;
  background: rgba(247, 250, 255, 0.7);
}

.pdf-canvas-wrap canvas {
  box-shadow: 0 16px 28px rgba(71, 104, 158, 0.12);
  background: #fff;
  border-radius: 10px;
}

.pdf-fallback-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.pdf-loading {
  text-align: center;
  color: var(--fluent-text-2);
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid var(--fluent-accent);
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
  color: var(--fluent-text-3);
  padding: 40px;
}

.compile-error-panel {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  padding: 16px;
  border: 1px solid rgba(196, 43, 28, 0.14);
  border-radius: 18px;
  background-color: rgba(255, 245, 246, 0.9);
  color: #7a1f27;
  overflow: auto;
}

.compile-error-panel h3 {
  margin: 0 0 10px 0;
  font-size: 16px;
}

.compile-error-panel pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.45;
}

.compile-error-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.analyze-report-btn {
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 14px;
  background: linear-gradient(135deg, #a15c00 0%, #d48600 100%);
  color: #fff;
  padding: 8px 14px;
  font-size: 13px;
  cursor: pointer;
  font-weight: 600;
}

.analyze-report-btn:hover {
  transform: translateY(-1px);
}

.analyze-report-btn:disabled {
  background: #f3c27a;
  cursor: not-allowed;
}

.compile-now-btn {
  margin-top: 15px;
  padding: 8px 16px;
  background: linear-gradient(135deg, #0f6cbd 0%, #4f8cff 100%);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-weight: 600;
}

.compile-now-btn:hover {
  transform: translateY(-1px);
}

/* 底部状态栏 */
.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 20px;
  background: var(--fluent-surface);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  border: 1px solid var(--fluent-border);
  border-radius: 18px;
  font-size: 12px;
  color: var(--fluent-text-2);
  box-shadow: var(--fluent-shadow);
}

.status-left, .status-right {
  display: flex;
  gap: 15px;
}

@media (max-width: 1280px) {
  .editor-main {
    flex-direction: column;
  }

  .editor-sidebar {
    width: 100%;
    max-height: 280px;
  }
}
</style>