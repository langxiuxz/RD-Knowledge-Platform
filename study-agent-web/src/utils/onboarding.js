export const isPathManager = (project) => project?.status === 'ACTIVE' && ['OWNER', 'MAINTAINER'].includes(project?.myRole)
export const isExecutable = (assignment, userId) => userId != null && assignment?.assigneeUserId === userId && ['ASSIGNED', 'IN_PROGRESS'].includes(assignment?.status)
export const canCompleteTask = (assignment, task, userId) => isExecutable(assignment, userId) && task?.completionRule === 'MANUAL' && ['READ', 'CHECKPOINT'].includes(task.type) && ['PENDING', 'IN_PROGRESS'].includes(task.status)
export const canSubmitAssignment = (assignment, tasks, userId) => isExecutable(assignment, userId) && assignment.status === 'IN_PROGRESS' && tasks.length > 0 && tasks.every(task => !task.required || task.status === 'COMPLETED')
export function taskResource(type) {
  return { READ: ['DOCUMENT_VERSION', 'MANUAL'], CHECKPOINT: ['NONE', 'MANUAL'], QUIZ: ['QUIZ', 'AUTO'], DRILL: ['DRILL', 'AUTO'] }[type]
}
