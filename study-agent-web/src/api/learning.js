import request from './request'

export const createLearningRecord = (payload) => request.post('/learning/records', payload)
export const listLearningRecords = (params) => request.get('/learning/records', { params })
export const getLearningProgress = (params) => request.get('/learning/progress', { params })
