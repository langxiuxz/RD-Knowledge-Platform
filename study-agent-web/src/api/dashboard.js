import request from './request'

export const getDashboardOverview = (params) => request.get('/dashboard/overview', { params })
export const getDashboardTrend = (params) => request.get('/dashboard/trend', { params })
export const getWeakTopics = (params) => request.get('/dashboard/weak-topics', { params })
