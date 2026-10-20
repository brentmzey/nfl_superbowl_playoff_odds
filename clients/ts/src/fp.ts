/**
 * Result / Optional, matching the FP module used across the sibling repos (`web-chess`, `escar-site`,
 * `settimba-resume-applications-agent`: `src/utils/fp.ts`), plus a few combinators this client needs.
 */
export type Result<T, E> = Ok<T, E> | Err<T, E>;

export class Ok<T, E> {
  readonly ok = true as const;
  constructor(public readonly value: T) {}
  isOk(): this is Ok<T, E> { return true; }
  isErr(): this is Err<T, E> { return false; }
  map<U>(fn: (val: T) => U): Result<U, E> { return new Ok(fn(this.value)); }
  mapErr<F>(_fn: (err: E) => F): Result<T, F> { return new Ok(this.value); }
  flatMap<U>(fn: (val: T) => Result<U, E>): Result<U, E> { return fn(this.value); }
  fold<U>(_onErr: (err: E) => U, onOk: (val: T) => U): U { return onOk(this.value); }
  getOrElse(_fallback: T): T { return this.value; }
  getOrThrow(): T { return this.value; }
}

export class Err<T, E> {
  readonly ok = false as const;
  constructor(public readonly error: E) {}
  isOk(): this is Ok<T, E> { return false; }
  isErr(): this is Err<T, E> { return true; }
  map<U>(_fn: (val: T) => U): Result<U, E> { return new Err<U, E>(this.error); }
  mapErr<F>(fn: (err: E) => F): Result<T, F> { return new Err<T, F>(fn(this.error)); }
  flatMap<U>(_fn: (val: T) => Result<U, E>): Result<U, E> { return new Err<U, E>(this.error); }
  fold<U>(onErr: (err: E) => U, _onOk: (val: T) => U): U { return onErr(this.error); }
  getOrElse(fallback: T): T { return fallback; }
  getOrThrow(): never { throw this.error instanceof Error ? this.error : new Error(JSON.stringify(this.error)); }
}

export const ok = <T, E = never>(value: T): Result<T, E> => new Ok<T, E>(value);
export const err = <T = never, E = unknown>(error: E): Result<T, E> => new Err<T, E>(error);

/** Collects an array of Results into a Result of an array, stopping at the first error. */
export const sequence = <T, E>(results: readonly Result<T, E>[]): Result<T[], E> =>
  results.reduce<Result<T[], E>>((acc, r) => acc.flatMap((xs) => r.map((x) => [...xs, x])), ok<T[], E>([]));

export type Optional<T> = Some<T> | None<T>;

export class Some<T> {
  constructor(public readonly value: T) {}
  isSome(): this is Some<T> { return true; }
  isNone(): this is None<T> { return false; }
  map<U>(fn: (val: T) => U): Optional<U> { return new Some(fn(this.value)); }
  flatMap<U>(fn: (val: T) => Optional<U>): Optional<U> { return fn(this.value); }
  fold<U>(_onNone: () => U, onSome: (val: T) => U): U { return onSome(this.value); }
  getOrElse(_fallback: T): T { return this.value; }
}

export class None<T> {
  isSome(): this is Some<T> { return false; }
  isNone(): this is None<T> { return true; }
  map<U>(_fn: (val: T) => U): Optional<U> { return new None<U>(); }
  flatMap<U>(_fn: (val: T) => Optional<U>): Optional<U> { return new None<U>(); }
  fold<U>(onNone: () => U, _onSome: (val: T) => U): U { return onNone(); }
  getOrElse(fallback: T): T { return fallback; }
}

export const some = <T>(value: T): Optional<T> => new Some(value);
export const none = <T>(): Optional<T> => new None<T>();
export const fromNullable = <T>(value: T | null | undefined): Optional<T> => (value == null ? none<T>() : some(value));
