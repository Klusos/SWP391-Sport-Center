import { useId } from "react";

export default function SportCenterMark({ size = 36, className = "" }) {
  const gid = useId().replace(/:/g, "");
  return (
    <span className={`sc-public-logo ${className}`.trim()} aria-hidden="true">
      <svg viewBox="0 0 48 48" width={size} height={size}>
        <defs>
          <linearGradient id={`ring-${gid}`} x1="8" y1="40" x2="40" y2="8" gradientUnits="userSpaceOnUse">
            <stop offset="0" stopColor="#1f6feb" />
            <stop offset="0.55" stopColor="#1f6feb" />
            <stop offset="1" style={{ stopColor: "var(--lp-logo-ink, var(--sc-public-logo-ink))" }} />
          </linearGradient>
        </defs>
        <g strokeLinecap="round" strokeWidth="2.2">
          <path d="M33 6.5 38.5 3.5" stroke="#1f6feb" />
          <path d="M36.5 10 44 5.8" style={{ stroke: "var(--lp-logo-ink, var(--sc-public-logo-ink))" }} />
          <path d="M40.5 14.5 46 11.5" stroke="#1f6feb" />
          <path d="M2.5 36 8 33" style={{ stroke: "var(--lp-logo-ink, var(--sc-public-logo-ink))" }} />
          <path d="M4 41.5 11.5 37.3" stroke="#1f6feb" />
          <path d="M10 45 15.5 42" stroke="#1f6feb" />
        </g>
        <circle cx="24" cy="24" r="17" fill="none" stroke={`url(#ring-${gid})`} strokeWidth="4.6" />
        <g fill="none" stroke="#1f6feb" strokeWidth="2.9" strokeLinecap="round" strokeLinejoin="round">
          <path d="M27.4 19.6 22.2 27.6" />
          <path d="M18.6 20.4 22.6 18.6 27.4 19.6 30.4 23.4" />
          <path d="M22.2 27.6 26.2 30.2 25.2 34.6" />
          <path d="M22.2 27.6 18.4 30.6 14.6 33.4" />
        </g>
        <circle cx="30.2" cy="15.2" r="2.7" fill="#1f6feb" />
      </svg>
    </span>
  );
}
