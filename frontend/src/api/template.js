import request from '../utils/request'

/**
 * 获取所有模板
 * @returns {Promise}
 */
export function getAllTemplates() {
  return request({
    url: '/templates',
    method: 'get'
  })
}

/**
 * 根据ID获取模板
 * @param {Number} id - 模板ID
 * @returns {Promise}
 */
export function getTemplateById(id) {
  return request({
    url: `/templates/${id}`,
    method: 'get'
  })
}

/**
 * 根据分类获取模板
 * @param {String} category - 分类
 * @returns {Promise}
 */
export function getTemplatesByCategory(category) {
  return request({
    url: `/templates/category/${category}`,
    method: 'get'
  })
}

/**
 * 获取系统模板
 * @returns {Promise}
 */
export function getSystemTemplates() {
  return request({
    url: '/templates/system',
    method: 'get'
  })
}

