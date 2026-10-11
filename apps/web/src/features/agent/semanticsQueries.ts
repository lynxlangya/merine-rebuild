import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../auth/public';
import { isForbiddenError } from '../../shared/api-error';
import { ApiError } from '../../shared/http';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { fetchSemanticCatalog } from './semanticsApi';

export function useSemanticCatalogQuery() {
  const { state } = useAuth();
  const user = state.status === 'authenticated' ? state.user : null;
  const allowed = user != null && hasPermission(user.permissionCodes, PERMISSIONS.chatUse);
  const query = useQuery({
    queryKey: ['agent', 'semantics', user?.id ?? ''] as const,
    queryFn: ({ signal }) => fetchSemanticCatalog(signal),
    enabled: allowed,
    staleTime: 60_000,
    retry: (count, error) =>
      count < 1 &&
      !isForbiddenError(error) &&
      !(
        error instanceof ApiError &&
        (error.status === 401 || error.code === 'SEMANTIC_SCHEMA_UNSUPPORTED')
      ),
  });
  return { allowed, query };
}
