import api from '../api/request'

/** 下载 /api/exports/** 的 CSV 文件（带 token）。 */
export async function downloadExportCsv(path, params = {}, filename = 'export.csv') {
  const res = await api.get(path, { params, responseType: 'blob' })
  const blob = res instanceof Blob ? res : new Blob([res], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename.endsWith('.csv') ? filename : `${filename}.csv`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
