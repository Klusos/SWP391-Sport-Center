export const AUTH_STORAGE_KEY = "sportcenter.auth";
const LEGACY_SESSION_AUTH_KEY = AUTH_STORAGE_KEY;
const VALID_ROLES = new Set(["ADMIN", "RECEPTIONIST", "COACH", "MEMBER"]);

export function isValidAuth(value) {
  return Boolean(
    value &&
      typeof value.accessToken === "string" &&
      value.accessToken.length > 0 &&
      VALID_ROLES.has(value.role)
  );
}

export function readSharedAuth(storage = globalThis.localStorage) {
  try {
    const value = JSON.parse(storage?.getItem(AUTH_STORAGE_KEY) || "null");
    return isValidAuth(value) ? value : null;
  } catch {
    return null;
  }
}

export function writeSharedAuth(auth, storage = globalThis.localStorage) {
  if (!isValidAuth(auth)) return false;
  try {
    storage?.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth));
    return Boolean(storage);
  } catch {
    return false;
  }
}

export function clearSharedAuth(storage = globalThis.localStorage) {
  try {
    storage?.removeItem(AUTH_STORAGE_KEY);
    return Boolean(storage);
  } catch {
    return false;
  }
}

export function clearSharedAuthIfToken(expectedToken, storage = globalThis.localStorage) {
  if (!expectedToken) return false;
  try {
    const current = readSharedAuth(storage);
    if (current?.accessToken !== expectedToken) return false;
    storage?.removeItem(AUTH_STORAGE_KEY);
    return Boolean(storage);
  } catch {
    return false;
  }
}

export async function withAuthTransitionLock(operation, target = globalThis.navigator) {
  const locks = target?.locks;
  if (typeof locks?.request === "function") {
    return locks.request("sportcenter-auth-transition", { mode: "exclusive" }, operation);
  }
  throw new Error("Trình duyệt không hỗ trợ đồng bộ đăng nhập an toàn giữa các tab.");
}

export function clearLegacyTabAuth(storage = globalThis.sessionStorage) {
  try {
    storage?.removeItem(LEGACY_SESSION_AUTH_KEY);
  } catch {
    // Legacy per-tab credentials are never restored, even if cleanup is unavailable.
  }
}

export function subscribeToSharedAuth(onChange, target = globalThis.window) {
  if (!target?.addEventListener) return () => {};

  const sync = (event) => {
    if (event?.type === "storage" && event.key !== AUTH_STORAGE_KEY && event.key !== null) return;
    try {
      onChange(readSharedAuth(target.localStorage));
    } catch {
      onChange(null);
    }
  };

  target.addEventListener("storage", sync);
  target.addEventListener("focus", sync);
  target.addEventListener("pageshow", sync);
  target.document?.addEventListener("visibilitychange", sync);

  return () => {
    target.removeEventListener("storage", sync);
    target.removeEventListener("focus", sync);
    target.removeEventListener("pageshow", sync);
    target.document?.removeEventListener("visibilitychange", sync);
  };
}
