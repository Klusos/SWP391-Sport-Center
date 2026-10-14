import { Routes, Route, useLocation } from 'react-router-dom'
import { useEffect } from 'react'
import Header from './components/Header.jsx'
import Footer from './components/Footer.jsx'
import Home from './pages/Home.jsx'
import Disciplines from './pages/Disciplines.jsx'
import Schedule from './pages/Schedule.jsx'
import Coaches from './pages/Coaches.jsx'
import Facilities from './pages/Facilities.jsx'
import Admission from './pages/Admission.jsx'

function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])
  return null
}

export default function App() {
  return (
    <>
      <ScrollToTop />
      <Header />
      <main>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/bo-mon" element={<Disciplines />} />
          <Route path="/lich-tap" element={<Schedule />} />
          <Route path="/huan-luyen-vien" element={<Coaches />} />
          <Route path="/co-so-vat-chat" element={<Facilities />} />
          <Route path="/tuyen-sinh" element={<Admission />} />
          <Route path="*" element={<Home />} />
        </Routes>
      </main>
      <Footer />
    </>
  )
}
