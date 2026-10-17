import { useCallback, useEffect, useState } from 'react'
import Sidebar from '../components/Sidebar'
import Img from '../public-site/components/Img'
import { getCoachesApi, getFacilitiesApi, getSportsApi, uploadCoachImageApi, uploadFacilityImageApi, uploadSportImageApi } from '../api'

const categories = [
  { key: 'sports', label: 'Bộ môn', upload: uploadSportImageApi, title: (row) => row.name },
  { key: 'coaches', label: 'Huấn luyện viên', upload: uploadCoachImageApi, title: (row) => row.userName || row.name },
  { key: 'facilities', label: 'Cơ sở vật chất', upload: uploadFacilityImageApi, title: (row) => row.name },
]

function ImageCard({ item, category, token, refresh }) {
  const [file, setFile] = useState(null)
  const [preview, setPreview] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview)
  }, [preview])
  function choose(event) {
    const selected = event.target.files?.[0] || null
    setError('')
    setNotice('')
    if (selected && !['image/jpeg', 'image/png', 'image/webp'].includes(selected.type)) {
      setFile(null); event.target.value = ''; setError('Chỉ chấp nhận JPEG, PNG hoặc WebP.'); return
    }
    if (selected && selected.size > 5 * 1024 * 1024) {
      setFile(null); event.target.value = ''; setError('Ảnh không được vượt quá 5 MB.'); return
    }
    setPreview(selected ? URL.createObjectURL(selected) : '')
    setFile(selected)
    event.target.value = ''
  }
  async function upload() {
    if (!file || busy) return
    setBusy(true); setError(''); setNotice('')
    try {
      await category.upload(token, item.id, file)
      setFile(null); setPreview(''); setNotice('Đã cập nhật ảnh.')
      await refresh()
    } catch (err) { setError(err.message || 'Không tải được ảnh lên.') }
    finally { setBusy(false) }
  }
  const title = category.title(item)
  return <article className="image-management-card">
    <Img src={preview || item.imageUrl} alt={title} label={title} className="image-management-preview" />
    <div className="image-management-content">
      <h3>{title}</h3>
      {item.status && <p className="muted small">Trạng thái: {item.status}</p>}
      {category.key === 'facilities' && <p className="muted small">{[item.type, item.sportName].filter(Boolean).join(' · ')}</p>}
      {category.key === 'coaches' && <p className="muted small">{item.email}</p>}
      <label htmlFor={category.key + '-' + item.id + '-file'}>Chọn ảnh (JPEG, PNG, WebP; tối đa 5 MB)</label>
      <input id={category.key + '-' + item.id + '-file'} type="file" accept="image/jpeg,image/png,image/webp" onChange={choose} disabled={busy} />
      {error && <p className="banner error" role="alert">{error}</p>}
      {notice && <p className="banner success" role="status">{notice}</p>}
      <button type="button" className="btn-primary" onClick={upload} disabled={!file || busy}>{busy ? 'Đang tải…' : 'Tải ảnh lên'}</button>
    </div>
  </article>
}

export default function ImageManagement({ token, role, onLogout, onNavigate }) {
  const [data, setData] = useState({ sports: [], coaches: [], facilities: [] })
  const [active, setActive] = useState('sports')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const [sports, coaches, facilities] = await Promise.all([getSportsApi(token), getCoachesApi(token), getFacilitiesApi(token)])
      setData({ sports: Array.isArray(sports) ? sports : [], coaches: Array.isArray(coaches) ? coaches : [], facilities: Array.isArray(facilities) ? facilities : [] })
    } catch (err) {
      setError(err.status === 401 || err.status === 403 ? 'Chỉ ADMIN được phép quản lý ảnh.' : err.message || 'Không tải được danh sách.')
    } finally { setLoading(false) }
  }, [token])
  useEffect(() => { if (role === 'ADMIN') Promise.resolve().then(load) }, [role, load])
  const category = categories.find((item) => item.key === active)
  return <div className="layout">
    <Sidebar role={role} activePage="images" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main">
      <header className="page-header"><p className="eyebrow">QUẢN TRỊ NỘI DUNG</p><h1>Quản lý hình ảnh</h1>
        <p className="subtitle">Tải ảnh cho bộ môn, huấn luyện viên và cơ sở vật chất đã có.</p></header>
      <div className="image-management-tabs" role="tablist" aria-label="Danh mục hình ảnh">
        {categories.map((item) => <button key={item.key} type="button" role="tab" aria-selected={active === item.key}
          className={active === item.key ? 'active' : ''} onClick={() => setActive(item.key)}>{item.label}</button>)}
      </div>
      {error && <p className="banner error" role="alert">{error}</p>}
      {loading ? <div className="queue-state" role="status">Đang tải danh sách…</div>
        : data[active].length === 0 ? <div className="queue-state">Danh mục chưa có dữ liệu.</div>
          : <div className="image-management-grid">{data[active].map((item) =>
            <ImageCard key={item.id} item={item} category={category} token={token} refresh={load} />)}</div>}
    </main>
  </div>
}