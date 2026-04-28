# LaTeW 项目完整文档

## 1. 项目概述

LaTeW 是一个学术写作与排版系统，支持：

- 用户注册/登录、个人资料管理
- LaTeX 项目创建、编辑、编译与导出
- 模板管理（含管理员导入 zip 模板包）
- PDF 转 LaTeX（AI 辅助）
- Word/LaTeX 导出
- 图片资源上传与 URL 导入

项目采用前后端分离：

- 前端：Vue 3 + Vite
- 后端：Spring Boot 3 + MyBatis + MySQL


## 2. 技术栈与运行环境

### 2.1 后端

- Java 21
- Spring Boot 3.2.0
- MyBatis Spring Boot Starter 3.0.3
- MySQL（可配 H2）
- Redis（登录态/缓存相关能力）
- JWT（`jjwt`）
- PDFBox（PDF 解析）
- Apache POI（Word 处理）
- WebFlux（AI 请求）
- 邮件服务（SMTP）

### 2.2 前端

- Vue 3
- Vue Router 4
- Axios
- Vite 5
- CodeMirror 6
- pdfjs-dist

### 2.3 外部依赖

- TeX Live / MiKTeX（`pdflatex`、`xelatex`、`lualatex`）
- Pandoc（Word 导出）
- 可选：BibTeX（参考文献编译流程）


## 3. 代码结构

```text
LaTeW/
├─ frontend/                          # Vue 前端
│  ├─ src/
│  │  ├─ api/                         # 前端 API 封装
│  │  ├─ router/                      # 路由
│  │  ├─ utils/                       # 工具（鉴权、组件配置等）
│  │  └─ views/                       # 页面（Login/Projects/NewProject/Editor/Profile）
│  └─ package.json
├─ src/main/java/org/example/
│  ├─ controller/                     # REST API
│  ├─ service/                        # 业务接口
│  ├─ service/impl/                   # 业务实现
│  ├─ mapper/                         # MyBatis Mapper 接口
│  ├─ dto/                            # DTO
│  ├─ vo/                             # VO
│  ├─ config/                         # Web/JWT 等配置
│  └─ util/                           # LatexCompileUtil、WordExportUtil 等
├─ src/main/resources/
│  ├─ mapper/                         # MyBatis XML
│  └─ application.yml
├─ static/                            # 运行时资源（pdf/word/images/templates/avatars）
└─ docs/
```


## 4. 核心业务流程

### 4.1 登录与鉴权

1. 用户登录：`POST /api/users/login`
2. 返回 token 与用户信息
3. 前端在请求头中附带 `token`
4. 后端拦截器解析 token 并把 `userId` 注入 request attribute
5. 业务接口根据 `userId` 做权限校验

### 4.2 项目编辑与编译

1. 进入编辑器加载项目内容
2. 代码变化可触发自动编译
3. 后端编译时：
   - 创建临时目录
   - 预处理图片路径
   - 执行 `pdflatex/xelatex/lualatex`
   - 如检测到引用信息，自动执行 `bibtex + 二次/三次 latex`
   - 输出 PDF 到 `static/pdf`

### 4.3 模板导入（管理员）

1. 项目页右上角 `导入模板`（仅 admin 可见）
2. 需填写模板名称与描述，并上传 `.zip`
3. 后端解压到 `static/templates/bundle_xxx`
4. 对 zip 内 `.tex` 文件建立模板记录
5. 创建项目时会携带模板源路径标记，编译阶段复制依赖目录（`.cls/.sty/.bst/.bib`）

### 4.4 PDF 导入项目

1. 上传 PDF
2. 后端调用 AI 服务转换为 LaTeX
3. 生成项目并写入数据库


## 5. 前端页面说明

### 5.1 `Login/Register`

- 登录、注册、邮箱验证码

### 5.2 `Projects`

- 项目列表、搜索、删除、下载 PDF
- 头像入口
- admin：导入模板 zip

### 5.3 `NewProject`

- 空白创建
- 使用模板创建
- 导入 PDF 创建（AI 转换）

### 5.4 `Editor`

- CodeMirror 编辑
- 组件拖拽插入 LaTeX
- 编译器选择（pdfLaTeX / xeLaTeX / luaLaTeX）
- PDF 预览、分页与缩放
- 导出 PDF / Word / LaTeX
- AI 助手与编译错误分析

### 5.5 `Profile`

- 用户信息展示与头像上传


