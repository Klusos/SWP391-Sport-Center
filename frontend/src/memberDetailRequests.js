export async function applyMemberDetailRequestIfCurrent(promise, { isCurrent, onSuccess, onError, onSettled }) {
  let result;
  let failure;
  let failed = false;
  try {
    result = await promise;
  } catch (error) {
    failure = error;
    failed = true;
  }

  if (!isCurrent()) return false;
  if (failed) onError?.(failure);
  else onSuccess?.(result);
  if (isCurrent()) onSettled?.();
  return true;
}
