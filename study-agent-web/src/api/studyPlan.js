import request from './request'

export const generateStudyPlan = (payload) => request.post('/study-plans/generate', payload)
export const listStudyPlans = (params) => request.get('/study-plans', { params })
export const getStudyPlan = (id) => request.get(`/study-plans/${id}`)
export const updateStudyPlanTask = (planId, taskId, completed) =>
  request.put(`/study-plans/${planId}/tasks/${taskId}`, { completed })
export const deleteStudyPlan = (id) => request.delete(`/study-plans/${id}`)
