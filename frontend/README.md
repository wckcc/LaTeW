# LaTeW 前端项目

Vue 3 + Vite 构建的前端应用

## 功能特性

- ✅ 用户登录
- ✅ 用户注册（创建账号）
- ✅ Token 认证管理
- ✅ 响应式设计

## 技术栈

- Vue 3
- Vue Router 4
- Axios
- Vite

## 安装依赖

```bash
npm install
```

## 开发运行

```bash
npm run dev
```

前端服务将在 http://localhost:3000 启动

## 构建生产版本

```bash
npm run build
```

## API 接口

### 用户登录
- **接口**: `POST /api/users/login`
- **请求体**: `{ username: string, password: string }`
- **响应**: `{ code: 200, message: "登录成功", data: { token, userId, username } }`

### 创建用户（注册）
- **接口**: `POST /api/users`
- **请求体**: `{ username: string, phone?: string }`
- **响应**: `{ code: 200, message: "用户创建成功", data: { id, username, phone } }`

## 项目结构

```
frontend/
├── src/
│   ├── api/          # API 接口封装
│   │   └── user.js   # 用户相关接口
│   ├── assets/       # 静态资源
│   │   └── css/      # 样式文件
│   ├── router/       # 路由配置
│   │   └── index.js
│   ├── utils/        # 工具函数
│   │   ├── auth.js   # 认证相关工具
│   │   └── request.js # Axios 封装
│   ├── views/        # 页面组件
│   │   ├── Login.vue    # 登录页面
│   │   └── Register.vue # 注册页面
│   ├── App.vue       # 根组件
│   └── main.js       # 入口文件
├── index.html        # HTML 模板
├── package.json      # 项目配置
├── vite.config.js    # Vite 配置
└── README.md         # 项目说明
```

## 注意事项

1. 确保后端服务运行在 `http://localhost:8080`
2. 注册时密码默认为 `123456`，登录后可以修改
3. Token 存储在 localStorage 中，key 为 `latew_token`
4. 用户信息存储在 localStorage 中，key 为 `latew_user`

