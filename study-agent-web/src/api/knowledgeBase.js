import request from './request'

export const listKnowledgeBases = (params) => request.get('/knowledge-bases', { params })
export const createKnowledgeBase = (payload) => request.post('/knowledge-bases', payload)
export const getKnowledgeBase = (id) => request.get(`/knowledge-bases/${id}`)
export const updateKnowledgeBase = (id, payload) => request.put(`/knowledge-bases/${id}`, payload)
export const deleteKnowledgeBase = (id) => request.delete(`/knowledge-bases/${id}`)
export const listDocuments = (knowledgeBaseId, params) => request.get(`/knowledge-bases/${knowledgeBaseId}/documents`, { params })
export const uploadDocument = (knowledgeBaseId, file) => {
  const body = new FormData()
  body.append('file', file)
  return request.post(`/knowledge-bases/${knowledgeBaseId}/documents`, body, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}
export const getDocumentStatus = (id) => request.get(`/documents/${id}/status`)
export const reprocessDocument = (id) => request.post(`/documents/${id}/reprocess`)
export const deleteDocument = (id) => request.delete(`/documents/${id}`)
