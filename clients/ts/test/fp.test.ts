import { expect, test } from 'bun:test';
import { err, fromNullable, none, ok, sequence, some } from '../src/index.ts';

test('Result combinators', () => {
  const good = ok<number, string>(2);
  const bad = err<number, string>('boom');
  expect(good.map((x) => x * 2).getOrThrow()).toBe(4);
  expect(good.flatMap((x) => ok(x + 1)).getOrElse(0)).toBe(3);
  expect(good.mapErr((e) => e.length).isOk()).toBe(true);
  expect(good.fold(() => 'e', (v) => `v${v}`)).toBe('v2');
  expect(good.isErr()).toBe(false);
  expect(bad.map((x) => x * 2).isErr()).toBe(true);
  expect(bad.flatMap((x) => ok(x)).getOrElse(9)).toBe(9);
  expect(bad.mapErr((e) => e.length).fold((n) => n, () => 0)).toBe(4);
  expect(bad.isOk()).toBe(false);
  expect(() => bad.getOrThrow()).toThrow('"boom"');
  expect(() => err(new Error('real')).getOrThrow()).toThrow('real');
  expect(sequence([ok(1), ok(2)]).getOrThrow()).toEqual([1, 2]);
  expect(sequence([ok<number, string>(1), err<number, string>('x'), ok<number, string>(3)]).fold((e) => e, () => '')).toBe('x');
});

test('Optional combinators', () => {
  expect(some(2).map((x) => x + 1).getOrElse(0)).toBe(3);
  expect(some(2).flatMap(() => none<number>()).isNone()).toBe(true);
  expect(some(2).fold(() => 'n', (v) => `s${v}`)).toBe('s2');
  expect(some(1).isSome()).toBe(true);
  expect(some(1).isNone()).toBe(false);
  expect(none<number>().map((x) => x + 1).getOrElse(7)).toBe(7);
  expect(none<number>().flatMap((x) => some(x)).isSome()).toBe(false);
  expect(none<number>().fold(() => 'n', () => 's')).toBe('n');
  expect(fromNullable(null).isNone()).toBe(true);
  expect(fromNullable(0).isSome()).toBe(true);
});

test('every method on every variant', () => {
  const variants = [ok<number, string>(1), err<number, string>('e')];
  for (const v of variants) {
    expect(typeof v.ok).toBe('boolean');
    expect(v.isOk()).toBe(v.ok);
    expect(v.isErr()).toBe(!v.ok);
    expect(v.getOrElse(5)).toBe(v.ok ? 1 : 5);
    expect(v.map(String).isOk()).toBe(v.ok);
    expect(v.mapErr(String).isOk()).toBe(v.ok);
    expect(v.flatMap((x) => ok(x)).isOk()).toBe(v.ok);
    expect(v.fold(() => 'e', () => 'o')).toBe(v.ok ? 'o' : 'e');
  }
  for (const o of [some(1), none<number>()]) {
    expect(o.isSome()).toBe(!o.isNone());
    expect(o.map(String).isSome()).toBe(o.isSome());
    expect(o.flatMap((x) => some(x)).isSome()).toBe(o.isSome());
    expect(o.fold(() => 0, (x) => x)).toBe(o.isSome() ? 1 : 0);
    expect(o.getOrElse(9)).toBe(o.isSome() ? 1 : 9);
  }
});
