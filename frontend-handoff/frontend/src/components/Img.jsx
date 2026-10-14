import { useState } from 'react'

// Ảnh có fallback: nếu chưa có file trong public/images thì hiện khối màu thay thế.
export default function Img({ src, alt, className = '', style = {}, label }) {
  const [failed, setFailed] = useState(false)
  if (failed) {
    return (
      <div className={`img-fallback ${className}`} style={style} role="img" aria-label={alt}>
        <span>{label || alt}</span>
      </div>
    )
  }
  return <img src={src} alt={alt} className={className} style={style} onError={() => setFailed(true)} />
}