## 6. 后端 API 一览

以下为当前核心接口（实际以 Controller 为准）。

### 6.1 用户接口（`/api/users`）

- `POST /api/users`：注册
- `POST /api/users/login`：登录
- `POST /api/users/email/send-code`：发送邮箱验证码
- `GET /api/users/{userId}`：用户详情
- `PUT /api/users/{userId}/username`：修改用户名
- `POST /api/users/{userId}/avatar`：上传头像

### 6.2 项目接口（`/api/projects`）

- `POST /api/projects`：创建项目
- `GET /api/projects/user/{userId}`：用户项目列表
- `GET /api/projects/{id}`：项目详情
- `PUT /api/projects`：更新项目内容
- `DELETE /api/projects/{id}`：删除项目
- `POST /api/projects/{id}/compile`：编译
- `GET /api/projects/{id}/export-word`：导出 Word
- `GET /api/projects/{id}/export-latex`：导出 LaTeX
- `POST /api/projects/from-pdf`：PDF 转 LaTeX 并创建项目
- `POST /api/projects/{id}/images`：上传项目图片
- `POST /api/projects/{id}/images/from-url`：URL 导入图片

### 6.3 模板接口（`/api/templates`）

- `GET /api/templates`：全部模板
- `GET /api/templates/{id}`：模板详情
- `GET /api/templates/category/{category}`：按分类
- `GET /api/templates/system`：系统模板
- `POST /api/templates`：创建模板（admin）
- `POST /api/templates/import-zip`：导入 zip（admin）
- `PUT /api/templates/{id}`：更新模板
- `DELETE /api/templates/{id}`：删除模板

### 6.4 AI 接口（`/api/ai`）

- `POST /api/ai/pdf-to-latex`：PDF 转 LaTeX
- `POST /api/ai/process`：通用 AI 处理


## 7. 数据库设计（全部表信息，基于当前 Mapper/SQL）

本节按“当前代码真实访问到的表”整理。  
说明：项目存在历史遗留的 `documents` 相关 Mapper，当前主流程以 `project` 为核心。

### 7.1 `user`（用户表）

来源：`UserMapper.java`（注解 SQL）

- `user_id`：主键，用户 ID（PK）
- `username`：用户名（建议唯一）
- `email`：邮箱（建议唯一）
- `password_hash`：密码哈希
- `avatar_url`：头像地址
- `role`：角色（如 `user` / `admin`）

用途：

- 登录认证、角色鉴权（模板管理 admin 校验）
- 用户资料展示（头像、用户名）

关系：

- `user.user_id` 1:N `project.user_id`

### 7.2 `project`（项目表，核心业务表）

来源：`ProjectMapper.xml`

- `project_id`：主键（PK）
- `name`：项目名称
- `latex_content`：LaTeX 内容（长文本）
- `user_id`：所属用户 ID（FK -> `user.user_id`）
- `created_at`：创建时间
- `updated_at`：更新时间

用途：

- 新建/编辑/删除项目
- 编译输入源（编辑器内容）
- 导出 LaTeX/Word/PDF 的基础数据来源

关系：

- N:1 到 `user`
- 1:N 到 `compile_task.project_id`
- 1:N 到 `pdf_word_file.project_id`
- 1:N 到 `ai_log.project_id`

### 7.3 `template`（模板表）

来源：`TemplateMapper.xml`

- `template_id`：主键（PK）
- `name`：模板名称
- `description`：模板描述
- `template_path`：模板 tex 路径（磁盘路径）
- `preview_url`：模板预览图 URL
- `created_at`：创建时间

用途：

- 模板列表展示
- 模板创建/更新/删除
- zip 模板导入后持久化入口

说明：

- 服务层会将 `template_path` 读取为内容返回给前端；
- 分类、系统模板、使用次数等字段在当前数据库中可能不存在，服务层有兼容逻辑（默认值/幂等更新）。

### 7.4 `compile_task`（编译任务日志表）

来源：`CompileLogMapper.xml`

- `task_id`：主键（PK）
- `project_id`：项目 ID（FK -> `project.project_id`）
- `engine`：编译引擎信息（如 pdflatex/xelatex）
- `status`：编译状态（SUCCESS/WARNING/ERROR）
- `log`：编译日志全文
- `result_pdf_path`：输出 PDF 路径
- `created_at`：创建时间
- `completed_at`：完成时间（insert 中有写入）

用途：

