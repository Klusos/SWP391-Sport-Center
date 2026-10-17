import { useState } from 'react'
import { resolveImageUrl } from '../../api.js'

export default function Img({ src, alt, className = '', style = {}, label }) {
  const [failedSrc, setFailedSrc] = useState('')
  const imageSrc = resolveImageUrl(src)
  if (!imageSrc || failedSrc === imageSrc) {
    return <div className={'img-fallback ' + className} style={style} role="img" aria-label={alt || label || 'Ảnh chưa có'}>
      <span>{label || alt || 'Chưa có ảnh'}</span>
    </div>
  }
  return <img src={imageSrc} alt={alt || ''} className={className} style={style} onError={() => setFailedSrc(imageSrc)} />
}