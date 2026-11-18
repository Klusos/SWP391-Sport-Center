/** Sort section elements in their rendered document order, independent of navbar order. */
export function orderSectionsByDocumentPosition(elements) {
  return [...elements].sort((left, right) => {
    const position = left.compareDocumentPosition(right);
    if (position & 4) return -1; // DOCUMENT_POSITION_FOLLOWING
    if (position & 2) return 1; // DOCUMENT_POSITION_PRECEDING
    return 0;
  });
}

/** Select the last rendered section whose top has crossed the scroll-spy threshold. */
export function selectActiveSection(orderedIds, sectionTops, threshold, atPageEnd = false) {
  if (orderedIds.length === 0) return null;
  if (atPageEnd) return orderedIds[orderedIds.length - 1];

  let active = orderedIds[0];
  for (const id of orderedIds) {
    if (Number.isFinite(sectionTops[id]) && sectionTops[id] <= threshold) active = id;
  }
  return active;
}
