import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App } from 'antd';
import { useState } from 'react';
import { errorText } from '../../../shared/api-error';
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog';
import { deleteArchive } from '../api';
import { archives, type ArchiveKind, type ArchiveRecord } from '../model';
import { maritimeKeys } from '../queries';
import { ArchiveDrawer } from './ArchiveDrawer';

export function useArchiveMaintenance(onDeleted?: (kind: ArchiveKind, id: string) => void) {
  const [drawer, setDrawer] = useState<{
    kind: ArchiveKind;
    record?: ArchiveRecord;
    defaults?: Partial<ArchiveRecord>;
  } | null>(null);
  const [target, setTarget] = useState<{ kind: ArchiveKind; row: ArchiveRecord } | null>(null);
  const { message } = App.useApp();
  const client = useQueryClient();
  const deletion = useMutation({
    mutationFn: () => deleteArchive(target!.kind, target!.row.id, target!.row.version),
    onSuccess: async () => {
      const deleted = target!;
      await client.invalidateQueries({ queryKey: maritimeKeys.all });
      setTarget(null);
      void message.success('档案已删除');
      onDeleted?.(deleted.kind, deleted.row.id);
    },
  });
  return {
    create: (kind: ArchiveKind, defaults?: Partial<ArchiveRecord>) => setDrawer({ kind, defaults }),
    edit: (kind: ArchiveKind, record: ArchiveRecord) => setDrawer({ kind, record }),
    remove: (kind: ArchiveKind, row: ArchiveRecord) => {
      deletion.reset();
      setTarget({ kind, row });
    },
    overlays: (
      <>
        {drawer && (
          <ArchiveDrawer
            key={`${drawer.kind}-${drawer.record?.id ?? 'new'}`}
            {...drawer}
            onClose={() => setDrawer(null)}
          />
        )}
        <ConfirmDialog
          open={Boolean(target)}
          title={`删除${target ? archives[target.kind].label : '档案'}`}
          confirmLabel="删除档案"
          danger
          submitting={deletion.isPending}
          error={deletion.isError ? errorText(deletion.error) : null}
          onCancel={() => {
            if (!deletion.isPending) setTarget(null);
          }}
          onConfirm={() => deletion.mutate()}
        >
          删除“{target?.row.name}”后无法恢复。被引用的档案会由系统拒绝删除，已有关系可通过停用保留。
        </ConfirmDialog>
      </>
    ),
  };
}
