import { useState } from 'react'
import { resolveImageUrl } from '../../api.js'
import { resolveImgSource } from './imageSource.js'

export default function Img({ src, alt, className = '', style = {}, label }) {
  const [failedSrc, setFailedSrc] = useState('')
  const imageSrc = resolveImgSource(src, resolveImageUrl)
  if (!imageSrc || failedSrc === imageSrc) {
    return <div className={'img-fallback ' + className} style={style} role="img" aria-label={alt || label || '\u1ea2nh ch\u01b0a c\u00f3'}>
      <span>{label || alt || 'Ch\u01b0a c\u00f3 \u1ea3nh'}</span>
    </div>
  }
  return <img src={imageSrc} alt={alt || ''} className={className} style={style} onError={() => setFailedSrc(imageSrc)} />
}
