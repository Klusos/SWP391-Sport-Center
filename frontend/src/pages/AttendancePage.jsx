import { useMemo, useState } from 'react';

const seed = [
  { id: 1, member: 'Nguyễn Minh Anh', className: 'Yoga sáng', time: '07:00', status: 'Có mặt' },
  { id: 2, member: 'Trần Quốc Bảo', className: 'Yoga sáng', time: '07:00', status: 'Chưa điểm danh' },
  { id: 3, member: 'Lê Thu Hà', className: 'Gym chiều', time: '17:30', status: 'Có mặt' },
  { id: 4, member: 'Phạm Hoàng Nam', className: 'Gym chiều', time: '17:30', status: 'Vắng mặt' },
];
export default function AttendancePage() {
  const [rows, setRows] = useState(seed);
  const [query, setQuery] = useState('');
  const [classFilter, setClassFilter] = useState('Tất cả lớp');
  const filtered = useMemo(() => rows.filter(r => (classFilter === 'Tất cả lớp' || r.className === classFilter) && `${r.member} ${r.className}`.toLowerCase().includes(query.toLowerCase())), [rows, query, classFilter]);
  const setStatus = (id, status) => setRows(old => old.map(r => r.id === id ? { ...r, status } : r));
  return <><div className="page-intro"><div><h2>Điểm danh lớp học</h2><p>Ghi nhận trạng thái tham gia của hội viên theo từng lớp.</p></div><span className="date-chip">{new Date().toLocaleDateString('vi-VN')}</span></div>
    <div className="stats-grid"><div className="stat-card"><span>Tổng hội viên</span><strong>{rows.length}</strong><i className="stat-dot blue" /></div><div className="stat-card"><span>Có mặt</span><strong>{rows.filter(r => r.status === 'Có mặt').length}</strong><i className="stat-dot green" /></div><div className="stat-card"><span>Chưa điểm danh</span><strong>{rows.filter(r => r.status === 'Chưa điểm danh').length}</strong><i className="stat-dot orange" /></div></div>
    <section className="panel"><div className="panel-head"><div><h3>Danh sách điểm danh</h3><p>Chọn trạng thái tương ứng với từng hội viên.</p></div><div className="filter-row"><input className="search-input" placeholder="Tìm hội viên..." value={query} onChange={e => setQuery(e.target.value)} /><select className="search-input" value={classFilter} onChange={e => setClassFilter(e.target.value)}><option>Tất cả lớp</option>{[...new Set(rows.map(r => r.className))].map(c => <option key={c}>{c}</option>)}</select></div></div>
      <div className="table-wrap"><table><thead><tr><th>#</th><th>Hội viên</th><th>Lớp học</th><th>Giờ học</th><th>Trạng thái</th><th>Cập nhật</th></tr></thead><tbody>{filtered.map((r, i) => <tr key={r.id}><td>{i + 1}</td><td><strong>{r.member}</strong></td><td>{r.className}</td><td>{r.time}</td><td><span className={`status ${r.status === 'Có mặt' ? 'success' : r.status === 'Vắng mặt' ? 'danger' : 'warning'}`}>{r.status}</span></td><td><select className="table-select" value={r.status} onChange={e => setStatus(r.id, e.target.value)}><option>Có mặt</option><option>Vắng mặt</option><option>Chưa điểm danh</option></select></td></tr>)}</tbody></table></div></section></>;
}