- 查询某项目最近编译记录
- 失败排查与日志追踪

### 7.5 `pdf_word_file`（导出文件记录表）

来源：`PdfWordFileMapper.java`（注解 SQL）

- `pdf_id`：主键（PK）
- `project_id`：项目 ID（FK -> `project.project_id`）
- `filename`：文件名（含后缀）
- `file_path`：文件绝对路径/存储路径
- `uploaded_at`：记录创建时间

用途：

- 记录导出的 PDF/Word 文件
- 按项目 + 后缀查询历史并执行保留策略（超出数量删除旧文件）

### 7.6 `ai_log`（AI 调用日志表）

来源：`AILogMapper.java`（注解 SQL）

- `id`：主键（推断，具体以建表 SQL 为准）
- `project_id`：项目 ID（FK -> `project.project_id`）
- `request_type`：请求类型（如 CHAT/ERROR_ANALYSIS）
- `input_content`：输入内容
- `output_content`：输出内容
- `created_at`：创建时间

用途：

- 记录 AI 交互，用于审计与回溯

### 7.7 `documents`（历史文档表，兼容保留）

来源：`DocumentMapper.xml`

- `id`：主键（PK）
- `title`：文档标题
- `content`：LaTeX 内容
- `html_content`：HTML 内容
- `user_id`：用户 ID
- `template_id`：模板 ID
- `status`：文档状态
- `latex_compiler`：编译器类型
- `created_at`：创建时间
- `updated_at`：更新时间
- `last_compiled_at`：最后编译时间

用途：

- 旧版文档模型接口；当前主业务基本迁移到 `project`

### 7.8 关系总览（论文可直接引用）

- `user(user_id)` 1 --- N `project(user_id)`
- `project(project_id)` 1 --- N `compile_task(project_id)`
- `project(project_id)` 1 --- N `pdf_word_file(project_id)`
- `project(project_id)` 1 --- N `ai_log(project_id)`
- `template(template_id)` 与 `project` 在当前实现中主要为“模板内容注入”关系，非强外键耦合
- `documents` 为历史模型，可作为兼容或迁移对象


## 8. 配置说明（`application.yml`）

主要配置块：

- `spring.datasource`：数据库
- `spring.data.redis`：Redis
- `spring.mail`：邮件服务
- `jwt`：密钥与过期时间
- `avatar.upload`：头像路径
- `image.upload`：项目图片路径
- `latex.compile`：编译器与目录配置
- `word.export`：Pandoc 导出配置
- `volcengine.api`：AI 接口配置

> 安全建议：生产环境不要把数据库密码、邮箱授权码、API Key 明文写入仓库。  
> 建议改为环境变量或配置中心注入。


## 9. 本地开发启动

### 9.1 后端

```bash
# 在项目根目录
mvn clean package
java -jar target/LaTeW-1.0-SNAPSHOT.jar
```

默认端口：`8080`

### 9.2 前端

```bash
# 在 frontend 目录
npm install
npm run dev
```

默认端口：`3000`（视 Vite 配置）


## 10. 资源目录与清理建议

运行时会在以下目录写入文件：

- `static/pdf`：编译产物 PDF
- `static/word`：Word 导出
- `static/images`：项目图片
- `static/templates`：模板与 zip 解压包
- `static/avatars`：头像
- `temp/compile`：编译临时目录
- `temp/word`：Word 导出临时目录

建议：

- 定期清理历史产物（尤其 `static/pdf` 与 `temp/*`）
- 对上传目录做磁盘配额与备份策略


## 11. 模板（Springer/Nature）支持说明

为保证模板样式与 Overleaf 一致，当前链路已支持：

- zip 模板导入保留目录结构
- 编译时复制模板依赖（`.cls/.sty/.bst/.bib`）
- 基于模板源 tex 文件名输出 PDF（不再固定 `main.pdf`）
- BibTeX 检测后执行多轮编译

推荐模板包要求：

- 使用 Overleaf 的完整 `Source` zip
- `sn-jnl.cls`、`*.bib`、`*.bst`、图片文件齐全


## 12. 已知问题与排查

### 12.1 编译失败但日志无致命错误

- 检查输出 PDF 路径映射与静态资源映射
- 查看后端返回 `CompileResult.logContent`

### 12.2 样式与 Overleaf 不一致

- 确认模板是完整 zip 导入，而非只贴 `.tex`
- 确认文档类与依赖文件存在于同一模板包

