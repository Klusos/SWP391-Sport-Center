export function shouldShowNotificationBell(pathname, auth) {
  return Boolean(auth?.accessToken && pathname !== "/");
}
