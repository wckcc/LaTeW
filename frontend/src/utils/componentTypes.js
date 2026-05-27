// 组件类型枚举
export const ComponentType = {
  // 标题
  HEADING_1: 'heading_1',
  HEADING_2: 'heading_2',
  HEADING_3: 'heading_3',
  
  // 公式
  FORMULA: 'formula',
  
  // 图片
  IMAGE: 'image',

  // 符号
  SYMBOL: 'symbol',
  
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
    latexTemplate: '\\chapter{标题内容}'
  },
  [ComponentType.HEADING_2]: {
    name: '二级标题',
    icon: '📎',
    latexTemplate: '\\section{标题内容}'
  },
  [ComponentType.HEADING_3]: {
    name: '三级标题',
    icon: '📏',
    latexTemplate: '\\subsection{标题内容}'
  },
  [ComponentType.FORMULA]: {
    name: '公式',
    icon: '∑',
    latexTemplate: ''
  },
  [ComponentType.IMAGE]: {
    name: '图片',
    icon: '🖼️',
    latexTemplate: `\\begin{figure}
    \\centering
    \\includegraphics[width=0.5\\linewidth]{image.png}
    \\caption{Enter Caption}
    \\label{fig:placeholder}
\\end{figure}`
  },
  [ComponentType.SYMBOL]: {
    name: '符号',
    icon: 'Ω',
    latexTemplate: ''
  },
  [ComponentType.TABLE]: {
    name: '表格',
    icon: '📊',
    latexTemplate: `\\begin{table}
    \\centering
    \\begin{tabular}{|c|c|c|}
        \\hline
        Header1 & Header2 & Header3 \\\\
        \\hline
        Value1 & Value2 & Value3 \\\\
        \\hline
    \\end{tabular}
    \\caption{Enter Caption}
    \\label{tab:placeholder}
\\end{table}`
  },
  [ComponentType.UNORDERED_LIST]: {
    name: '无序列表',
    icon: '•',
    latexTemplate: `\\begin{itemize}
    \\item Item 1
    \\item Item 2
    \\item Item 3
\\end{itemize}`
  },
  [ComponentType.ORDERED_LIST]: {
    name: '有序列表',
    icon: '1.',
    latexTemplate: `\\begin{enumerate}
    \\item Item 1
    \\item Item 2
    \\item Item 3
\\end{enumerate}`
  },
  [ComponentType.QUOTE]: {
    name: '引用',
    icon: '"',
    latexTemplate: `\\begin{quote}
    Quote text here.
\\end{quote}`
  },
  [ComponentType.CODE]: {
    name: '代码',
    icon: '💻',
    latexTemplate: `\\begin{verbatim}
function hello() {
    console.log("Hello, world!");
}
\\end{verbatim}`
  }
};