### 12.3 引用未定义（`Citation ... undefined`）

- 确认 `.bib` 文件存在且 `\bibliography{...}` 正确
- 确认 BibTeX 运行成功（日志中应出现 `[bibtex]`）


## 13. 生产部署建议

- 使用 Nginx 反向代理前端与后端
- 上传目录挂载到持久化存储
- 配置日志采集（编译日志/请求日志）
- 对 AI 与导出接口做超时与限流
- token 密钥、数据库密码、邮件授权码全部改为环境变量


## 14. 后续迭代建议

- 增加 OpenAPI/Swagger 自动文档
- 增加数据库迁移（Flyway/Liquibase）
- 增加集成测试（编译链路与模板导入）
- 模板导入增加校验报告（依赖清单、主 tex 检测、可编译性预检查）


## 15. 论文写作专用素材（可直接用于毕业论文）

本节面向“用 AI 辅助完成毕业论文写作”，提供可直接改写或引用的结构化内容。  
建议将本节作为你的论文素材池，结合实际数据再润色。

### 15.1 课题背景（可改写）

随着高校科研产出规模增长，学术写作工具面临两个核心矛盾：  
一是 LaTeX 具备高质量排版能力，但学习门槛较高；二是可视化编辑易上手，但学术模板兼容性较弱。  
在此背景下，构建一个融合“结构化模板 + 在线编辑 + 自动编译 + AI 辅助”的论文写作平台具有实践价值。

LaTeW 项目针对上述痛点，重点解决以下问题：

- 初学者难以快速上手 LaTeX 命令与模板规范；
- 模板迁移过程中常出现依赖缺失导致样式失真；
- 编译错误定位成本高，反馈链路长；
- PDF/Word/LaTeX 跨格式输出过程繁琐且不稳定。

### 15.2 研究目标（可改写）

本系统设计目标如下：

- 实现面向论文场景的在线编辑与实时编译闭环；
- 提供模板化项目创建与模板依赖完整保留能力；
- 支持 AI 辅助错误分析与内容转换（PDF 转 LaTeX）；
- 支持 PDF、Word、LaTeX 多格式导出；
- 在可维护性、安全性、可扩展性上满足课程设计/毕业设计要求。

### 15.3 研究内容（可改写）

论文可将实现内容拆分为四个方向：

- **用户与权限子系统**：JWT 登录鉴权、基础用户信息管理。
- **论文项目子系统**：项目 CRUD、LaTeX 内容编辑、编译及预览。
- **模板子系统**：模板导入、模板管理、模板依赖编译支持。
- **AI 增强子系统**：错误分析、问答辅助、PDF 转 LaTeX。


## 16. 需求分析（论文第 2 章可用）

### 16.1 功能性需求

- 用户注册、登录、个人资料维护；
- 新建项目（空白 / 模板 / PDF 导入）；
- 在线编辑与自动保存；
- 编译与预览（支持 pdfLaTeX、xeLaTeX、luaLaTeX）；
- 导出 PDF/Word/LaTeX；
- 模板导入与管理（管理员）；
- AI 辅助分析与转换能力。

### 16.2 非功能性需求

- **可用性**：界面交互应直观，用户在 5 分钟内可完成首次项目创建。
- **性能**：普通篇幅文档编译应在可接受时延内返回结果（受硬件与 TeX 环境影响）。
- **可靠性**：编译失败需返回明确日志，便于定位错误。
- **安全性**：接口需鉴权；用户仅能访问自身项目；密钥与凭据应外置化。
- **可维护性**：采用分层结构（Controller / Service / Mapper），便于扩展。

### 16.3 角色与权限

- **普通用户（user）**
  - 可管理自身项目、编译与导出、使用模板创建项目。
- **管理员（admin）**
  - 具备模板创建、更新、删除及 zip 导入权限。


## 17. 系统总体设计（论文第 3 章可用）

### 17.1 架构设计

系统采用前后端分离架构：

- 前端基于 Vue 3 实现业务交互、状态展示与 API 调用；
- 后端基于 Spring Boot 提供 REST 服务与编译任务编排；
- MyBatis + MySQL 完成业务数据持久化；
- 本地文件系统承载模板、图片、编译产物等静态资源；
- 第三方 AI 服务提供文本生成与 PDF 转 LaTeX 能力。

### 17.2 分层设计

