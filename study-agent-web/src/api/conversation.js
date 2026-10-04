import request from './request'

export const listConversations = (params) => request.get('/conversations', { params })
export const createConversation = (payload) => request.post('/conversations', payload)
export const getConversation = (id) => request.get(`/conversations/${id}`)
export const updateConversation = (id, payload) => request.put(`/conversations/${id}`, payload)
export const deleteConversation = (id) => request.delete(`/conversations/${id}`)
export const listMessages = (id, params) => request.get(`/conversations/${id}/messages`, { params })
export const sendMessage = (id, payload) => request.post(`/conversations/${id}/messages`, payload)
export const stopGeneration = (id, traceId) =>
  request.delete(`/conversations/${id}/generation/${encodeURIComponent(traceId)}`)
