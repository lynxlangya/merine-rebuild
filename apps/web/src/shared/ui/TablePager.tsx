import styles from './TablePager.module.css';

/** 页码按钮：首尾与当前页前后一页，其余折叠，避免页数多时铺满一行。 */
function pageItems(current: number, pageCount: number): (number | 'gap')[] {
  if (pageCount <= 7) return Array.from({ length: pageCount }, (_value, index) => index + 1);
  const wanted = [1, current - 1, current, current + 1, pageCount]
    .filter((page) => page >= 1 && page <= pageCount)
    .sort((left, right) => left - right);
  const items: (number | 'gap')[] = [];
  let previous = 0;
  for (const page of wanted) {
    if (previous && page - previous > 1) items.push('gap');
    items.push(page);
    previous = page;
  }
  return items;
}

/**
 * 服务端分页的页脚：总数、区间与页码按钮。
 * 只负责呈现与回调，翻页语义（回第一页、清空选中）由使用它的表格/页面决定。
 */
export function TablePager({
  total,
  page,
  pageSize,
  itemCount,
  onPageChange,
}: {
  total: number;
  page: number;
  pageSize: number;
  /** 当前页实际返回的行数：用于显示区间，不能按 pageSize 推断最后一页 */
  itemCount: number;
  onPageChange: (page: number) => void;
}) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const from = itemCount === 0 ? 0 : (page - 1) * pageSize + 1;
  const to = itemCount === 0 ? 0 : from + itemCount - 1;

  return (
    <div className={styles.pager}>
      <span>
        共 {total} 条 · 每页 {pageSize} 条 · {total === 0 ? '本页 0 条' : `本页 ${from}–${to}`}
      </span>
      <div className={styles.pagerPages}>
        <button
          type="button"
          className={styles.pagerBtn}
          aria-label="上一页"
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
        >
          ‹
        </button>
        {pageItems(page, pageCount).map((item, index) =>
          item === 'gap' ? (
            <span key={`gap-${index}`} className={styles.pagerGap}>
              …
            </span>
          ) : (
            <button
              key={item}
              type="button"
              className={styles.pagerBtn}
              aria-current={item === page ? 'page' : undefined}
              onClick={() => onPageChange(item)}
            >
              {item}
            </button>
          ),
        )}
        <button
          type="button"
          className={styles.pagerBtn}
          aria-label="下一页"
          disabled={page >= pageCount}
          onClick={() => onPageChange(page + 1)}
        >
          ›
        </button>
      </div>
    </div>
  );
}
