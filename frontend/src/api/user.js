import request from '../utils/request'

/**
 * 用户登录
 * @param {Object} loginData - 登录数据 { username, password }
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
 * @param {Object} userData - 用户数据 { username, phone?, password? }
 * @returns {Promise}
 */
export function createUser(userData) {
  return request({
    url: '/users',
    method: 'post',
    data: userData
  })
}

