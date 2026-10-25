import { useState } from 'react';
const seed = [
 { id: 1, member: 'Nguyễn Minh Anh', subject: 'Tư vấn gói hội viên', date: '19/10/2026', status: 'Chờ xử lý', detail: 'Tôi muốn biết thêm về quyền lợi gói Premium.' },
 { id: 2, member: 'Trần Quốc Bảo', subject: 'Đổi lịch lớp học', date: '18/10/2026', status: 'Đang xử lý', detail: 'Tôi cần đổi lịch tập trong tuần này.' },
 { id: 3, member: 'Lê Thu Hà', subject: 'Thanh toán', date: '17/10/2026', status: 'Hoàn thành', detail: 'Nhờ kiểm tra trạng thái thanh toán gói tập.' },
];
export default function SupportPage() {
 const [rows, setRows] = useState(seed); const [selected, setSelected] = useState(null); const [query, setQuery] = useState('');
 const update = (id, status) => setRows(old => old.map(r => r.id === id ? {...r, status} : r));
 const filtered = rows.filter(r => `${r.member} ${r.subject} ${r.status}`.toLowerCase().includes(query.toLowerCase()));
 return <><div className="page-intro"><div><h2>Hỗ trợ hội viên</h2><p>Tiếp nhận yêu cầu và cập nhật tiến độ xử lý.</p></div><span className="date-chip">{rows.filter(r => r.status === 'Chờ xử lý').length} yêu cầu chờ</span></div>
 <div className="stats-grid"><div className="stat-card"><span>Chờ xử lý</span><strong>{rows.filter(r=>r.status==='Chờ xử lý').length}</strong><i className="stat-dot orange"/></div><div className="stat-card"><span>Đang xử lý</span><strong>{rows.filter(r=>r.status==='Đang xử lý').length}</strong><i className="stat-dot blue"/></div><div className="stat-card"><span>Hoàn thành</span><strong>{rows.filter(r=>r.status==='Hoàn thành').length}</strong><i className="stat-dot green"/></div></div>
 <section className="panel"><div className="panel-head"><div><h3>Yêu cầu hỗ trợ</h3><p>Chọn một yêu cầu để xem chi tiết.</p></div><input className="search-input" placeholder="Tìm yêu cầu..." value={query} onChange={e=>setQuery(e.target.value)}/></div>
 <div className="table-wrap"><table><thead><tr><th>#</th><th>Hội viên</th><th>Chủ đề</th><th>Ngày gửi</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{filtered.map((r,i)=><tr key={r.id}><td>{i+1}</td><td>{r.member}</td><td>{r.subject}</td><td>{r.date}</td><td><span className={`status ${r.status==='Hoàn thành'?'success':r.status==='Đang xử lý'?'info':'warning'}`}>{r.status}</span></td><td><div className="actions"><button className="btn-light" onClick={()=>setSelected(r)}>Chi tiết</button><select className="table-select" value={r.status} onChange={e=>update(r.id,e.target.value)}><option>Chờ xử lý</option><option>Đang xử lý</option><option>Hoàn thành</option></select></div></td></tr>)}</tbody></table></div></section>
 {selected && <div className="modal-backdrop" onClick={()=>setSelected(null)}><section className="modal-card" onClick={e=>e.stopPropagation()}><div className="modal-head"><h3>Chi tiết yêu cầu</h3><button className="close-btn" onClick={()=>setSelected(null)}>×</button></div><p><b>Hội viên:</b> {selected.member}</p><p><b>Chủ đề:</b> {selected.subject}</p><p><b>Nội dung:</b> {selected.detail}</p><p><b>Ngày gửi:</b> {selected.date}</p><button className="btn-primary" onClick={()=>{update(selected.id,'Đang xử lý');setSelected(null)}}>Nhận xử lý</button></section></div>}
 </>;
}
