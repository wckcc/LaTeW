import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import Projects from '../views/Projects.vue'
import NewProject from '../views/NewProject.vue'
import Editor from '../views/Editor.vue'
import Profile from '../views/Profile.vue'
import { isAuthenticated } from '../utils/auth'
const routes = [
  {
    path: '/',
    redirect: '/projects'
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/register',
    name: 'Register',
    component: Register
  },
  {
    path: '/projects',
    name: 'Projects',
    component: Projects,
    meta: { requiresAuth: true }
  },
  {
    path: '/new-project',
    name: 'NewProject',
    component: NewProject,
    meta: { requiresAuth: true }
  },
  {
    path: '/editor/:id',
    name: 'Editor',
    component: Editor,
    meta: { requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: Profile,
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const authed = isAuthenticated()
  
  if (to.meta.requiresAuth && !authed) {
    // 需要登录但未登录，跳转到登录页
    next('/login')
  } else if (to.path === '/login' && authed) {
    // 已登录，访问登录页时跳转到项目管理页
    next('/projects')
  } else {
    next()
  }
})

export default router

