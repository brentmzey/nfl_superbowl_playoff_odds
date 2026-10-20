import { describe as group, expect, test } from 'bun:test';
import { checkParams, defineQuery, placeholders, sortClause } from '../src/index.ts';

group('query templates', () => {
  test('placeholders are listed once, in order', () => {
    expect(placeholders('a = {:x} && (b = {:y} || c = {:x})')).toEqual(['x', 'y']);
    expect(placeholders('')).toEqual([]);
  });

  test('runtime param check catches missing, undefined and unknown params', () => {
    expect(checkParams('a = {:x}', { x: 1 })).toBeNull();
    expect(checkParams('a = {:x} && b = {:y}', { x: 1 })).toBe('missing parameter(s): y');
    expect(checkParams('a = {:x}', { x: undefined })).toBe('missing parameter(s): x');
    expect(checkParams('', { x: 1 })).toBe('unknown parameter(s): x');
  });

  test('templates are frozen and sorts join', () => {
    const q = defineQuery({ name: 'q', collection: 'i_nfl_teams', filter: '', sort: ['-str_team_code', 'created'] });
    expect(Object.isFrozen(q)).toBe(true);
    expect(Object.isFrozen(q.sort)).toBe(true);
    expect(sortClause(q.sort)).toBe('-str_team_code,created');
  });
});
