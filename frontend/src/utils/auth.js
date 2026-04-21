const TOKEN_KEY = 'latew_token'
const USER_KEY = 'latew_user'

// 保存token
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

// 获取token
export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

// 判断当前是否为有效登录态（仅本地校验）
export function isAuthenticated() {
  const token = getToken()
  const user = getUser()
  return Boolean(token && user && user.userId)
}

// 移除token
export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

// 保存用户信息
export function setUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

// 获取用户信息
export function getUser() {
  const userStr = localStorage.getItem(USER_KEY)
  return userStr ? JSON.parse(userStr) : null
}

// 移除用户信息
export function removeUser() {
  localStorage.removeItem(USER_KEY)
}

// 清除所有认证信息
export function clearAuth() {
  removeToken()
  removeUser()
}

