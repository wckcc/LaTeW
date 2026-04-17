import request from '../utils/request'

/**
 * 用户登录
 * @param {Object} loginData - 登录数据 { email, code }
 * @returns {Promise}
 */
export function login(loginData) {
  return request({
    url: '/users/login',
    method: 'post',
    data: loginData
  })
}

/**
 * 创建用户（注册）
 * @param {Object} userData - 注册数据 { username?, email, code, password? }
 * @returns {Promise}
 */
export function createUser(userData) {
  return request({
    url: '/users',
    method: 'post',
    data: userData
  })
}

/**
 * 发送邮箱验证码（写入 Redis）
 * @param {string} email
 * @returns {Promise}
 */
export function sendEmailCode(email) {
  return request({
    url: '/users/email/send-code',
    method: 'post',
    data: { email }
  })
}

/**
 * 上传用户头像
 * @param {number} userId - 用户ID
 * @param {File} file - 头像文件
 * @returns {Promise}
 */
export function uploadAvatar(userId, file) {
  const formData = new FormData()
  formData.append('file', file)
  
  return request({
    url: `/users/${userId}/avatar`,
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 获取用户信息
 * @param {number} userId - 用户ID
 * @returns {Promise}
 */
export function getUserById(userId) {
  return request({
    url: `/users/${userId}`,
    method: 'get'
  })
}

