import request from './request'

export const listQuizzes = (params) => request.get('/quizzes', { params })
export const generateQuiz = (payload) => request.post('/quizzes/generate', payload)
export const getQuiz = (id) => request.get(`/quizzes/${id}`)
export const submitQuiz = (id, payload) => request.post(`/quizzes/${id}/submit`, payload)
export const getQuizResult = (id) => request.get(`/quizzes/${id}/result`)
export const listWrongQuestions = (params) => request.get('/wrong-questions', { params })
export const updateWrongQuestionMastered = (id, mastered) => request.put(`/wrong-questions/${id}/mastered`, { mastered })
export const deleteWrongQuestion = (id) => request.delete(`/wrong-questions/${id}`)
