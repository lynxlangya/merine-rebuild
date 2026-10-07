import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Drawer, Form, Input, Select, Space } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/http';
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog';
import { errorText } from '../../../shared/api-error';
import { DICTIONARY_CODES, toDictOptions, useDictionary } from '../../dictionaries/public';
import { saveArchive } from '../api';
import { archives, writeInput, type ArchiveKind, type ArchiveRecord } from '../model';
import { maritimeKeys } from '../queries';
import styles from '../Maritime.module.css';
import { ArchiveSelect } from './ArchiveSelect';

export function ArchiveDrawer({
  kind,
  record,
  defaults,
  onClose,
}: {
  kind: ArchiveKind;
  record?: ArchiveRecord;
  defaults?: Partial<ArchiveRecord>;
  onClose: () => void;
}) {
  const [form] = Form.useForm<Partial<ArchiveRecord>>();
  const [dirty, setDirty] = useState(false);
  const [statusValues, setStatusValues] = useState<Partial<ArchiveRecord> | null>(null);
  const { message, modal } = App.useApp();
  const client = useQueryClient();
  const status = useDictionary(DICTIONARY_CODES.status);
  const inhabitation = useDictionary(DICTIONARY_CODES.islandInhabitation);
  const stationId = Form.useWatch('policeStationId', form);
  const save = useMutation({
    mutationFn: (values: Partial<ArchiveRecord>) =>
      saveArchive(kind, writeInput(kind, values, record?.version), record?.id),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: maritimeKeys.all });
      void message.success(record ? '档案已保存' : '档案已新增');
      onClose();
    },
    onError: (error) => {
      if (error instanceof ApiError && error.fieldErrors.length)
        form.setFields(
          error.fieldErrors.map((field) => ({
            name: field.field as keyof ArchiveRecord,
            errors: [field.message],
          })),
        );
    },
  });
  const close = () => {
    if (save.isPending) return;
    if (dirty)
      modal.confirm({
        title: '放弃尚未保存的修改？',
        content: '关闭后本次输入不会保存。',
        okText: '放弃修改',
        cancelText: '继续编辑',
        onOk: onClose,
      });
    else onClose();
  };
  const nameRule = [{ required: true, whitespace: true, message: '请输入名称' }, { max: 120 }];
  return (
    <>
      <Drawer
        open
        width={640}
        title={`${record ? '编辑' : '新建'}${archives[kind].label}${record ? ' · ' + record.name : ''}`}
        onClose={close}
        mask={{ closable: !save.isPending }}
        keyboard={!save.isPending}
        extra={
          <Space>
            <Button onClick={close} disabled={save.isPending}>
              取消
            </Button>
            <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
              保存档案
            </Button>
          </Space>
        }
      >
        <Form
          name="maritimeArchive"
          form={form}
          disabled={save.isPending || Boolean(statusValues)}
          layout="vertical"
          initialValues={{
            status: 'ENABLED',
            inhabitationType: 'INHABITED',
            ...defaults,
            ...record,
          }}
          onValuesChange={(changed) => {
            setDirty(true);
            save.reset();
            if ('policeStationId' in changed && kind === 'wharfs')
              form.setFieldValue('responsibleOfficerId', undefined);
          }}
          onFinish={(values) => {
            if (record && record.status !== values.status) setStatusValues(values);
            else save.mutate(values);
          }}
        >
          {save.isError && (
            <Alert
              type="error"
              showIcon
              title="保存失败，输入已保留"
              description={errorText(save.error)}
              style={{ marginBottom: 16 }}
            />
          )}
          <div className={styles.drawerGrid}>
            <Form.Item
              name="name"
              label={kind === 'port-officers' ? '姓名' : '名称'}
              rules={nameRule}
              className={styles.full}
            >
              <Input placeholder="请输入" maxLength={120} autoFocus />
            </Form.Item>
            {kind !== 'port-officers' && (
              <>
                <Form.Item
                  name="region"
                  label="所在区域"
                  rules={[
                    { required: true, whitespace: true, message: '请输入所在区域' },
                    { max: 120 },
                  ]}
                >
                  <Input placeholder="请输入" maxLength={120} />
                </Form.Item>
                <Form.Item name="status" label="状态" rules={[{ required: true }]}>
                  <Select placeholder="请选择" options={toDictOptions(status.data)} />
                </Form.Item>
                <Form.Item name="location" label="位置描述" className={styles.full}>
                  <Input.TextArea
                    placeholder="请输入位置描述，如街道、港区或周边水域"
                    maxLength={200}
                    showCount
                    rows={2}
                  />
                </Form.Item>
              </>
            )}
            {['ports', 'wharfs', 'anchorages'].includes(kind) && (
              <Form.Item name="purpose" label="用途" className={styles.full}>
                <Input placeholder="请输入" maxLength={200} />
              </Form.Item>
            )}
            {kind === 'islands' && (
              <Form.Item name="inhabitationType" label="居住类型" rules={[{ required: true }]}>
                <Select placeholder="请选择" options={toDictOptions(inhabitation.data)} />
              </Form.Item>
            )}
            {kind === 'wharfs' && (
              <Form.Item name="portId" label="所属港口（独立码头可留空）" className={styles.full}>
                <ArchiveSelect
                  kind="ports"
                  current={
                    record?.portId
                      ? {
                          id: record.portId,
                          name: record.portName ?? '',
                          status: record.portStatus ?? null,
                        }
                      : undefined
                  }
                />
              </Form.Item>
            )}
            {['wharfs', 'port-officers'].includes(kind) && (
              <Form.Item
                name="policeStationId"
                label={kind === 'wharfs' ? '管辖派出所' : '所属派出所'}
                rules={
                  kind === 'port-officers' ? [{ required: true, message: '请选择所属派出所' }] : []
                }
                className={styles.full}
              >
                <ArchiveSelect
                  kind="police-stations"
                  current={
                    record?.policeStationId
                      ? {
                          id: record.policeStationId,
                          name: record.policeStationName ?? '',
                          status: record.policeStationStatus ?? null,
                        }
                      : undefined
                  }
                />
              </Form.Item>
            )}
            {kind === 'wharfs' && (
              <>
                <Form.Item
                  name="responsibleOfficerId"
                  label="主要责任民警"
                  extra="更换管辖派出所后，请重新选择民警。"
                  className={styles.full}
                >
                  <ArchiveSelect
                    key={stationId ?? 'none'}
                    kind="port-officers"
                    stationId={stationId}
                    disabled={!stationId}
                    current={
                      record?.responsibleOfficerId && stationId === record.policeStationId
                        ? {
                            id: record.responsibleOfficerId,
                            name: record.responsibleOfficerName ?? '',
                            status: record.responsibleOfficerStatus ?? null,
                          }
                        : undefined
                    }
                  />
                </Form.Item>
              </>
            )}
            {kind === 'port-officers' && (
              <>
                <Form.Item name="duty" label="职务">
                  <Input placeholder="请输入" maxLength={120} />
                </Form.Item>
                <Form.Item name="status" label="状态">
                  <Select placeholder="请选择" options={toDictOptions(status.data)} />
                </Form.Item>
              </>
            )}
          </div>
        </Form>
      </Drawer>
      <ConfirmDialog
        open={Boolean(statusValues)}
        title={`${statusValues?.status === 'DISABLED' ? '停用' : '启用'}${archives[kind].label}`}
        confirmLabel="确认并保存"
        submitting={save.isPending}
        error={save.isError ? errorText(save.error) : null}
        onCancel={() => {
          if (!save.isPending) setStatusValues(null);
        }}
        onConfirm={() => {
          if (statusValues) save.mutate(statusValues);
        }}
      >
        {statusValues?.status === 'DISABLED'
          ? `停用“${record?.name}”后将不再用于新增关联，已有关系继续显示。`
          : `启用“${record?.name}”后可以重新用于新增关联。`}
      </ConfirmDialog>
    </>
  );
}
