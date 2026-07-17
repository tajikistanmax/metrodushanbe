"use client";

/**
 * Канал «страница → шапка» для индикатора источника данных.
 *
 * ЗАЧЕМ. Шапка переехала в app/layout.tsx (одна на весь портал вместо копии в
 * каждом page-client). Но пилюля источника — API или офлайн-демо — принадлежит
 * ДАННЫМ страницы: их грузит клиент раздела, а layout о них ничего не знает и
 * знать не должен. Пробросить проп через layout нельзя — между ним и страницей
 * нет общего рендера. Поэтому страница публикует источник в контекст, а шапка
 * его читает.
 *
 * Три состояния РАЗЛИЧАЮТСЯ и означают разное — их нельзя схлопывать:
 *   undefined — раздел не работает с данными сети (новости, офлайн-страница);
 *               пилюли нет вовсе;
 *   null      — данные ещё грузятся; пилюля показывает «загружается»;
 *   DataSource— источник известен: API либо демо.
 * Ровно та же семантика, что была у опционального пропа `source` у Header.
 */

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { DataSource } from "@/lib/types";

/** undefined — раздел без данных сети; null — грузится. */
type ReportedSource = DataSource | null | undefined;

type DataSourceContextValue = {
  source: ReportedSource;
  setSource: (source: ReportedSource) => void;
};

const DataSourceContext = createContext<DataSourceContextValue | null>(null);

export function DataSourceProvider({ children }: { children: ReactNode }) {
  const [source, setSource] = useState<ReportedSource>(undefined);
  const value = useMemo(() => ({ source, setSource }), [source]);
  return (
    <DataSourceContext.Provider value={value}>
      {children}
    </DataSourceContext.Provider>
  );
}

/** Текущий источник данных — для шапки. */
export function useDataSource(): ReportedSource {
  return useContext(DataSourceContext)?.source;
}

/**
 * Публикует источник данных раздела в шапку.
 *
 * Сброс в undefined при размонтировании обязателен: без него при переходе на
 * раздел без данных сети (например, /news) в шапке осталась бы пилюля от
 * предыдущей страницы — шапка утверждала бы про источник данных, которых на
 * экране уже нет. React вызывает cleanup размонтированного дерева до эффектов
 * нового, поэтому порядок «сброс → публикация» соблюдается сам.
 */
export function useReportDataSource(source: ReportedSource): void {
  const ctx = useContext(DataSourceContext);
  const setSource = ctx?.setSource;
  useEffect(() => {
    if (!setSource) return;
    setSource(source);
    return () => setSource(undefined);
  }, [setSource, source]);
}
