import request from './request'

export const listQuizzes = (params) => request.get('/quizzes', { params })
