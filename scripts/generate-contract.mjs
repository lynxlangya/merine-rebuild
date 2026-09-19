import { writeFile } from 'node:fs/promises';
import openapiTS, { astToString } from 'openapi-typescript';

const base = process.env.API_BASE_URL ?? 'http://127.0.0.1:9002';
const response = await fetch(`${base}/api/openapi`, { signal: AbortSignal.timeout(15000) });
if (!response.ok) throw new Error(`OpenAPI request failed: ${response.status}`);
const schema = await response.json();
schema.servers = [{ url: '/' }];
await writeFile(
  new URL('../packages/api-contract/openapi.json', import.meta.url),
  `${JSON.stringify(schema, null, 2)}\n`,
);
await writeFile(
  new URL('../packages/api-contract/src/schema.d.ts', import.meta.url),
  astToString(await openapiTS(schema)),
);
console.log('OpenAPI snapshot and TypeScript types updated.');
