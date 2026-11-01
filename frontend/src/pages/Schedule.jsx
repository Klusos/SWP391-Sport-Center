import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
import { getCoachAttendanceSessionsApi, getAssignedPtBookingsApi, getMyPtBookingsApi, getMyClassSessionsApi, getMyRegistrationsApi } from "../api";
const list = (d) => Array.isArray(d) ? d : d?.content ?? [];
const DAYS = ["Chủ nhật","Thứ 2","Thứ 3","Thứ 4","Thứ 5","Thứ 6","Thứ 7"];
const monday = (v) => { const d = new Date(v); d.setHours(0,0,0,0); d.setDate(d.getDate()-((d.getDay()+6)%7)); return d; };
const short = (d) => d.toLocaleDateString("vi-VN",{day:"2-digit",month:"2-digit"});
const time = (s) => s ? new Date(s).toLocaleTimeString("vi-VN",{hour:"2-digit",minute:"2-digit"}) : "";
const ptEvent = (b, who) => ({id:"pt-"+b.id,startTime:b.startTime,endTime:b.endTime,title:"PT · "+(b.sportName||b.sport?.name||"Buổi cá nhân"),sub:who(b),status:b.status});
export default function Schedule({token,role,onLogout,onNavigate}) {
 const coach=role==="COACH"; const [items,setItems]=useState(null),[regs,setRegs]=useState([]),[error,setError]=useState(""),[week,setWeek]=useState(()=>monday(new Date()));
 useEffect(()=>{let active=true;(async()=>{const rs=coach?await Promise.allSettled([getCoachAttendanceSessionsApi(token),getAssignedPtBookingsApi(token)]):await Promise.allSettled([getMyPtBookingsApi(token),getMyClassSessionsApi(token),getMyRegistrationsApi(token)]);
 if(!active)return;const failed=rs.filter(x=>x.status==="rejected");if(failed.length)setError("Một số dữ liệu lịch không tải được: "+failed.map(x=>x.reason?.message||"Lỗi API").join("; "));
 if(coach){const sessions=rs[0].status==="fulfilled"?list(rs[0].value).map(s=>({id:"class-"+s.classSessionId,startTime:s.startTime,endTime:s.endTime,title:[s.courseClassName,s.courseName].filter(Boolean).join(" · ")||"Lớp học",sub:s.sportName||"",status:s.sessionStatus})):[];const pt=rs[1].status==="fulfilled"?list(rs[1].value).map(b=>ptEvent(b,x=>x.memberName||x.memberFullName||"")):[];setItems([...pt,...sessions]);}
 else {const pt=rs[0].status==="fulfilled"?list(rs[0].value).map(b=>ptEvent(b,x=>x.coachName||x.coachFullName||"")):[];const sessions=rs[1].status==="fulfilled"?list(rs[1].value).filter(s=>String(s.status||s.sessionStatus).toUpperCase()!=="CANCELLED").map(s=>({id:"class-"+(s.id??s.classSessionId),startTime:s.startTime,endTime:s.endTime,title:"Lớp · "+(s.className||s.courseClassName||s.courseName||"Lớp học"),sub:s.facilityName||s.sportName||"",status:s.status||s.sessionStatus})):[];setItems([...pt,...sessions]);if(rs[2].status==="fulfilled")setRegs(list(rs[2].value));}
 })();return()=>{active=false;};},[token,coach]);
 const days=Array.from({length:7},(_,i)=>{const d=new Date(week);d.setDate(d.getDate()+i);return d;});
 const shift=(n)=>setWeek(w=>{const d=new Date(w);d.setDate(d.getDate()+7*n);return d;});
 return <div className="layout"><Sidebar role={role} activePage={coach?"coach-schedule":"member-schedule"} onLogout={onLogout} onNavigate={onNavigate}/><main className="main">
 <h1>Lịch trình</h1><p className="subtitle">{coach?"Các lớp được giao cho bạn và buổi huấn luyện cá nhân.":"Các lớp đã đăng ký và buổi huấn luyện cá nhân của bạn."}</p>{error&&<div className="banner error" role="alert">{error}</div>}
 <div className="pf-weekbar"><button onClick={()=>shift(-1)}>Tuần trước</button><button onClick={()=>setWeek(monday(new Date()))}>Tuần này</button><button onClick={()=>shift(1)}>Tuần sau</button><strong>{short(days[0])} – {short(days[6])}/{days[6].getFullYear()}</strong></div>
 {items===null?<div className="queue-state">Đang tải lịch…</div>:<div className="pf-week">{days.map(d=>{const rows=items.filter(x=>x.startTime&&new Date(x.startTime).toDateString()===d.toDateString()).sort((a,b)=>new Date(a.startTime)-new Date(b.startTime));return <section key={d.toISOString()} className={"pf-day"+(d.toDateString()===new Date().toDateString()?" today":"")}><h2>{DAYS[d.getDay()]}<small>{short(d)}</small></h2>{rows.length===0?<p className="pf-none">Không có buổi</p>:rows.map(x=><div key={x.id} className={"pf-session"+(/CANCEL/i.test(x.status||"")?" off":"")}><small>{time(x.startTime)}{x.endTime?" – "+time(x.endTime):""}</small><b>{x.title}</b>{x.sub&&<span>{x.sub}</span>}{x.status&&<em>{x.status}</em>}</div>)}</section>})}</div>}
 {!coach&&<section className="dash-card" style={{marginTop:24}}><h2>Lớp đã đăng ký</h2>{regs.length===0?<p className="dash-empty">Bạn chưa đăng ký lớp nào.</p>:<ul className="dash-rows">{regs.map(r=><li key={r.id}><span><b>{r.className||"Lớp #"+r.classId}</b><br/><small>{[r.courseName,r.courseOfferingName].filter(Boolean).join(" · ")}</small></span><em>{r.status}</em></li>)}</ul>}</section>}
 </main></div>;
}