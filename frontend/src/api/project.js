import request from '../utils/request'

/**
 * 创建项目
 * @param {Object} projectData - 项目数据
 * @returns {Promise}
 */
export function createProject(projectData) {
  return request({
    url: '/projects',
    method: 'post',
    data: projectData
  })
}

/**
 * 根据ID获取项目
 * @param {Number} id - 项目ID
 * @returns {Promise}
 */
export function getProjectById(id) {
  return request({
    url: `/projects/${id}`,
    method: 'get'
  })
}

/**
 * 根据用户ID获取项目列表
 * @param {Number} userId - 用户ID
 * @returns {Promise}
 */
export function getProjectsByUser(userId) {
  return request({
    url: `/projects/user/${userId}`,
    method: 'get'
  })
}

/**
 * 获取所有项目
 * @returns {Promise}
 */
export function getAllProjects() {
  return request({
    url: '/projects',
    method: 'get'
  })
}

/**
 * 更新项目
 * @param {Number} id - 项目ID
 * @param {Object} projectData - 项目数据
 * @returns {Promise}
 */
export function updateProject(id, projectData) {
  return request({
    url: `/projects/${id}`,
    method: 'put',
    data: projectData
  })
}

/**
 * 删除项目
 * @param {Number} id - 项目ID
 * @returns {Promise}
 */
export function deleteProject(id) {
  return request({
    url: `/projects/${id}`,
    method: 'delete'
  })
}

