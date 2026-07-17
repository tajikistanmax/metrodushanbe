import { lineBadgeLabel, type Dict, type Lang } from "@/lib/i18n";
import type { StationArrivalsResult } from "@/lib/types";

type StationArrivalsProps = {
  results: StationArrivalsResult[];
  loading: boolean;
  lang: Lang;
  dict: Dict;
};

/** Оценочные прибытия по всем линиям выбранной станции. */
export default function StationArrivals({
  results,
  loading,
  lang,
  dict,
}: StationArrivalsProps) {
  return (
    <section className="mt-3 border-t border-[var(--border-subtle)] pt-3">
      <h4 className="text-caption font-bold uppercase tracking-[0.08em] text-text-secondary">
        {dict.arrivals.heading}
      </h4>

      {loading && (
        <p role="status" className="mt-1.5 text-caption text-text-secondary">
          {dict.arrivals.loading}
        </p>
      )}

      {!loading && results.length === 0 && (
        <p role="status" className="mt-1.5 text-caption text-text-secondary">
          {dict.arrivals.unavailable}
        </p>
      )}

      {!loading && results.length > 0 && (
        <div className="mt-2 space-y-2">
          {results.map(({ data, source }) => (
            <div
              key={data.lineCode}
              className="rounded-control border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-2.5"
            >
              <div className="flex flex-wrap items-center gap-1.5">
                <span className="line-badge">
                  {lineBadgeLabel(data.lineCode, lang)}
                </span>
                {data.headwayMinutes !== null && (
                  <span className="text-caption font-semibold text-text-secondary">
                    {dict.arrivals.headway} {data.headwayMinutes}{" "}
                    {dict.arrivals.minuteSuffix}
                  </span>
                )}
                {source === "demo" && (
                  <span className="ml-auto rounded-chip border border-[var(--border-subtle)] px-2 py-0.5 text-caption font-semibold uppercase tracking-[0.08em] text-text-secondary">
                    {dict.arrivals.demo}
                  </span>
                )}
              </div>

              {data.arrivals.length > 0 ? (
                <ol className="mt-2 grid grid-cols-3 gap-1.5">
                  {data.arrivals.map((arrival) => (
                    <li
                      key={`${data.lineCode}-${arrival.time}`}
                      className="rounded-control bg-[var(--surface-sunken)] px-1.5 py-1.5 text-center"
                    >
                      <span className="block text-small font-bold tabular-nums">
                        {arrival.time}
                      </span>
                      <span className="block text-caption font-semibold text-text-secondary">
                        {arrival.etaMinutes === 0
                          ? dict.arrivals.now
                          : `${arrival.etaMinutes} ${dict.arrivals.minuteSuffix}`}
                      </span>
                    </li>
                  ))}
                </ol>
              ) : (
                <p className="mt-2 text-caption text-text-secondary">
                  {data.serviceActive
                    ? dict.arrivals.noUpcoming
                    : dict.arrivals.inactive}
                </p>
              )}
            </div>
          ))}

          <p className="text-caption leading-relaxed text-text-secondary">
            {dict.arrivals.estimated}
          </p>
        </div>
      )}
    </section>
  );
}
