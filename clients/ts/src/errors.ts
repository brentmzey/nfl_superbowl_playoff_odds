/** Every failure the client can return, as a discriminated union (never thrown). */
export type NflClientError =
  | { readonly kind: 'config'; readonly message: string }
  | { readonly kind: 'http'; readonly status: number; readonly message: string; readonly url: string }
  | { readonly kind: 'network'; readonly message: string; readonly url: string }
  | { readonly kind: 'timeout'; readonly timeoutMs: number; readonly url: string }
  | {
      readonly kind: 'validation';
      readonly collection: string;
      readonly recordId: string;
      readonly field: string;
      readonly expected: string;
      readonly actual: string;
    }
  | { readonly kind: 'query'; readonly query: string; readonly message: string };

/** Transient failures worth retrying: network blips, timeouts, rate limits, and 5xx (PocketHost cold starts). */
export const isRetryable = (e: NflClientError): boolean =>
  e.kind === 'network' || e.kind === 'timeout' || (e.kind === 'http' && (e.status === 429 || e.status >= 500));

export const describe = (e: NflClientError): string => {
  switch (e.kind) {
    case 'config': return `config: ${e.message}`;
    case 'http': return `HTTP ${e.status} from ${e.url}: ${e.message}`;
    case 'network': return `network error calling ${e.url}: ${e.message}`;
    case 'timeout': return `timed out after ${e.timeoutMs}ms calling ${e.url}`;
    case 'validation': return `${e.collection}/${e.recordId}.${e.field}: expected ${e.expected}, got ${e.actual}`;
    case 'query': return `query ${e.query}: ${e.message}`;
  }
};
