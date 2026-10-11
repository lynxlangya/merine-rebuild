import type { SemanticCatalog } from '@merine/api-contract';
import { ApiError, request } from '../../shared/http';

/** 正式内容由后端统一提供，不在前端复制业务文本。 */
export async function fetchSemanticCatalog(signal?: AbortSignal) {
  const catalog = await request<SemanticCatalog>('/api/agent/semantics', { signal });
  if (catalog.schemaVersion !== 1) {
    throw new ApiError(
      '资料库内容版本与当前页面不兼容，请刷新页面后重试。',
      200,
      'SEMANTIC_SCHEMA_UNSUPPORTED',
    );
  }
  return catalog;
}
