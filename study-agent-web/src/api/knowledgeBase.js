import request from './request'

export const listKnowledgeBases = (params) => request.get('/knowledge-bases', { params })
export const createKnowledgeBase = (payload) => request.post('/knowledge-bases', payload)