- **表示层（Controller）**：接收请求、参数校验、权限检查、统一响应。
- **业务层（Service）**：实现编译、模板导入、项目管理等核心业务逻辑。
- **数据层（Mapper/XML）**：执行 SQL，完成数据读写。
- **工具层（Util）**：LaTeX 编译、Word 导出、路径处理等通用能力。

### 17.3 关键模块与职责

- `ProjectController / ProjectServiceImpl`：项目管理与编译调度。
- `TemplateController / TemplateServiceImpl`：模板导入、模板查询与模板内容归一化。
- `LatexCompileUtil`：编译执行、图片预处理、BibTeX 多轮流程、模板依赖复制。
- `AIController / AIServiceImpl`：AI 问答与 PDF 转 LaTeX。


## 18. 关键实现细节（论文第 4 章可用）

### 18.1 模板依赖保留机制

针对 Springer/Nature 等模板依赖较多的问题，系统在模板导入时：

- 将 zip 完整解压至 `static/templates/bundle_xxx`；
- 对 `.tex` 文件建立模板记录，保留真实 `sourcePath`；
- 创建项目时把模板源路径写入内容标记；
- 编译阶段识别标记并复制整个 bundle 到临时目录，确保 `.cls/.sty/.bst/.bib` 可被定位。

此机制解决了“只复制 tex 导致样式不一致”的核心问题。

### 18.2 编译流程优化

编译流程不是单次 `pdflatex`，而是：

- 第一轮 latex 编译产生 `.aux`；
- 若检测到引用信息则执行 `bibtex`；
- 再执行两轮 latex 以收敛交叉引用和文献编号。

该流程与 Overleaf 的标准实践一致，能够减少引用未定义问题。

### 18.3 编译鲁棒性处理

系统在编译前对常见问题做预处理：

- `\includegraphics` 路径规范化与资源复制；
- 占位符图片路径容错；
- 按主 tex 文件名动态识别输出 PDF（避免固定 `main.pdf` 误判失败）。

### 18.4 权限控制策略

- 登录后 token 放入请求头；
- 后端通过拦截器解析 token，注入 `userId`；
- 业务接口通过 `userId` 校验项目读写权限；
- 模板导入/创建等敏感操作要求 admin 角色。


## 19. 数据设计补充（论文可直接引用）

### 19.1 实体关系说明（文字版 ER）

- 一个用户可拥有多个项目（`user` 1:N `project`）。
- 一个模板可被多个项目复用（逻辑关联，项目内容可来自模板）。
- 一个项目可产生多个导出文件（PDF/Word），用于历史留存与清理策略。
- 编译日志与项目存在关联，用于问题追踪。

### 19.2 字段设计原则

- 主键统一使用数值型 ID；
- 文本内容（LaTeX）使用长文本字段存储；
- 文件资源采用“数据库路径 + 文件系统实体”混合管理；
- 关键操作保留时间字段，便于排序与审计。


## 20. 测试与验证方案（论文第 5 章可用）

### 20.1 测试目标

- 验证核心流程可用性（创建、编辑、编译、导出）；
- 验证模板导入后样式保真能力；
- 验证权限控制正确性；
- 验证异常情况下系统可恢复性和错误可解释性。

### 20.2 功能测试用例建议

- **用户模块**
  - 正常登录、错误密码、未登录访问受限页面。
- **项目模块**
  - 创建项目、编辑保存、删除项目、越权访问项目。
- **编译模块**
  - 无错误 LaTeX、语法错误 LaTeX、含引用文献 LaTeX、含图片 LaTeX。
- **模板模块**
  - 导入完整模板包、导入不完整模板包、非 zip 上传、非 admin 导入。
- **导出模块**
  - PDF 导出、Word 导出、LaTeX 导出。

### 20.3 性能测试建议（可选）

- 指标：
  - 编译平均耗时（ms）
  - 95 分位耗时
  - 并发下成功率
- 场景：
  - 小文档（<5 页）、中型文档（10-20 页）、大文档（>30 页）

### 20.4 测试结果表格模板（可直接填）

```text
表 X-X 功能测试结果
| 编号 | 测试场景                 | 期望结果               | 实际结果 | 是否通过 |
|------|--------------------------|------------------------|----------|----------|
| T01  | 用户登录（正确账号密码） | 返回 token 并跳转项目页 |          |          |
| T02  | 项目编译（无错误文档）   | 返回 SUCCESS 且可预览 PDF |       |          |
| T03  | 模板导入（完整 zip）     | 成功创建模板并可编译     |          |          |
| T04  | 模板导入（缺 cls）       | 返回可理解错误信息       |          |          |
```


