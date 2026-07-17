import "server-only";

import { cache } from "react";
import { ApiHttpError, fetchApiJson } from "./api";
import { parseNewsArticle } from "./news-data";
import type { NewsArticle } from "./types";

/**
 * Strict server-side loader. Only a backend 404 becomes null; transport failures and malformed
 * payloads stay errors so an outage is never misreported or indexed as "not found".
 */
export const loadNewsArticleForPage = cache(
  async (slug: string): Promise<NewsArticle | null> => {
    try {
      const payload = await fetchApiJson(`/news/${encodeURIComponent(slug)}`);
      const article = parseNewsArticle(payload);
      if (!article) {
        throw new Error("News API returned an invalid article payload");
      }
      return article;
    } catch (error) {
      if (error instanceof ApiHttpError && error.status === 404) {
        return null;
      }
      throw error;
    }
  },
);
