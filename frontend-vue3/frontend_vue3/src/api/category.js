import request from '@/utils/request'

export function listCategories(type, parentId) {
  return request.get('/categories', {
    params: { type, parentId },
  })
}
