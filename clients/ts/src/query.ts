import type { CollectionName, CollectionRecords } from './generated/schema.ts';

/**
 * JDBI-style query templates for PocketBase.
 *
 * A template is a PocketBase filter with named `{:param}` placeholders, e.g.
 * `int_season_year = {:season} && str_status_code = {:status}`. The compiler parses the template and types each
 * parameter from the column it is compared against, so a missing, misspelled or wrongly typed parameter is a
 * compile error. At runtime the values are bound through PocketBase's own `filter()` escaping, never by string
 * concatenation, so parameters cannot inject filter syntax.
 */

/** Values PocketBase can bind into a filter. */
export type FilterValue = string | number | boolean | null | Date;

type Trim<S extends string> =
  S extends ` ${infer R}` ? Trim<R>
  : S extends `${infer R} ` ? Trim<R>
  : S extends `(${infer R}` ? Trim<R>
  : S extends `${infer R})` ? Trim<R>
  : S;

type Clauses<S extends string> =
  S extends `${infer A}&&${infer B}` ? Clauses<A> | Clauses<B>
  : S extends `${infer A}||${infer B}` ? Clauses<A> | Clauses<B>
  : Trim<S>;

/** PocketBase comparison operators (longest first so `>=` is not read as `>`). */
type Operator = '!=' | '>=' | '<=' | '!~' | '?=' | '=' | '>' | '<' | '~';

type ColumnType<R, F extends string> = F extends keyof R ? (R[F] extends FilterValue ? R[F] : never) : never;

type ClauseParams<R, C extends string> =
  C extends `${infer F} ${Operator} {:${infer P}}` ? { [K in P]: ColumnType<R, Trim<F>> }
  : C extends `${string}{:${infer P}}${string}` ? { [K in P]: FilterValue }
  // No placeholder: contributes nothing (must not be `unknown`, which would erase the whole union).
  : Record<never, never>;

type UnionToIntersection<U> = (U extends unknown ? (x: U) => void : never) extends (x: infer I) => void ? I : never;
type Simplify<T> = { [K in keyof T]: T[K] } & {};

/** The parameter object a filter template requires, inferred from the template text. */
export type QueryParams<R, F extends string> = Simplify<UnionToIntersection<ClauseParams<R, Clauses<F>>>>;

/** `column`, `-column` (descending) or `+column`, checked against the record type. */
export type SortKey<R> = `${'' | '-' | '+'}${Extract<keyof R, string>}`;

export interface QueryTemplate<C extends CollectionName, F extends string> {
  /** Stable name for logs and errors, like a JDBI `@SqlQuery` method name. */
  readonly name: string;
  readonly collection: C;
  readonly filter: F;
  readonly sort: readonly SortKey<CollectionRecords[C]>[];
  /** Optional row cap (fetches just one page instead of the full list). */
  readonly limit?: number;
}

export type RecordOf<Q> = Q extends QueryTemplate<infer C, string> ? CollectionRecords[C] : never;
export type ParamsOf<Q> = Q extends QueryTemplate<infer C, infer F> ? QueryParams<CollectionRecords[C], F> : never;

/** Declares a typed query template. `filter` may be `''` for "all rows". */
export const defineQuery = <C extends CollectionName, F extends string>(template: QueryTemplate<C, F>): QueryTemplate<C, F> =>
  Object.freeze({ ...template, sort: Object.freeze([...template.sort]) });

const PLACEHOLDER = /\{:(\w+)\}/g;

/** Placeholder names in a template, in order of first appearance. */
export const placeholders = (filter: string): string[] => [...new Set([...filter.matchAll(PLACEHOLDER)].map((m) => m[1] as string))];

/**
 * Runtime guard behind the compile-time check (for callers using `any` or plain JS): every placeholder must have
 * a value and no unknown parameters may be passed. Returns an error message, or `null` when the params fit.
 */
export const checkParams = (filter: string, params: Readonly<Record<string, unknown>>): string | null => {
  const expected = placeholders(filter);
  const missing = expected.filter((p) => !(p in params) || params[p] === undefined);
  const extra = Object.keys(params).filter((p) => !expected.includes(p));
  if (missing.length > 0) return `missing parameter(s): ${missing.join(', ')}`;
  if (extra.length > 0) return `unknown parameter(s): ${extra.join(', ')}`;
  return null;
};

export const sortClause = (sort: readonly string[]): string => sort.join(',');
