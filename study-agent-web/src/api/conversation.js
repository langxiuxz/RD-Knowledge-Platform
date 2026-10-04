import request from './request'

export const listConversations = (params) => request.get('/conversations', { params })
export const createConversation = (payload) => request.post('/conversations', payload)
