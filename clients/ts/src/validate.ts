import { err, ok, type Result } from './fp.ts';
import type { NflClientError } from './errors.ts';
import { FIELD_SPECS, type CollectionName, type CollectionRecords, type FieldKind } from './generated/schema.ts';

const describeValue = (v: unknown): string => (v === null ? 'null' : Array.isArray(v) ? 'array' : typeof v);

const matches = (kind: FieldKind, required: boolean, v: unknown): boolean => {
  switch (kind) {
    case 'text':
    case 'date':
    case 'autodate':
      return typeof v === 'string' && (!required || v.length > 0);
    case 'relation':
      return typeof v === 'string' && v.length > 0;
    case 'number':
      return typeof v === 'number' && Number.isFinite(v);
    case 'bool':
      return typeof v === 'boolean';
    case 'json':
      return v !== undefined;
  }
};

/**
 * Checks a raw PocketBase record against the generated field specs for its collection, so callers get a value that
 * really is `CollectionRecords[C]` (or a precise validation error), not an unchecked cast.
 */
export const validateRecord = <C extends CollectionName>(collection: C, raw: unknown): Result<CollectionRecords[C], NflClientError> => {
  if (typeof raw !== 'object' || raw === null || typeof (raw as { id?: unknown }).id !== 'string') {
    return err({ kind: 'validation', collection, recordId: '?', field: 'id', expected: 'record with string id', actual: describeValue(raw) });
  }
  const record = raw as Record<string, unknown> & { id: string };
  const failure = Object.entries(FIELD_SPECS[collection]).find(([field, spec]) => !matches(spec.kind, spec.required, record[field]));
  return failure === undefined
    ? ok(record as unknown as CollectionRecords[C])
    : err({
        kind: 'validation',
        collection,
        recordId: record.id,
        field: failure[0],
        expected: `${failure[1].kind}${failure[1].required ? ' (required)' : ''}`,
        actual: describeValue(record[failure[0]]),
      });
};
