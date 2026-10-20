import PocketBase, { ClientResponseError } from 'pocketbase';
import { err, ok, sequence, type Result } from './fp.ts';
import { isRetryable, type NflClientError } from './errors.ts';
import type { CollectionName, CollectionRecords } from './generated/schema.ts';
import { checkParams, sortClause, type QueryParams, type QueryTemplate } from './query.ts';
import { validateRecord } from './validate.ts';

export type FetchLike = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

export interface RetryPolicy {
  readonly attempts: number;
  readonly initialDelayMs: number;
  readonly factor: number;
  readonly maxDelayMs: number;
  /** 0..1: each delay is stretched by up to this fraction at random. */
  readonly jitter: number;
}

export const DEFAULT_RETRY: RetryPolicy = { attempts: 4, initialDelayMs: 300, factor: 2, maxDelayMs: 5_000, jitter: 0.2 };

export interface PocketBaseSourceOptions {
  /** Instance URL, e.g. from `POCKETHOST_URL`. */
  readonly url: string;
  readonly timeoutMs?: number;
  readonly retry?: Partial<RetryPolicy>;
  /** Injection points for tests and non-browser runtimes. */
  readonly fetch?: FetchLike;
  readonly sleep?: (ms: number) => Promise<void>;
  readonly random?: () => number;
}

/**
 * Read port over the predictions database, in the spirit of the sibling repos' `DatabaseAdapter`, but typed
 * end-to-end: a query template plus its inferred params in, validated records (or a typed error) out.
 */
export interface RecordSource {
  query<C extends CollectionName, F extends string>(
    template: QueryTemplate<C, F>,
    params: QueryParams<CollectionRecords[C], F>,
  ): Promise<Result<CollectionRecords[C][], NflClientError>>;
  health(): Promise<Result<true, NflClientError>>;
}

/** Reads `POCKETHOST_URL` (no hard-coded fallback). Works in Bun/Node; pass the URL explicitly in browsers. */
export const urlFromEnv = (env: Readonly<Record<string, string | undefined>> = typeof process === 'undefined' ? {} : process.env): Result<string, NflClientError> => {
  const url = env['POCKETHOST_URL']?.trim();
  return url ? ok(url.replace(/\/+$/, '')) : err({ kind: 'config', message: 'POCKETHOST_URL is not set (see .env.example)' });
};

const toError = (e: unknown, url: string, timedOut: boolean, timeoutMs: number): NflClientError => {
  if (timedOut) return { kind: 'timeout', timeoutMs, url };
  if (e instanceof ClientResponseError) {
    return e.status === 0
      ? { kind: 'network', message: e.message, url }
      : { kind: 'http', status: e.status, message: e.message, url: e.url || url };
  }
  return { kind: 'network', message: e instanceof Error ? e.message : String(e), url };
};

export const createPocketBaseSource = (options: PocketBaseSourceOptions): RecordSource => {
  const url = options.url.replace(/\/+$/, '');
  const pb = new PocketBase(url);
  pb.autoCancellation(false);
  const timeoutMs = options.timeoutMs ?? 15_000;
  const policy: RetryPolicy = { ...DEFAULT_RETRY, ...options.retry };
  const sleep = options.sleep ?? ((ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)));
  const random = options.random ?? Math.random;
  const transport = options.fetch === undefined ? {} : { fetch: options.fetch };

  /** One attempt with its own timeout; failures come back as typed errors. */
  const attempt = async <T>(run: (signal: AbortSignal) => Promise<T>): Promise<Result<T, NflClientError>> => {
    const controller = new AbortController();
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, timeoutMs);
    try {
      return ok(await run(controller.signal));
    } catch (e: unknown) {
      return err(toError(e, url, timedOut, timeoutMs));
    } finally {
      clearTimeout(timer);
    }
  };

  /** Exponential back-off with jitter on retryable errors only. */
  const withRetry = async <T>(run: (signal: AbortSignal) => Promise<T>, n = 1, delay = policy.initialDelayMs): Promise<Result<T, NflClientError>> => {
    const result = await attempt(run);
    if (result.isOk() || n >= policy.attempts || !isRetryable(result.error)) return result;
    await sleep(Math.min(policy.maxDelayMs, Math.round(delay * (1 + policy.jitter * random()))));
    return withRetry(run, n + 1, delay * policy.factor);
  };

  return {
    async query(template, params) {
      const problem = checkParams(template.filter, params as Readonly<Record<string, unknown>>);
      if (problem !== null) return err({ kind: 'query', query: template.name, message: problem });
      const filter = template.filter === '' ? '' : pb.filter(template.filter, params as Record<string, unknown>);
      const base = { filter, sort: sortClause(template.sort), requestKey: null, ...transport };
      const collection = pb.collection(template.collection);
      const rows = await withRetry((signal) =>
        template.limit === undefined
          ? collection.getFullList<unknown>({ ...base, batch: 500, signal })
          : collection.getList<unknown>(1, template.limit, { ...base, skipTotal: true, signal }).then((page) => page.items),
      );
      return rows.flatMap((items) => sequence(items.map((raw) => validateRecord(template.collection, raw))));
    },

    async health() {
      const res = await withRetry((signal) => pb.health.check({ requestKey: null, signal, ...transport }));
      return res.map(() => true as const);
    },
  };
};
