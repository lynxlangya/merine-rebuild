import { useState } from 'react';
import { Button, Input, Select, Space } from 'antd';
import styles from '../InformationFlowPage.module.css';

/** 输入中的查询条件与 URL 中已应用的条件分开；查询和重置统一回第一页。 */
export function FlowFilters({
  view,
  status,
  keyword,
  onSearch,
}: {
  view: 'sent' | 'received';
  status: string;
  keyword: string;
  onSearch: (status: string, keyword: string) => void;
}) {
  const [draftStatus, setDraftStatus] = useState(status);
  const [draftKeyword, setDraftKeyword] = useState(keyword);
  return (
    <form
      className={styles.filters}
      onSubmit={(event) => {
        event.preventDefault();
        onSearch(draftStatus, draftKeyword.trim());
      }}
    >
      <div className={styles.filterField}>
        <label htmlFor="flow-status">{view === 'sent' ? '发送状态' : '签收状态'}</label>
        <Select
          id="flow-status"
          value={draftStatus}
          onChange={setDraftStatus}
          options={
            view === 'sent'
              ? [
                  { value: 'all', label: '全部状态' },
                  { value: 'DRAFT', label: '草稿' },
                  { value: 'PUBLISHED', label: '已发出' },
                ]
              : [
                  { value: 'all', label: '全部状态' },
                  { value: 'pending', label: '待签收' },
                  { value: 'signed', label: '已签收' },
                ]
          }
        />
      </div>
      <div className={styles.keywordField}>
        <label htmlFor="flow-keyword">标题或编号</label>
        <Input
          id="flow-keyword"
          placeholder="搜索标题或编号"
          value={draftKeyword}
          onChange={(e) => setDraftKeyword(e.target.value)}
          allowClear
          maxLength={160}
        />
      </div>
      <Space>
        <Button type="primary" htmlType="submit">
          查询
        </Button>
        <Button
          onClick={() => {
            setDraftStatus('all');
            setDraftKeyword('');
            onSearch('all', '');
          }}
        >
          重置
        </Button>
      </Space>
    </form>
  );
}
