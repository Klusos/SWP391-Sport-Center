export function resolveImgSource(src, resolveApiImage) {
  if (!src || typeof src !== 'string') return ''
  if (src.startsWith('/images/')) return src
  return resolveApiImage(src)
}
