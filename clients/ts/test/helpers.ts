import { FIELD_SPECS, type CollectionName, type CollectionRecords } from '../src/generated/schema.ts';

let seq = 0;

/** A record that satisfies the generated field specs for [collection], with optional overrides. */
export const makeRecord = <C extends CollectionName>(collection: C, overrides: Partial<CollectionRecords[C]> = {}): CollectionRecords[C] => {
  const base: Record<string, unknown> = { id: `rec${(seq++).toString().padStart(11, '0')}`, collectionId: collection, collectionName: collection };
  for (const [field, spec] of Object.entries(FIELD_SPECS[collection])) {
    base[field] = {
      text: field === 'str_status_code' ? 'ACTIVE' : `${field}_value`,
      date: '2026-10-19 00:00:00.000Z',
      autodate: '2026-10-19 12:00:00.000Z',
      relation: 'relid000000001',
      number: 1,
      bool: true,
      json: [0.5, 0.5],
    }[spec.kind];
  }
  return { ...base, ...overrides } as unknown as CollectionRecords[C];
};

export interface Captured { readonly url: URL; readonly init: RequestInit | undefined }

/** A fetch double that records requests and answers from [respond]. */
export const mockFetch = (respond: (url: URL, call: number) => Response | Promise<Response>) => {
  const calls: Captured[] = [];
  const fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = new URL(input instanceof Request ? input.url : input.toString());
    calls.push({ url, init });
    return respond(url, calls.length);
  };
  return { fetch, calls };
};

export const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

/** PocketBase list page body. */
export const page = (items: unknown[], pageNo = 1, perPage = 500): Response =>
  json({ page: pageNo, perPage, totalItems: -1, totalPages: -1, items });
