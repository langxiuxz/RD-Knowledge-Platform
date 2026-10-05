import request from './request'

export const feedbackMessage = (id, payload) => request.post(`/messages/${id}/feedback`, payload)
