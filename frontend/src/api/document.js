import request from '../utils/request'

/**
 * 创建文档
 * @param {Object} documentData - 文档数据
 * @returns {Promise}
 */
export function createDocument(documentData) {
  return request({
    url: '/documents',
    method: 'post',
    data: documentData
  })
}

/**
 * 根据ID获取文档
 * @param {Number} id - 文档ID
 * @returns {Promise}
 */
export function getDocumentById(id) {
  return request({
    url: `/documents/${id}`,
    method: 'get'
  })
}

/**
 * 根据用户ID获取文档列表
 * @param {Number} userId - 用户ID
 * @returns {Promise}
 */
export function getDocumentsByUser(userId) {
  return request({
    url: `/documents/user/${userId}`,
    method: 'get'
  })
}

/**
 * 获取所有文档
 * @returns {Promise}
 */
export function getAllDocuments() {
  return request({
    url: '/documents',
    method: 'get'
  })
}

/**
 * 更新文档
 * @param {Number} id - 文档ID
 * @param {Object} documentData - 文档数据
 * @returns {Promise}
 */
export function updateDocument(id, documentData) {
  return request({
    url: `/documents/${id}`,
    method: 'put',
    data: documentData
  })
}

/**
 * 删除文档
 * @param {Number} id - 文档ID
 * @returns {Promise}
 */
export function deleteDocument(id) {
  return request({
    url: `/documents/${id}`,
    method: 'delete'
  })
}

/**
 * 导入LaTeX文件
 * @param {FormData} formData - 包含文件和用户ID的表单数据
 * @returns {Promise}
 */
export function importLaTeXFile(formData) {
  return request({
    url: '/editor/import/latex',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

