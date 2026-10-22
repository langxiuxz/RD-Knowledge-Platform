import request from './request'

export const listKnowledgeBases = (projectId, params) => request.get(`/projects/${projectId}/knowledge-bases`, { params })
export const createKnowledgeBase = (projectId, payload) => request.post(`/projects/${projectId}/knowledge-bases`, payload)
export const getKnowledgeBase = (id) => request.get(`/knowledge-bases/${id}`)
export const updateKnowledgeBase = (id, payload) => request.put(`/knowledge-bases/${id}`, payload)
export const archiveKnowledgeBase = (id) => request.post(`/knowledge-bases/${id}/archive`)
export const listDocuments = (knowledgeBaseId, params) => request.get(`/knowledge-bases/${knowledgeBaseId}/documents`, { params })
export const uploadDocument = (knowledgeBaseId, { title, changeNote, file }) => {
  const body = new FormData()
  body.append('title', title)
  if (changeNote) body.append('changeNote', changeNote)
  body.append('file', file)
  return request.post(`/knowledge-bases/${knowledgeBaseId}/documents`, body, { timeout: 60000 })
}
export const getDocument = (id) => request.get(`/documents/${id}`)
export const uploadVersion = (id, { changeNote, file }) => {
  const body = new FormData()
  if (changeNote) body.append('changeNote', changeNote)
  body.append('file', file)
  return request.post(`/documents/${id}/versions`, body, { timeout: 60000 })
}
export const getVersionStatus = (id) => request.get(`/document-versions/${id}/status`)
export const reprocessVersion = (id) => request.post(`/document-versions/${id}/reprocess`)
export const submitReview = (id) => request.post(`/document-versions/${id}/submit-review`)
export const reviewVersion = (id, payload) => request.post(`/document-versions/${id}/review`, payload)
export const publishVersion = (id) => request.post(`/document-versions/${id}/publish`)
export const listSnapshots = (id, params) => request.get(`/knowledge-bases/${id}/snapshots`, { params })
export const getSnapshot = (id) => request.get(`/knowledge-snapshots/${id}`)