## 21. 创新点、局限与改进（论文第 6 章可用）

### 21.1 创新点（建议写法）

- 将模板导入、模板依赖复制、编译流程串联为闭环，提升复杂模板可用性；
- 引入 AI 能力用于 PDF 转 LaTeX 与错误分析，降低使用门槛；
- 面向学术写作场景构建“编辑-编译-导出”一体化流程。

### 21.2 当前局限

- 复杂模板兼容性仍受 TeX 环境和模板质量影响；
- AI 转换结果仍需人工校对；
- 部分高并发场景下编译队列与资源隔离能力有待增强。

### 21.3 后续改进方向

- 引入任务队列与编译沙箱隔离；
- 增加模板体检报告与自动修复建议；
- 增加 OpenAPI 自动文档与集成测试流水线；
- 优化大文档渲染与增量编译策略。


## 22. 论文写作模板（可直接复制）

### 22.1 摘要模板

本课题围绕学术论文在线写作场景，设计并实现了一个基于 Vue3 与 Spring Boot 的 LaTeX 写作排版系统。系统集成项目管理、模板导入、在线编辑、自动编译、AI 辅助转换与多格式导出等功能。针对复杂模板样式失真与依赖缺失问题，提出了基于模板包全量保留与编译目录依赖复制的处理方案。实验结果表明，系统能够在常见论文写作任务中稳定提供编辑与编译服务，并有效提升模板可用性与写作效率。

关键词：LaTeX；学术写作；模板编译；Spring Boot；Vue3；AI 辅助

### 22.2 “系统设计与实现”章节模板

- 4.1 系统总体架构设计  
- 4.2 用户与权限模块实现  
- 4.3 项目编辑与编译模块实现  
- 4.4 模板导入与依赖保留机制实现  
- 4.5 AI 辅助模块实现  
- 4.6 文件导入导出模块实现

### 22.3 “实验与分析”章节模板

- 5.1 测试环境与数据集说明  
- 5.2 功能测试结果  
- 5.3 性能测试结果  
- 5.4 典型问题与修复效果分析  
- 5.5 小结


## 23. AI 写论文提示词（高可用）

以下提示词建议直接复制到大模型，配合本项目代码与日志使用。

### 23.1 生成论文初稿（完整结构）

```text
你是一名软件工程毕业论文写作助手。请基于“LaTeW 学术写作排版系统”生成本科毕业论文初稿，包含：
摘要、关键词、绪论、需求分析、系统设计、系统实现、测试与结果、总结与展望、参考文献。
要求：
1) 使用学术中文，避免口语化；
2) 每章含小节标题；
3) 在“系统实现”中重点描述：模板导入与依赖保留、LaTeX 编译流程、BibTeX 多轮编译；
4) 给出可量化测试指标与表格样例；
5) 篇幅约 12000-15000 字。
```

### 23.2 根据代码自动补充“关键实现细节”

```text
请根据以下项目信息补写“关键实现细节”章节，要求结合工程实践：
- 后端：Spring Boot + MyBatis + LatexCompileUtil
- 模板导入：zip 解压到 bundle 目录，保留 .cls/.sty/.bst/.bib
- 编译：pdflatex / xelatex / lualatex，必要时 bibtex + 两轮 latex
- 前端：Vue3，新建项目支持模板创建与 PDF 导入
输出格式：按“问题 -> 方案 -> 实现 -> 效果”组织。
```

### 23.3 生成“测试章节”

```text
请为毕业论文生成“系统测试与结果分析”章节，包含：
1) 测试目标；
2) 测试环境（硬件、软件、依赖）；
3) 功能测试用例表；
4) 性能测试指标与结果表；
5) 典型故障案例（模板缺依赖、引用未定义、编译失败）与修复分析。
语言要求：正式、学术、可直接用于论文正文。
```


## 24. 论文提交前检查清单

- [ ] 论文中的系统功能与代码实现一致；
- [ ] API、模块名、技术栈版本与项目真实配置一致；
- [ ] 测试结果有数据支撑（截图/日志/表格）；
- [ ] 参考文献格式统一；
- [ ] 不泄露敏感信息（密码、密钥、授权码）；
- [ ] 附录包含核心流程图、关键代码说明与部署说明。


