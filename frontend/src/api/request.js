import axios from 'axios'

const api = axios.create({ baseURL: '/api', timeout: 15000 })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  config.headers['X-Request-Id'] = crypto.randomUUID().replaceAll('-', '')
  return config
})

api.interceptors.response.use(
  (resp) => {
    const data = resp.data
    // 文件下载（blob / arraybuffer）原样返回，不走统一业务包装
    if (resp.config?.responseType === 'blob' || resp.config?.responseType === 'arraybuffer') {
      return data
    }
    if (data && data.code !== 200) {
      const rid = resp.headers?.['x-request-id']
      return Promise.reject(new Error((data.msg || '请求失败') + (rid ? ` [${rid}]` : '')))
    }
    return data
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('token')
      location.hash = '#/login'
    }
    const rid = err.response?.headers?.['x-request-id']
    if (rid) err.requestId = rid
    return Promise.reject(err)
  }
)

export default api
