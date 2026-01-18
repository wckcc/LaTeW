# 学术论文写作排版系统

## 项目简介

基于JavaWeb技术开发的学术论文在线写作排版系统，支持LaTeX格式的在线编辑、编译、导出，并提供AI助手辅助功能。系统采用前后端分离架构，提供友好的用户界面和丰富的功能特性。

## 技术架构

### 后端技术栈
- **框架**: Spring Boot 3.x
- **数据访问**: MyBatis
- **数据库**: MySQL / H2 (开发环境)
- **Java版本**: JDK 21

### 项目结构

```
src/main/java/org/example/
├── controller/          # 控制器层（REST API）
│   ├── UserController.java          # 用户管理API
│   ├── DocumentController.java      # 文档管理API
│   ├── TemplateController.java      # 模板管理API
│   ├── EditorController.java        # 在线编辑器API
│   └── AIController.java            # AI助手API
├── service/             # 服务层接口
│   ├── UserService.java             # 用户服务接口
│   ├── DocumentService.java         # 文档服务接口
│   ├── TemplateService.java         # 模板服务接口
│   ├── LaTeXService.java            # LaTeX编译导出服务接口
│   └── AIService.java               # AI助手服务接口
├── mapper/              # 数据访问层（MyBatis Mapper）
│   ├── UserMapper.java              # 用户数据访问接口
│   ├── DocumentMapper.java          # 文档数据访问接口
│   ├── TemplateMapper.java          # 模板数据访问接口
│   └── CompileLogMapper.java        # 编译日志数据访问接口
└── dto/                 # 数据传输对象
    ├── UserDTO.java
    ├── DocumentDTO.java
    ├── TemplateDTO.java
    ├── CompileRequest.java
    ├── CompileResult.java
    ├── AIRequest.java
    ├── AIResponse.java
    ├── ExportRequest.java
    ├── ImportRequest.java
    └── ResponseResult.java
```

## 核心功能

### 1. 用户管理
- ✅ 用户注册和登录
- ✅ 用户信息管理（增删改查）
- ✅ 用户权限管理

**API接口**:
- `POST /api/users` - 创建用户
- `GET /api/users/{id}` - 获取用户信息
- `GET /api/users` - 获取所有用户
- `PUT /api/users/{id}` - 更新用户信息
- `DELETE /api/users/{id}` - 删除用户

### 2. 文档管理
- ✅ 文档的创建、编辑、删除
- ✅ 文档列表查询
- ✅ 文档内容保存（支持LaTeX和HTML两种格式）

**API接口**:
- `POST /api/documents` - 创建文档
- `GET /api/documents/{id}` - 获取文档
- `GET /api/documents/user/{userId}` - 获取用户的文档列表
- `PUT /api/documents/{id}` - 更新文档
- `PUT /api/documents/{id}/content` - 保存文档内容
- `DELETE /api/documents/{id}` - 删除文档

### 3. 在线排版编辑
- ✅ 命令模式：支持手动输入LaTeX代码
- ✅ 可视化模式：支持拖拽组件进行可视化编辑（待实现）
- ✅ 实时预览功能

**API接口**:
- `POST /api/editor/compile` - 编译LaTeX文档
- `POST /api/editor/validate` - 验证LaTeX语法
- `GET /api/editor/{documentId}/compile-log` - 获取编译日志

### 4. 文件导入导出
- ✅ LaTeX格式导出
- ✅ Word格式导出（.docx）
- ✅ PDF格式导出
- ✅ LaTeX格式文件导入（支持AI优化）
- ✅ PDF文件导入（使用DeepSeek AI自动识别并转换为LaTeX）

**API接口**:
- `GET /api/editor/{documentId}/export/latex` - 导出为LaTeX
- `GET /api/editor/{documentId}/export/word` - 导出为Word
- `GET /api/editor/{documentId}/export/pdf` - 导出为PDF
- `POST /api/editor/import/latex` - 导入LaTeX文件

### 5. LaTeX编译
- ✅ 支持多种LaTeX编译器：
  - PDFLaTeX
  - XeLaTeX（支持中文）
  - LuaLaTeX
- ✅ 编译日志记录
- ✅ 错误信息捕获和分析

**编译模式**:
- `pdflatex` - 标准PDFLaTeX编译器
- `xelatex` - XeLaTeX编译器（推荐用于中文文档）
- `lualatex` - LuaLaTeX编译器

