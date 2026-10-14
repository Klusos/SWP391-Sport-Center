import { useState } from 'react'

export default function ConsultForm() {
  const [form, setForm] = useState({ name: '', phone: '', email: '', sport: '', age: '' })
  const [sent, setSent] = useState(false)

  const onChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })
  const onSubmit = (e) => {
    e.preventDefault()
    // TODO: gọi API thật tại đây
    console.log('Đăng ký tư vấn:', form)
    setSent(true)
    setForm({ name: '', phone: '', email: '', sport: '', age: '' })
  }

  const fields = [
    ['name', 'Họ và tên', 'text'],
    ['phone', 'Số điện thoại', 'tel'],
    ['email', 'Email', 'email'],
    ['sport', 'Môn học', 'text'],
    ['age', 'Độ tuổi', 'number'],
  ]

  return (
    <form className="consult-form" onSubmit={onSubmit}>
      <h3>TƯ VẤN GHI DANH</h3>
      {fields.map(([name, label, type]) => (
        <input
          key={name}
          name={name}
          type={type}
          placeholder={label}
          aria-label={label}
          value={form[name]}
          onChange={onChange}
          required
          className="form-control"
        />
      ))}
      <button type="submit" className="btn btn-brand-dark">ĐĂNG KÍ NGAY</button>
      {sent && <p className="consult-ok" role="status">Đã gửi! Trung tâm sẽ phản hồi trong 24h.</p>}
    </form>
  )
}
