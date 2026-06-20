import request from '@/utils/request'

export function uploadWord(stemFiles, answerFiles, subject) {
  const formData = new FormData()
  formData.append('subject', subject)

  const stems = Array.isArray(stemFiles) ? stemFiles : (stemFiles ? [stemFiles] : [])
  stems.forEach((item) => {
    if (item) formData.append('stemFiles', item)
  })

  const answers = Array.isArray(answerFiles) ? answerFiles : (answerFiles ? [answerFiles] : [])
  answers.forEach((item) => {
    if (item) formData.append('answerFiles', item)
  })

  return request.post('/import/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function updateImportBatch(batchUuid, data) {
  return request.put(`/import/batches/${batchUuid}`, data)
}

export function getBatch(batchUuid) {
  return request.get(`/import/batches/${batchUuid}`)
}

export function getBatchItems(batchUuid, status) {
  return request.get(`/import/batches/${batchUuid}/items`, {
    params: { status },
  })
}

export function updateImportItem(id, data) {
  return request.put(`/import/items/${id}`, data)
}

export function acceptImportItem(id) {
  return request.post(`/import/items/${id}/accept`)
}

export function rejectImportItem(id) {
  return request.post(`/import/items/${id}/reject`)
}

export function confirmBatch(batchUuid) {
  return request.post(`/import/batches/${batchUuid}/confirm`)
}