### 6. AI助手功能
- ✅ 错误报告分析：分析LaTeX编译错误并提供修复建议
- ✅ 排版优化：使用AI优化LaTeX代码结构
- ✅ 语法修复：自动修复LaTeX语法错误
- ✅ 文件导入时AI优化选项
- ✅ PDF转LaTeX：使用DeepSeek AI自动识别PDF内容并转换为LaTeX代码

**API接口**:
- `POST /api/ai/pdf-to-latex` - 将PDF文件转换为LaTeX代码
- `POST /api/ai/process` - 通用AI处理（错误分析、优化、语法修复等）
- `POST /api/projects/from-pdf` - 从PDF文件创建项目（包含AI转换）

### 7. 模板系统
- ✅ 提供多种学术论文模板
- ✅ 模板分类管理（通用、会议、期刊、学位论文、书籍）
- ✅ 系统模板和用户自定义模板
- ✅ 模板使用统计

**API接口**:
- `GET /api/templates` - 获取所有模板
- `GET /api/templates/{id}` - 获取模板详情
- `GET /api/templates/category/{category}` - 按分类获取模板
- `GET /api/templates/system` - 获取系统模板
- `POST /api/templates` - 创建模板（管理员）
- `PUT /api/templates/{id}` - 更新模板（管理员）
- `DELETE /api/templates/{id}` - 删除模板（管理员）

**模板分类**:
- `GENERAL` - 通用模板
- `CONFERENCE` - 会议论文模板
- `JOURNAL` - 期刊论文模板
- `THESIS` - 学位论文模板
- `BOOK` - 书籍模板

## 数据库设计

### 用户表 (users)
- id: 主键
- username: 用户名（唯一）
- email: 邮箱（唯一）
- password: 密码
- real_name: 真实姓名
- avatar: 头像路径
- role: 角色（USER/ADMIN）
- created_at: 创建时间
- updated_at: 更新时间

### 文档表 (documents)
- id: 主键
- title: 文档标题
- content: LaTeX内容
- html_content: HTML可视化内容
- user_id: 用户ID（外键）
- template_id: 模板ID（外键）
- status: 状态（DRAFT/PUBLISHED/ARCHIVED）
- latex_compiler: LaTeX编译器类型
- created_at: 创建时间
- updated_at: 更新时间
- last_compiled_at: 最后编译时间

### 模板表 (templates)
- id: 主键
- name: 模板名称
- description: 模板描述
- content: LaTeX模板内容
- preview_image: 预览图路径
- category: 模板分类
- is_system: 是否为系统模板
- usage_count: 使用次数
- created_at: 创建时间
- updated_at: 更新时间

### 编译日志表 (compile_logs)
- id: 主键
- document_id: 文档ID（外键）
- status: 编译状态（SUCCESS/ERROR/WARNING）
- error_message: 错误信息
- log_content: 完整日志内容
- compiler_version: 编译器版本
- compile_time_ms: 编译耗时（毫秒）
- created_at: 创建时间

## API响应格式

所有API统一使用 `ResponseResult<T>` 格式返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {"...": "..."}
}
```

**状态码说明**:
- `200` - 操作成功
- `400` - 请求参数错误
- `404` - 资源不存在
- `500` - 服务器内部错误

## 开发计划

### 已完成
- ✅ 项目基础架构搭建
- ✅ Controller层API接口实现
- ✅ Service层接口定义
- ✅ Mapper层接口定义
- ✅ DTO数据传输对象定义

### 待实现
- ⏳ Service层具体业务逻辑实现
- ⏳ Mapper层SQL映射实现
- ⏳ 数据库表结构创建
- ⏳ LaTeX编译功能实现
- ⏳ 文件导入导出功能实现
- ⏳ AI助手API集成
- ⏳ 前端页面开发
- ⏳ 用户认证和授权
- ⏳ 可视化拖拽编辑器

## 部署说明

### 环境要求
- JDK 21+
- Maven 3.6+
- MySQL 8.0+ (生产环境)
- LaTeX发行版（TeX Live / MiKTeX）

### 配置说明
1. 配置数据库连接（application.yml）
2. 配置LaTeX编译器路径
3. 配置DeepSeek API密钥（application.yml或环境变量DEEPSEEK_API_KEY）
   - 在application.yml中配置：`deepseek.api.api-key: your-api-key-here`
   - 或通过环境变量：`export DEEPSEEK_API_KEY=your-api-key-here`

### 运行方式
```bash
# 编译项目
mvn clean package

# 运行应用
java -jar target/academic-paper-editor-1.0-SNAPSHOT.jar
```

## 浏览器兼容性

系统兼容以下主流浏览器：
- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

## 许可证

MIT License

## 联系方式

如有问题或建议，请联系项目维护者。

