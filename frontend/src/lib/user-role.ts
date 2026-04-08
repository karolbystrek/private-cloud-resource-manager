export const USER_ROLES = ['STUDENT', 'EMPLOYEE', 'ADMIN'] as const;

export type UserRole = (typeof USER_ROLES)[number];

export function isUserRole(value: string | null | undefined): value is UserRole {
  if (!value) {
    return false;
  }
  return USER_ROLES.includes(value as UserRole);
}
