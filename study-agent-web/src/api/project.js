import request from './request'

export const listProjects = (params) => request.get('/projects', { params })
export const createProject = (payload) => request.post('/projects', payload)
export const getProject = (id) => request.get(`/projects/${id}`)
export const updateProject = (id, payload) => request.put(`/projects/${id}`, payload)
export const archiveProject = (id) => request.post(`/projects/${id}/archive`)
export const listProjectMembers = (id, params) => request.get(`/projects/${id}/members`, { params })
export const addProjectMember = (id, payload) => request.post(`/projects/${id}/members`, payload)
export const changeProjectMemberRole = (projectId, userId, payload) =>
  request.put(`/projects/${projectId}/members/${userId}`, payload)
export const removeProjectMember = (projectId, userId) =>
  request.delete(`/projects/${projectId}/members/${userId}`)
export const listProjectAuditLogs = (id, params) => request.get(`/projects/${id}/audit-logs`, { params })
