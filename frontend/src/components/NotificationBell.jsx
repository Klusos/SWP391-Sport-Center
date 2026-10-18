import { useCallback, useEffect, useRef, useState } from "react";
import {
  getNotificationsApi,
  getUnreadNotificationCountApi,
  markAllNotificationsReadApi,
  markNotificationReadApi,
} from "../api";
import "./NotificationBell.css";

const formatDate = (value) => {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toLocaleString("vi-VN");
};

export default function NotificationBell({ token, role }) {
  const [unreadCount, setUnreadCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [page, setPage] = useState(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const countRequest = useRef(null);

  const refreshCount = useCallback(async (force = false) => {
    if (!token) return;
    if (countRequest.current) {
      if (!force) return countRequest.current;
      await countRequest.current;
    }
    const request = getUnreadNotificationCountApi(token)
      .then((result) => setUnreadCount(Number.isFinite(result?.unreadCount) ? result.unreadCount : 0))
      .catch((requestError) => {
        setError(requestError.message || "Không thể làm mới số thông báo chưa đọc.");
      });
    countRequest.current = request;
    try {
      await request;
    } finally {
      if (countRequest.current === request) countRequest.current = null;
    }
  }, [token]);

  const loadPage = async (nextPage) => {
    if (!token) return;
    setLoading(true);
    setError("");
    try {
      const result = await getNotificationsApi(token, nextPage, 20);
      const normalized = result && Array.isArray(result.content) ? result : { content: [], number: nextPage, totalPages: 0, hasNext: false };
      setPage(normalized);
      setPageNumber(normalized.number ?? nextPage);
    } catch (requestError) {
      setError(requestError.message || "Không thể tải thông báo.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!token || !["ADMIN", "RECEPTIONIST", "COACH", "MEMBER"].includes(role)) return undefined;
    const initial = window.setTimeout(refreshCount, 0);
    const timer = window.setInterval(refreshCount, 45000);
    return () => {
      window.clearTimeout(initial);
      window.clearInterval(timer);
    };
  }, [token, role, refreshCount]);

  const markOneRead = async (notification) => {
    if (notification.isRead || saving) return;
    setSaving(true);
    setError("");
    try {
      const updated = await markNotificationReadApi(token, notification.id);
      setPage((current) => current ? {
        ...current,
        content: current.content.map((item) => item.id === updated.id ? updated : item),
      } : current);
      await refreshCount(true);
    } catch (requestError) {
      setError(requestError.message || "Không thể cập nhật thông báo.");
    } finally {
      setSaving(false);
    }
  };

  const markAllRead = async () => {
    if (saving || unreadCount === 0) return;
    setSaving(true);
    setError("");
    try {
      await markAllNotificationsReadApi(token);
      setPage((current) => current ? {
        ...current,
        content: current.content.map((item) => ({ ...item, isRead: true })),
      } : current);
      await refreshCount(true);
    } catch (requestError) {
      setError(requestError.message || "Không thể đánh dấu đã đọc.");
    } finally {
      setSaving(false);
    }
  };

  const togglePanel = () => {
    const next = !open;
    setOpen(next);
    if (next) loadPage(0);
  };

  if (!token || !["ADMIN", "RECEPTIONIST", "COACH", "MEMBER"].includes(role)) return null;

  return (
    <div className="notification-widget">
      <button type="button" className="notification-bell" aria-label={`Thông báo, ${unreadCount} chưa đọc`} aria-expanded={open} onClick={togglePanel}>
        <i className="bi bi-bell" aria-hidden="true" />
        {unreadCount > 0 && <span className="notification-count">{unreadCount > 99 ? "99+" : unreadCount}</span>}
      </button>
      {open && (
        <section className="notification-panel" aria-label="Thông báo của bạn">
          <header className="notification-panel-header">
            <div><strong>Thông báo</strong><span>{unreadCount} chưa đọc</span></div>
            <button type="button" onClick={() => setOpen(false)} aria-label="Đóng thông báo">×</button>
          </header>
          {unreadCount > 0 && <button className="notification-read-all" type="button" disabled={saving} onClick={markAllRead}>Đánh dấu tất cả đã đọc</button>}
          {error && <p className="notification-error" role="alert">{error}</p>}
          {loading ? <p className="notification-state">Đang tải thông báo…</p> : error && !page ? null : !page?.content?.length ? (
            <p className="notification-state">Bạn chưa có thông báo nào.</p>
          ) : (
            <div className="notification-list">
              {page.content.map((item) => (
                <button type="button" key={item.id} className={`notification-item${item.isRead ? " is-read" : " is-unread"}`} onClick={() => markOneRead(item)} disabled={saving || item.isRead} aria-label={`${item.isRead ? "Đã đọc" : "Chưa đọc"}: ${item.title}`}>
                  <span className="notification-item-title">{item.title || "Thông báo"}</span>
                  <span className="notification-item-message">{item.message || ""}</span>
                  <time>{formatDate(item.createdAt)}</time>
                </button>
              ))}
            </div>
          )}
          <footer className="notification-pagination">
            <button type="button" disabled={loading || pageNumber <= 0} onClick={() => loadPage(pageNumber - 1)}>Trước</button>
            <span>{page ? `Trang ${(page.number ?? pageNumber) + 1}${page.totalPages ? ` / ${page.totalPages}` : ""}` : ""}</span>
            <button type="button" disabled={loading || !page?.hasNext} onClick={() => loadPage(pageNumber + 1)}>Sau</button>
          </footer>
        </section>
      )}
    </div>
  );
}
