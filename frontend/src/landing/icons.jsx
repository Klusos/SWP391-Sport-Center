const paths = {
  ArrowRight: <><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></>,
  ArrowUpRight: <><path d="M7 17 17 7" /><path d="M8 7h9v9" /></>,
  CalendarDays: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M16 3v4M8 3v4M3 11h18" /></>,
  ChartColumn: <><path d="M4 20V10M10 20V4M16 20v-7M22 20H2" /></>,
  CircleCheck: <><circle cx="12" cy="12" r="9" /><path d="m8 12 2.5 2.5L16 9" /></>,
  Mail: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m3 7 9 6 9-6" /></>,
  MapPin: <><path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
  Menu: <><path d="M4 6h16M4 12h16M4 18h16" /></>,
  Moon: <path d="M20.5 15.5A8.5 8.5 0 0 1 8.5 3.5 9 9 0 1 0 20.5 15.5Z" />,
  Phone: <path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.4 19.4 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7l.5 3a2 2 0 0 1-.6 1.7L7.1 10.3a16 16 0 0 0 6 6l1.9-1.9a2 2 0 0 1 1.7-.6l3 .5a2 2 0 0 1 1.7 2.6Z" />,
  ScanLine: <><path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2M3 12h18" /></>,
  Sun: <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M2 12h2m16 0h2M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42" /></>,
  TrendingUp: <><path d="m3 17 6-6 4 4 8-8" /><path d="M15 7h6v6" /></>,
  Users: <><path d="M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M10 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM20 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /></>,
  X: <><path d="m18 6-12 12M6 6l12 12" /></>,
};

function makeIcon(name) {
  function Icon({ size = 24, strokeWidth = 1.8, ...props }) {
    return <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" {...props}>{paths[name]}</svg>;
  }
  Icon.displayName = name;
  return Icon;
}

export const ArrowRight = makeIcon("ArrowRight");
export const ArrowUpRight = makeIcon("ArrowUpRight");
export const CalendarDays = makeIcon("CalendarDays");
export const ChartColumn = makeIcon("ChartColumn");
export const CircleCheck = makeIcon("CircleCheck");
export const Mail = makeIcon("Mail");
export const MapPin = makeIcon("MapPin");
export const Menu = makeIcon("Menu");
export const Moon = makeIcon("Moon");
export const Phone = makeIcon("Phone");
export const ScanLine = makeIcon("ScanLine");
export const Sun = makeIcon("Sun");
export const TrendingUp = makeIcon("TrendingUp");
export const Users = makeIcon("Users");
export const X = makeIcon("X");
