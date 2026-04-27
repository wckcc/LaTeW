import request from '../utils/request'

/**
 * AI 助手处理请求
 * @param {Object} payload - { content, type }
 * @returns {Promise}
 */
export function processWithAI(payload) {
  return request({
    url: '/ai/process',
    method: 'post',
    data: payload,
    timeout: 120000
  })
}

