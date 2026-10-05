import request from './request'

export const login = (payload) => request.post('/auth/login', payload)
export const register = (payload) => request.post('/auth/register', payload)
export const refresh = (refreshToken) => request.post('/auth/refresh', { refreshToken })
export const logout = () => request.post('/auth/logout')
export const getCurrentUser = () => request.get('/users/me')
export const updateProfile = (payload) => request.put('/users/me', payload)
export const changePassword = (payload) => request.put('/users/me/password', payload)
