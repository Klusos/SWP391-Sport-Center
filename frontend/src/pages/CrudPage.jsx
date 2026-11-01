import { useMemo, useState } from 'react';

const initial = {
  courses: [
    { id: 1, name: 'Yoga cơ bản', category: 'Yoga', duration: '8 tuần', price: 1200000, status: 'Đang mở' },
    { id: 2, name: 'Gym nền tảng', category: 'Gym', duration: '12 tuần', price: 1800000, status: 'Đang mở' },
    { id: 3, name: 'Cardio nâng cao', category: 'Cardio', duration: '6 tuần', price: 900000, status: 'Tạm dừng' },
  ],
  classes: [
    { id: 1, name: 'Yoga sáng', course: 'Yoga cơ bản', coach: 'Nguyễn Minh', schedule: 'T2, T4 · 07:00', capacity: 20, status: 'Đang diễn ra' },
    { id: 2, name: 'Gym chiều', course: 'Gym nền tảng', coach: 'Trần An', schedule: 'T3, T5 · 17:30', capacity: 15, status: 'Đang diễn ra' },
  ],
};
const fields = {
  courses: [
    ['name', 'Tên khóa học', 'text'], ['category', 'Bộ môn', 'text'],
    ['duration', 'Thời lượng', 'text'], ['price', 'Học phí (VNĐ)', 'number'], ['status', 'Trạng thái', 'select'],
  ],
  classes: [
    ['name', 'Tên lớp học', 'text'], ['course', 'Khóa học', 'text'],
    ['coach', 'Huấn luyện viên', 'text'], ['schedule', 'Lịch học', 'text'],
    ['capacity', 'Sức chứa', 'number'], ['status', 'Trạng thái', 'select'],
  ],
};
const columns = {
  courses: [['name', 'Tên khóa học'], ['category', 'Bộ môn'], ['duration', 'Thời lượng'], ['price', 'Học phí'], ['status', 'Trạng thái']],
  classes: [['name', 'Tên lớp'], ['course', 'Khóa học'], ['coach', 'Huấn luyện viên'], ['schedule', 'Lịch học'], ['capacity', 'Sức chứa'], ['status', 'Trạng thái']],
};
const blank = kind => Object.fromEntries(fields[kind].map(([key]) => [key, key === 'status' ? (kind === 'courses' ? 'Đang mở' : 'Đang diễn ra') : '']));

export default function CrudPage({ kind, title, subtitle }) {
  const [rows, setRows] = useState(initial[kind].map(x => ({ ...x })));
  const [search, setSearch] = useState('');
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(blank(kind));
  const [showForm, setShowForm] = useState(false);
  const filtered = useMemo(() => rows.filter(row => Object.values(row).join(' ').toLowerCase().includes(search.toLowerCase())), [rows, search]);
  const openCreate = () => { setEditing(null); setForm(blank(kind)); setShowForm(true); };
  const openEdit = row => { setEditing(row.id); setForm(Object.fromEntries(fields[kind].map(([key]) => [key, row[key] ?? '']))); setShowForm(true); };
  const save = e => {
    e.preventDefault();
    const normalized = { ...form, ...(kind === 'courses' ? { price: Number(form.price) || 0 } : { capacity: Number(form.capacity) || 0 }) };
    if (editing !== null) setRows(old => old.map(row => row.id === editing ? { ...row, ...normalized } : row));
    else setRows(old => [...old, { ...normalized, id: Math.max(0, ...old.map(x => x.id)) + 1 }]);
    setShowForm(false);
  };
  const remove = row => { if (window.confirm(`Bạn có chắc muốn xóa "${row.name}"?`)) setRows(old => old.filter(x => x.id !== row.id)); };
  const money = value => Number(value || 0).toLocaleString('vi-VN') + ' ₫';
  return (
    <>
      <div className="page-intro"><div><h2>{title}</h2><p>{subtitle}</p></div><button className="btn-primary" onClick={openCreate}>＋ Thêm mới</button></div>
      <section className="stats-grid">
        <div className="stat-card"><span>Tổng số</span><strong>{rows.length}</strong><i className="stat-dot blue" /></div>
        <div className="stat-card"><span>Đang hoạt động</span><strong>{rows.filter(x => String(x.status).includes('Đang')).length}</strong><i className="stat-dot green" /></div>
        <div className="stat-card"><span>Kết quả tìm kiếm</span><strong>{filtered.length}</strong><i className="stat-dot orange" /></div>
      </section>
      <section className="panel">
        <div className="panel-head"><div><h3>Danh sách</h3><p>Quản lý dữ liệu {kind === 'courses' ? 'khóa học' : 'lớp học'} của trung tâm</p></div><input className="search-input" value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm kiếm..." /></div>
        <div className="table-wrap"><table><thead><tr><th>#</th>{columns[kind].map(([key, label]) => <th key={key}>{label}</th>)}<th>Thao tác</th></tr></thead>
          <tbody>{filtered.map((row, i) => <tr key={row.id}><td>{i + 1}</td>{columns[kind].map(([key]) => <td key={key}>{key === 'price' ? money(row[key]) : row[key]}</td>)}<td><div className="actions"><button className="btn-light" onClick={() => openEdit(row)}>Sửa</button><button className="btn-danger" onClick={() => remove(row)}>Xóa</button></div></td></tr>)}
          {filtered.length === 0 && <tr><td colSpan={columns[kind].length + 2} className="empty">Không tìm thấy dữ liệu phù hợp.</td></tr>}</tbody>
        </table></div>
      </section>
      {showForm && <div className="modal-backdrop" onClick={() => setShowForm(false)}><section className="modal-card" onClick={e => e.stopPropagation()}>
        <div className="modal-head"><h3>{editing !== null ? 'Cập nhật thông tin' : 'Thêm mới'}</h3><button className="close-btn" onClick={() => setShowForm(false)}>×</button></div>
        <form onSubmit={save}><div className="form-grid">{fields[kind].map(([key, label, type]) => <label key={key}>{label}
          {type === 'select' ? <select value={form[key]} onChange={e => setForm({ ...form, [key]: e.target.value })}><option>{kind === 'courses' ? 'Đang mở' : 'Đang diễn ra'}</option><option>Tạm dừng</option><option>Đã kết thúc</option></select>
            : <input required={key !== 'price' && key !== 'capacity'} type={type} min={type === 'number' ? 0 : undefined} value={form[key]} onChange={e => setForm({ ...form, [key]: e.target.value })} />}</label>)}</div>
          <div className="modal-actions"><button type="button" className="btn-light" onClick={() => setShowForm(false)}>Hủy</button><button className="btn-primary" type="submit">Lưu thông tin</button></div></form>
      </section></div>}
    </>
  );
}
