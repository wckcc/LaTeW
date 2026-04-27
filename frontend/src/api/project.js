import request from '../utils/request'
import axios from 'axios'
import { getToken } from '../utils/auth'

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
  // 将项目ID添加到请求数据中
  const data = { ...projectData, id }
  return request({
    url: '/projects',
    method: 'put',
    data: data
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

/**
 * 编译项目为PDF
 * @param {Number} id - 项目ID
 * @param {String} compiler - 编译器类型（pdflatex, xelatex, lualatex）
 * @returns {Promise}
 */
export function compileProject(id, compiler = 'pdflatex') {
  return request({
    url: `/projects/${id}/compile`,
    method: 'post',
    data: { compiler }
  })
}

/**
 * 从PDF文件创建项目
 * @param {File} file - PDF文件
 * @param {String} projectName - 项目名称
 * @returns {Promise}
 */
export function createProjectFromPdf(file, projectName) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('name', projectName)
  
  return request({
    url: '/projects/from-pdf',
    method: 'post',
    data: formData,
    timeout: 120000 // 文件上传和AI处理可能需要更长时间，设置为120秒
  })
}

/**
 * 上传项目图片资源（用于 LaTeX includegraphics）
 * @param {Number} id - 项目ID
 * @param {File} file - 图片文件
 * @returns {Promise}
 */
export function uploadProjectImage(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: `/projects/${id}/images`,
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 通过URL上传项目图片资源
 * @param {Number} id - 项目ID
 * @param {String} imageUrl - 图片URL
 * @returns {Promise}
 */
export function uploadProjectImageByUrl(id, imageUrl) {
  const formData = new FormData()
  formData.append('imageUrl', imageUrl)
  return request({
    url: `/projects/${id}/images/from-url`,
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 导出项目为Word文档（docx）
 * @param {Number} id - 项目ID
 * @returns {Promise<Blob>}
 */
export function exportProjectWord(id) {
  const token = getToken()
  return axios({
    url: `/api/projects/${id}/export-word`,
    method: 'get',
    responseType: 'blob',
    headers: token ? { token } : {}
  })
}

/**
 * 导出项目为LaTeX源文件（tex）
 * @param {Number} id - 项目ID
 * @returns {Promise<Blob>}
 */
export function exportProjectLatex(id) {
  const token = getToken()
  return axios({
    url: `/api/projects/${id}/export-latex`,
    method: 'get',
    responseType: 'blob',
    headers: token ? { token } : {}
  })
}

