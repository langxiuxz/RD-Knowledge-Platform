import request from './request'

export const uploadFile = (file, type = 'AVATAR') => {
  const form = new FormData()
  form.append('file', file)
  form.append('type', type)
  return request.post('/files/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}
