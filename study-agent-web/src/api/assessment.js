import request from './request'

export const generateProjectQuiz = (projectId, payload) =>
  request.post(`/projects/${projectId}/quizzes/generate`, payload, { timeout: 60000 })
export const listProjectQuizzes = (projectId, params) =>
  request.get(`/projects/${projectId}/quizzes`, { params })
export const getProjectQuiz = id => request.get(`/quizzes/${id}`)
export const publishProjectQuiz = id => request.post(`/quizzes/${id}/publish`)
export const startQuizAttempt = (id, payload = {}) => request.post(`/quizzes/${id}/attempts`, payload, { timeout: 60000 })
export const submitQuizAttempt = (id, payload) => request.post(`/quiz-attempts/${id}/submit`, payload, { timeout: 60000 })
export const getQuizAttempt = id => request.get(`/quiz-attempts/${id}`)

export const listDrillScenarios = (projectId, status) =>
  request.get(`/projects/${projectId}/drill-scenarios`, { params: { status } })
export const createDrillScenario = (projectId, payload) =>
  request.post(`/projects/${projectId}/drill-scenarios`, payload)
export const publishDrillScenario = id => request.post(`/drill-scenarios/${id}/publish`)
export const startDrillRun = (id, payload = {}) => request.post(`/drill-scenarios/${id}/runs`, payload)
export const submitDrillAction = (id, payload) => request.post(`/drill-runs/${id}/actions`, payload, { timeout: 60000 })
export const submitDrillRun = (id, payload) => request.post(`/drill-runs/${id}/submit`, payload, { timeout: 60000 })
export const getDrillRun = id => request.get(`/drill-runs/${id}`)
