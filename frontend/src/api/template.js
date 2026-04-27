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

/**
 * 创建模板
 * @param {Object} templateData - 模板数据
 * @returns {Promise}
 */
export function createTemplate(templateData) {
  return request({
    url: '/templates',
    method: 'post',
    data: templateData
  })
}

/**
 * 导入模板 zip 包（管理员）
 * @param {File} file - zip 文件
 * @param {String} name - 模板名称
 * @param {String} description - 模板描述
 * @returns {Promise}
 */
export function importTemplatesZip(file, name, description) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('name', name || '')
  formData.append('description', description || '')
  return request({
    url: '/templates/import-zip',
    method: 'post',
    data: formData
  })
}

