// 组件类型枚举
export const ComponentType = {
  // 标题
  HEADING_1: 'heading_1',
  HEADING_2: 'heading_2',
  HEADING_3: 'heading_3',
  
  // 文本
  PARAGRAPH: 'paragraph',
  
  // 公式
  INLINE_MATH: 'inline_math',
  BLOCK_MATH: 'block_math',
  
  // 图片
  IMAGE: 'image',
  
  // 表格
  TABLE: 'table',
  
  // 列表
  UNORDERED_LIST: 'unordered_list',
  ORDERED_LIST: 'ordered_list',
  
  // 引用
  QUOTE: 'quote',
  
  // 代码
  CODE: 'code'
};

// 组件配置
export const componentConfig = {
  [ComponentType.HEADING_1]: {
    name: '一级标题',
    icon: '📌',
    latexTemplate: '\\section{标题内容}'
  },
  [ComponentType.HEADING_2]: {
    name: '二级标题',
    icon: '📎',
    latexTemplate: '\\subsection{标题内容}'
  },
  [ComponentType.HEADING_3]: {
    name: '三级标题',
    icon: '📏',
    latexTemplate: '\\subsubsection{标题内容}'
  },
  [ComponentType.PARAGRAPH]: {
    name: '段落',
    icon: '📝',
    latexTemplate: '这是一个段落内容。'
  },
  [ComponentType.INLINE_MATH]: {
    name: '行内公式',
    icon: '∑',
    latexTemplate: '$E = mc^2$'
  },
  [ComponentType.BLOCK_MATH]: {
    name: '块级公式',
    icon: '∫',
    latexTemplate: '\\[\\n  E = mc^2\\n\\]'
  },
  [ComponentType.IMAGE]: {
    name: '图片',
    icon: '🖼️',
    latexTemplate: '\\begin{figure}[h!]\\n  \\centering\\n  \\includegraphics[width=0.8\\linewidth]{image.png}\\n  \\caption{图片标题}\\n  \\label{fig:image}\\n\\end{figure}'
  },
  [ComponentType.TABLE]: {
    name: '表格',
    icon: '📊',
    latexTemplate: '\\begin{table}[h!]\\n  \\centering\\n  \\begin{tabular}{|c|c|c|}\\n    \\hline\\n    表头1 & 表头2 & 表头3 \\\\\\n    \\hline\\n    内容1 & 内容2 & 内容3 \\\\\\n    \\hline\\n  \\end{tabular}\\n  \\caption{表格标题}\\n  \\label{tab:table}\\n\\end{table}'
  },
  [ComponentType.UNORDERED_LIST]: {
    name: '无序列表',
    icon: '•',
    latexTemplate: '\\begin{itemize}\\n  \\item 列表项1\\n  \\item 列表项2\\n  \\item 列表项3\\n\\end{itemize}'
  },
  [ComponentType.ORDERED_LIST]: {
    name: '有序列表',
    icon: '1.',
    latexTemplate: '\\begin{enumerate}\\n  \\item 列表项1\\n  \\item 列表项2\\n  \\item 列表项3\\n\\end{enumerate}'
  },
  [ComponentType.QUOTE]: {
    name: '引用',
    icon: '"',
    latexTemplate: '\\begin{quote}\\n  这是一段引用内容。\\n\\end{quote}'
  },
  [ComponentType.CODE]: {
    name: '代码',
    icon: '💻',
    latexTemplate: '\\begin{verbatim}\\n// 这是一段代码\\nfunction hello() {\\n  console.log("Hello, world!");\\n}\\n\\end{verbatim}'
  }
};
