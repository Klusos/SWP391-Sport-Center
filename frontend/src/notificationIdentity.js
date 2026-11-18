export function notificationIdentityKey(auth) {
  if (!auth) return "anonymous";
  const userId = auth.userId == null ? "" : String(auth.userId);
  const identity = userId ? `user:${userId}` : `token:${String(auth.accessToken ?? "")}`;
  return `${auth.role ?? "unknown"}:${identity}`;
}
