export const ROLE = Object.freeze({
  USER: 'user',
  ADMIN: 'admin',
  SUPER_ADMIN: 'super_admin',
})

export function roleLabel(role) {
  switch (role) {
    case ROLE.SUPER_ADMIN: return '超级管理员'
    case ROLE.ADMIN: return '管理员'
    default: return '普通用户'
  }
}
