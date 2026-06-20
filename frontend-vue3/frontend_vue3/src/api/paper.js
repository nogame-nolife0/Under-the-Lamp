import axios from 'axios'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

export function createPaper(data) {
  return request.post('/papers', data)
}

export function smartPreviewPaper(data) {
  return request.post('/papers/smart/preview', data)
}

export function smartAlternatives(data) {
  return request.post('/papers/smart/alternatives', data)
}

export function smartComposePaper(data) {
  return request.post('/papers/smart', data)
}

export function pagePapers(params) {
  return request.get('/papers', { params })
}

export function getPaper(id) {
  return request.get(`/papers/${id}`)
}

export function batchDeletePapers(ids) {
  return request.post('/papers/batch-delete', { ids })
}

async function parseBlobError(blob) {
  try {
    const text = await blob.text()
    const json = JSON.parse(text)
    return json.msg || '导出失败'
  } catch {
    return '导出失败，返回内容不是有效的 Word 文件'
  }
}

function isDocxBlob(blob) {
  const type = blob.type || ''
  if (type.includes('json') || type.includes('text/html')) {
    return false
  }
  return true
}

async function validateDocxBlob(blob) {
  if (!blob || blob.size < 4) {
    throw new Error('导出失败，文件为空')
  }

  if (!isDocxBlob(blob)) {
    throw new Error(await parseBlobError(blob))
  }

  const header = await blob.slice(0, 2).arrayBuffer()
  const bytes = new Uint8Array(header)
  if (bytes[0] !== 0x50 || bytes[1] !== 0x4b) {
    throw new Error(await parseBlobError(blob))
  }
}

export async function exportPaper(paperId, exportType) {
  try {
    const token = localStorage.getItem('pg_token')
    const response = await axios.post(
      `/api/papers/${paperId}/export`,
      null,
      {
        params: { exportType },
        responseType: 'blob',
        timeout: 180000,
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      }
    )

    const blob = response.data
    await validateDocxBlob(blob)

    const disposition = response.headers['content-disposition'] || ''
    let fileName = exportType === 'TEACHER' ? '试卷_教师版.docx' : '试卷_学生版.docx'
    const match = disposition.match(/filename\*=UTF-8''(.+)/i)
    if (match?.[1]) {
      fileName = decodeURIComponent(match[1])
    }

    const docxBlob = new Blob([blob], {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    const url = window.URL.createObjectURL(docxBlob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (error) {
    const blob = error.response?.data
    let msg = error.message || '导出失败'
    if (blob instanceof Blob) {
      msg = await parseBlobError(blob)
    }
    ElMessage.error(msg)
    throw error
  }
}
