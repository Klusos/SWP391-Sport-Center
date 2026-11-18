export function resolveLandingHashTarget(hash, sectionIds) {
  const requestedId = String(hash || "").replace(/^#/, "");
  const targetId = requestedId === "consultation" ? "public-consultation" : requestedId;
  return sectionIds.includes(targetId) ? targetId : null;
}

/**
 * Wait for Landing content, fonts, and images to settle before scrolling.
 * The target's document position and page height must remain stable for three
 * animation frames so delayed API content cannot leave the viewport above it.
 */
export function scrollToLandingTarget({ root, target, windowRef = window, documentRef = document, reducedMotion = false }) {
  let cancelled = false;
  let frameId = null;
  let previousPosition = null;
  let stableFrames = 0;

  const cleanup = () => {
    if (frameId != null) windowRef.cancelAnimationFrame(frameId);
    frameId = null;
    windowRef.removeEventListener("wheel", cancelForUser, true);
    windowRef.removeEventListener("touchstart", cancelForUser, true);
    windowRef.removeEventListener("pointerdown", cancelForUser, true);
    windowRef.removeEventListener("keydown", cancelForUser, true);
  };
  const cancelForUser = () => {
    cancelled = true;
    cleanup();
  };

  windowRef.addEventListener("wheel", cancelForUser, { capture: true, passive: true });
  windowRef.addEventListener("touchstart", cancelForUser, { capture: true, passive: true });
  windowRef.addEventListener("pointerdown", cancelForUser, true);
  windowRef.addEventListener("keydown", cancelForUser, true);

  const fontsReady = documentRef.fonts?.ready ?? Promise.resolve();
  Promise.resolve(fontsReady).then(() => {
    if (cancelled) return;

    const checkStability = () => {
      frameId = null;
      if (cancelled) return;

      const imagesReady = [...root.querySelectorAll("img")].every((image) => image.complete);
      if (!imagesReady) {
        stableFrames = 0;
        frameId = windowRef.requestAnimationFrame(checkStability);
        return;
      }

      const position = target.getBoundingClientRect().top + windowRef.scrollY;
      const pageHeight = documentRef.documentElement.scrollHeight;
      const snapshot = `${position.toFixed(1)}:${pageHeight}`;
      stableFrames = snapshot === previousPosition ? stableFrames + 1 : 0;
      previousPosition = snapshot;

      if (stableFrames >= 2) {
        target.scrollIntoView({ block: "start", behavior: reducedMotion ? "auto" : "smooth" });
        cleanup();
        return;
      }
      frameId = windowRef.requestAnimationFrame(checkStability);
    };

    frameId = windowRef.requestAnimationFrame(checkStability);
  });

  return cleanup;
}
