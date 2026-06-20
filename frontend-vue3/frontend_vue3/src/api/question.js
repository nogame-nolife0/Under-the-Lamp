import request from '@/utils/request'

export function pageQuestions(params) {
  return request.get('/questions', { params })
}

export function listCourseNames() {
  return request.get('/questions/subjects')
}

export function getQuestion(id) {
  return request.get(`/questions/${id}`)
}

export function batchDeleteQuestions(ids) {
  return request.post('/questions/batch-delete', { ids })
}

export function syncQuestionEmbed(batchSize = 50) {
  return request.post('/questions/sync-embed', null, { params: { batchSize } })
}

export function batchUpdateSubject(ids, subject) {
  return request.post('/questions/batch-update-subject', { ids, subject })
}

export function updateQuestion(id, data) {
  return request.put(`/questions/${id}`, data)
}

export function uploadQuestionImage(id, scope, index, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/questions/${id}/images/${scope}/${index}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
