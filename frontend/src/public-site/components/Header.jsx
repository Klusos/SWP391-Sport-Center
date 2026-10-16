import { useState } from 'react'
import { NavLink, Link } from 'react-router-dom'
import { navLinks } from '../data/content.js'

export default function Header() {
  const [open, setOpen] = useState(false)
  return (
    <header className="site-header">
      <nav className="container-xl d-flex align-items-center justify-content-between py-3">
        <Link to="/" className="brand" onClick={() => setOpen(false)}>SPORT CENTER</Link>
        <button
          className="btn d-lg-none nav-toggle"
          aria-label="Mở menu"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          <i className={`bi ${open ? 'bi-x-lg' : 'bi-list'}`} />
        </button>
        <ul className={`nav-list list-unstyled mb-0 ${open ? 'is-open' : ''}`}>
          {navLinks.map((l) => (
            <li key={l.to}>
              <NavLink to={l.to} end={l.to === '/'} className="nav-item-link" onClick={() => setOpen(false)}>
                {l.label}
              </NavLink>
            </li>
          ))}
          <li className="d-lg-none">
            <Link to="/login" className="btn btn-brand w-100 mt-2" onClick={() => setOpen(false)}>Đăng nhập</Link>
          </li>
        </ul>
        <Link to="/login" className="btn btn-brand d-none d-lg-inline-block">Đăng nhập</Link>
      </nav>
    </header>
  )
}
