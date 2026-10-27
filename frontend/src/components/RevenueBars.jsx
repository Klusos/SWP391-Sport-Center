import { formatRevenuePeriod, formatVnd, REVENUE_PURPOSES } from "../revenueReport";
import "./RevenueBars.css";

export default function RevenueBars({ periods = [], groupBy = "MONTH", compact = false }) {
  const max = Math.max(0, ...periods.map((period) => Number(period.totalRevenue || 0)));
  return (
    <div className={`revenue-bars-scroll${compact ? " compact" : ""}`}>
      <div className="revenue-bars" role="list" aria-label="Doanh thu theo thời gian">
        {periods.map((period) => {
          const value = Number(period.totalRevenue || 0);
          const height = max === 0 ? 2 : Math.max(3, (value / max) * 100);
          const label = formatRevenuePeriod(period.periodStart, groupBy);
          const categoryDetails = REVENUE_PURPOSES
            .map(([purpose, title]) => `${title}: ${formatVnd(period.revenueByPurpose?.[purpose])}`)
            .join(" · ");
          return (
            <div className="revenue-bar-column" key={period.periodStart} role="listitem">
              <div className="revenue-bar-value">{formatVnd(value)}</div>
              <div
                className="revenue-bar-track"
                title={`${label}: ${formatVnd(value)}\n${categoryDetails}`}
                aria-label={`${label}: ${formatVnd(value)}. ${categoryDetails}`}
              >
                <div className="revenue-bar" style={{ height: `${height}%` }} />
              </div>
              <span>{label}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
