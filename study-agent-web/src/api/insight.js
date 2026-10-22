import request from './request'

export const getInsightOverview = projectId => request.get(`/projects/${projectId}/insights/overview`)
export const getInsightAssignments = projectId => request.get(`/projects/${projectId}/insights/assignments`)
export const getKnowledgeGaps = projectId => request.get(`/projects/${projectId}/insights/knowledge-gaps`)
export const getContentImpact = (projectId, params = {}) => request.get(`/projects/${projectId}/insights/content-impact`, { params })
export const getPendingReviews = projectId => request.get(`/projects/${projectId}/insights/pending-reviews`)
export const reviewQuizAttempt = (id, payload) => request.post(`/quiz-attempts/${id}/review`, payload)
export const reviewDrillRun = (id, payload) => request.post(`/drill-runs/${id}/review`, payload)
export const reviewAssignment = (id, payload) => request.post(`/assignments/${id}/review`, payload)
export const generateCapabilityReport = id => request.post(`/assignments/${id}/capability-report/generate`)
export const getCapabilityReport = id => request.get(`/capability-reports/${id}`)
