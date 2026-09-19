/* =============================================================================
   海防研判工作台 · 公共运行时
   1) 主题：亮色 / 暗黑，写入 localStorage，切换不重载页面 —— 查询条件、选中
      节点与会话状态因此天然保留。
   2) 外壳：左侧导航 + 顶栏由本文件统一渲染，保证 5 个页面结构与文案完全一致。
   3) 公共交互：抽屉 / 弹窗的焦点管理、Toast、选项卡、跨页证据与草稿传递。
   4) 合成数据：全部演示对象、来源、任务、情报集中在此，跨页共用一份。
   ============================================================================= */
(function () {
  'use strict';

  /* ------------------------------- 图标 ------------------------------- */
  var ICONS = {
    graph: '<circle cx="6" cy="6.5" r="2.6"/><circle cx="18.5" cy="8" r="2.6"/><circle cx="12" cy="17.5" r="2.6"/><path d="M8.5 7.3l7.4.6M7.1 8.9l3.6 6.4M17.3 10.4l-3.7 5"/>',
    spark: '<path d="M12 3.5l1.9 5.6 5.6 1.9-5.6 1.9L12 18.5l-1.9-5.6L4.5 11l5.6-1.9z"/><path d="M18.5 16.5l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z"/>',
    task: '<path d="M6 4.5h12v15H6zM9 2.5v4M15 2.5v4M9 11h6M9 15h4"/>',
    share: '<path d="M3.5 8.5h13l-3-3M20.5 15.5h-13l3 3"/>',
    admin: '<path d="M4 7.5h6M14 7.5h6M4 16.5h10M18 16.5h2"/><circle cx="12" cy="7.5" r="2.2"/><circle cx="16" cy="16.5" r="2.2"/>',
    users: '<path d="M9.5 11.5a3.3 3.3 0 100-6.6 3.3 3.3 0 000 6.6zM3.5 20c0-3.1 2.7-5.2 6-5.2s6 2.1 6 5.2M16.2 5.4a3 3 0 010 5.8M17.8 20c0-2.1-.6-3.7-1.7-4.7 2.8.2 4.9 2.1 4.9 4.7"/>',
    shield: '<path d="M12 3l7 2.8v5.6c0 4.2-2.9 7.7-7 9.1-4.1-1.4-7-4.9-7-9.1V5.8z"/><path d="M9 12l2.2 2.2L15.4 10"/>',
    org: '<path d="M4 20.5V8.8l8-4.3 8 4.3v11.7M9.5 20.5V15h5v5.5M8 11.5h1.5M14.5 11.5H16"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="M16.2 16.2L21 21"/>',
    filter: '<path d="M3.5 5.5h17l-6.6 7.6V20l-3.8-2.2v-4.7z"/>',
    close: '<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>',
    chevronDown: '<path d="M6.5 9.5l5.5 5.5 5.5-5.5"/>',
    chevronRight: '<path d="M9.5 6l6 6-6 6"/>',
    chevronLeft: '<path d="M14.5 6l-6 6 6 6"/>',
    collapse: '<path d="M3.5 4.5h17v15h-17zM9.5 4.5v15"/><path d="M16.5 9.5L14 12l2.5 2.5"/>',
    expand: '<path d="M3.5 4.5h17v15h-17zM9.5 4.5v15"/><path d="M13.5 9.5L16 12l-2.5 2.5"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2.2M12 19.3v2.2M2.5 12h2.2M19.3 12h2.2M5.3 5.3l1.6 1.6M17.1 17.1l1.6 1.6M5.3 18.7l1.6-1.6M17.1 6.9l1.6-1.6"/>',
    moon: '<path d="M20 14.6A8.6 8.6 0 019.4 4 8.6 8.6 0 1020 14.6z"/>',
    plus: '<path d="M12 5.5v13M5.5 12h13"/>',
    minus: '<path d="M5.5 12h13"/>',
    fit: '<path d="M4 9.5V4h5.5M20 9.5V4h-5.5M4 14.5V20h5.5M20 14.5V20h-5.5"/><path d="M9.5 9.5h5v5h-5z"/>',
    locate: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3"/>',
    refresh: '<path d="M4 12a8 8 0 0113.6-5.7L20 8.5M20 4v4.5h-4.5M20 12a8 8 0 01-13.6 5.7L4 15.5M4 20v-4.5h4.5"/>',
    reset: '<path d="M4.5 12a7.5 7.5 0 1015 0 7.5 7.5 0 00-13-5.2M4 4v4.5h4.5"/>',
    check: '<path d="M5 12.5l4.6 4.6L19 7.5"/>',
    warning: '<path d="M12 3.5l9.2 16H2.8z"/><path d="M12 9.5v4M12 16.5h.01"/>',
    error: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5v5M12 16h.01"/>',
    info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.8h.01"/>',
    download: '<path d="M12 4v11M7.5 11L12 15.5 16.5 11M5 19.5h14"/>',
    upload: '<path d="M12 15.5V4.5M7.5 9L12 4.5 16.5 9M5 19.5h14"/>',
    export: '<path d="M12 15V4M7.5 8L12 3.5 16.5 8M5 14v5.5h14V14"/>',
    send: '<path d="M4 12l15.5-7.5-4.6 15.5-3.4-6.2zM11.5 13.8l8-9.3"/>',
    inbox: '<path d="M3.5 13h5l1.2 3h4.6l1.2-3h5M6.5 4.5h11l3 8.5v6h-17v-6z"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7v5.2l3.4 2"/>',
    link: '<path d="M10.5 13.5a4 4 0 005.7 0l2.6-2.6a4 4 0 10-5.7-5.7l-1 1M13.5 10.5a4 4 0 00-5.7 0l-2.6 2.6a4 4 0 105.7 5.7l1-1"/>',
    play: '<path d="M8.5 5.5l10.5 6.5-10.5 6.5z"/>',
    stop: '<rect x="7" y="7" width="10" height="10" rx="1.5"/>',
    doc: '<path d="M6 3h8l4 4v14H6zM14 3v4h4M9 12.5h6M9 16h4"/>',
    paperclip: '<path d="M18.5 11.2l-7.4 7.4a4 4 0 01-5.7-5.7l7.9-7.9a2.7 2.7 0 013.8 3.8l-7.9 7.9a1.4 1.4 0 01-2-2l7.4-7.4"/>',
    trash: '<path d="M4.5 7h15M9 7V4.5h6V7M6.5 7l1 13.5h9L17.5 7M10.5 11v6M13.5 11v6"/>',
    edit: '<path d="M4 20h4.2L19 9.2 14.8 5 4 15.8zM14 5.8l4.2 4.2"/>',
    lock: '<path d="M5.5 11h13v9.5h-13zM9 11V7.8a3 3 0 016 0V11"/>',
    eye: '<path d="M2.5 12S6 6.2 12 6.2 21.5 12 21.5 12 18 17.8 12 17.8 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="2.8"/>',
    arrowLeft: '<path d="M19 12H5M11 6l-6 6 6 6"/>',
    arrowRight: '<path d="M5 12h14M13 6l6 6-6 6"/>',
    path: '<path d="M6 19.5a2.6 2.6 0 100-5.2 2.6 2.6 0 000 5.2zM18 9.7a2.6 2.6 0 100-5.2 2.6 2.6 0 000 5.2zM18 19.5a2.6 2.6 0 100-5.2 2.6 2.6 0 000 5.2zM8.4 16.6l7.3-4.4"/>',
    layers: '<path d="M12 3.5l8.5 4.3-8.5 4.3-8.5-4.3zM3.5 12.4l8.5 4.3 8.5-4.3M3.5 16.4l8.5 4.3 8.5-4.3"/>',
    user: '<circle cx="12" cy="8" r="3.8"/><path d="M4.5 20.5c0-3.7 3.4-6.2 7.5-6.2s7.5 2.5 7.5 6.2"/>',
    key: '<circle cx="8" cy="13" r="4.2"/><path d="M11.2 10.4L20 2.8M16.4 4.6l2.4 2.4M14.4 6.6l2 2"/>',
    branch: '<path d="M6.5 5.5v13M6.5 9.5c0-2 1.6-3.6 3.6-3.6h7.4M13.5 3.5l3 2.4-3 2.4M6.5 13.5c0 2 1.6 3.6 3.6 3.6h7.4M13.5 14.7l3 2.4-3 2.4"/>',
    history: '<path d="M3.5 12a8.5 8.5 0 1014.8-5.7M21 4v4.5h-4.5"/><path d="M12 7.5V12l3.2 2"/>',
    grid: '<path d="M4 4.5h6.5V11H4zM13.5 4.5H20V11h-6.5zM4 13h6.5v6.5H4zM13.5 13H20v6.5h-6.5z"/>'
  };

  function icon(name, cls) {
    var body = ICONS[name] || ICONS.info;
    return '<svg class="' + (cls || 'btn__icon') + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
      'stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + body + '</svg>';
  }

  /* --------------------------- 演示数据（跨页共用） --------------------------- */
  var AS_OF = '2026-10-02 09:10';   // 图谱最近一次同步完成时间
  var AS_OF_SRC = '2026-10-02 08:47'; // 资料导入最近一次完成时间

  var SOURCES = {
    '1': { idx: '1', id: 'DEMO-INOUT-001', name: '演示出港登记', at: '2026-09-18 14:20', kind: '登记记录',
           origin: '演示登记系统 · 演示库', batch: 'DEMO-B-2026-10-02-A', status: 'ok',
           quote: '出港登记：演示人员甲、演示人员乙；船舶：演示海船甲；出港地点：演示港口甲；登记时间：2026-09-18 14:20（UTC+8）。' },
    '2': { idx: '2', id: 'DEMO-DOC-001', name: '演示航次说明', at: '2026-09-19 10:05', kind: '文档',
           origin: '演示文档库 · 演示库', batch: 'DEMO-B-2026-10-02-A', status: 'ok',
           quote: '本组资料为教学用合成数据，仅用于演示系统功能，不证明任何实际活动性质。所含人员、船舶、地点、单位均为演示命名。' },
    '3': { idx: '3', id: 'DEMO-INOUT-002', name: '演示进港登记', at: '2026-09-21 09:05', kind: '登记记录',
           origin: '演示登记系统 · 演示库', batch: 'DEMO-B-2026-10-02-A', status: 'ok',
           quote: '进港登记：演示人员丙、演示人员丁；船舶：演示海船甲；进港地点：演示港口甲；登记时间：2026-09-21 09:05（UTC+8）。' },
    '4': { idx: '4', id: 'DEMO-DECL-001', name: '演示停靠申报', at: '2026-09-19 22:40', kind: '申报记录',
           origin: '演示申报系统 · 演示库', batch: 'DEMO-B-2026-10-02-A', status: 'pending',
           quote: '申报船舶：演示海船丙；停靠地点：演示港口乙；申报编号与既有记录存在重复，暂不写入图谱关系，待人工核对。' },
    '5': { idx: '5', id: 'DEMO-INOUT-003', name: '演示出港登记（第二批次）', at: '2026-10-02 07:15', kind: '登记记录',
           origin: '演示登记系统 · 演示库', batch: 'DEMO-B-2026-10-02-A', status: 'ok',
           quote: '出港登记：演示人员丙、演示人员丁；船舶：演示海船乙；出港地点：演示港口丙；登记时间：2026-10-02 07:15（UTC+8）。' },
    '6': { idx: '6', id: 'DEMO-DECL-002', name: '演示关联申报', at: '2026-10-02 07:40', kind: '申报记录',
           origin: '演示申报系统 · 演示库', batch: 'DEMO-B-2026-10-02-A', status: 'pending',
           quote: '申报船舶：演示海船丙；关联编号缺少来源字段，无法追溯至原始登记记录，待补充资料。' }
  };

  var OBJECTS = {
    'DEMO-P-001': { name: '演示人员甲', type: 'person', label: '人员',
      props: [['业务编号', 'DEMO-P-001'], ['数据范围', '演示单位甲 · 演示库'], ['首次出现', '2026-09-18 14:20'],
              ['关联记录', '1 条已加载'], ['数据更新时间', AS_OF]] },
    'DEMO-P-002': { name: '演示人员乙', type: 'person', label: '人员',
      props: [['业务编号', 'DEMO-P-002'], ['数据范围', '演示单位甲 · 演示库'], ['首次出现', '2026-09-18 14:20'],
              ['关联记录', '1 条已加载'], ['数据更新时间', AS_OF]] },
    'DEMO-P-003': { name: '演示人员丙', type: 'person', label: '人员',
      props: [['业务编号', 'DEMO-P-003'], ['数据范围', '演示单位甲 · 演示库'], ['首次出现', '2026-09-21 09:05'],
              ['关联记录', '2 条已加载'], ['数据更新时间', AS_OF]] },
    'DEMO-P-004': { name: '演示人员丁', type: 'person', label: '人员',
      props: [['业务编号', 'DEMO-P-004'], ['数据范围', '演示单位甲 · 演示库'], ['首次出现', '2026-09-21 09:05'],
              ['关联记录', '2 条已加载'], ['数据更新时间', AS_OF]] },
    'DEMO-S-001': { name: '演示海船甲', type: 'vessel', label: '船舶',
      props: [['业务编号', 'DEMO-S-001'], ['数据范围', '演示单位甲 · 演示库'], ['关联记录', '4 条已加载'],
              ['待核对关系', '1 条'], ['数据更新时间', AS_OF]] },
    'DEMO-S-002': { name: '演示海船乙', type: 'vessel', label: '船舶',
      props: [['业务编号', 'DEMO-S-002'], ['数据范围', '演示单位甲 · 演示库'], ['关联记录', '1 条已加载'],
              ['数据更新时间', AS_OF]] },
    'DEMO-S-003': { name: '演示海船丙', type: 'vessel', label: '船舶',
      props: [['业务编号', 'DEMO-S-003'], ['数据范围', '演示单位甲 · 演示库'], ['关联记录', '1 条已加载'],
              ['未加载关系', '3 条（超出当前筛选范围）'], ['数据更新时间', AS_OF]] },
    'DEMO-L-001': { name: '演示港口甲', type: 'place', label: '地点',
      props: [['业务编号', 'DEMO-L-001'], ['类型', '港口'], ['关联记录', '2 条已加载'], ['数据更新时间', AS_OF]] },
    'DEMO-L-002': { name: '演示港口乙', type: 'place', label: '地点',
      props: [['业务编号', 'DEMO-L-002'], ['类型', '港口'], ['关联记录', '1 条待核对'], ['数据更新时间', AS_OF]] },
    'DEMO-L-003': { name: '演示港口丙', type: 'place', label: '地点',
      props: [['业务编号', 'DEMO-L-003'], ['类型', '港口'], ['关联记录', '2 条已加载'], ['数据更新时间', AS_OF]] },
    'DEMO-E-001': { name: '演示出港记录', type: 'event', label: '事件', when: '2026-09-18 14:20',
      props: [['业务编号', 'DEMO-E-001'], ['发生时间', '2026-09-18 14:20（UTC+8）'], ['记录类型', '出港登记'],
              ['关联对象', '2 人 · 1 船 · 1 地点'], ['数据更新时间', AS_OF]] },
    'DEMO-E-002': { name: '演示进港记录', type: 'event', label: '事件', when: '2026-09-21 09:05',
      props: [['业务编号', 'DEMO-E-002'], ['发生时间', '2026-09-21 09:05（UTC+8）'], ['记录类型', '进港登记'],
              ['关联对象', '2 人 · 1 船 · 1 地点'], ['数据更新时间', AS_OF]] },
    'DEMO-E-003': { name: '演示停靠记录', type: 'event', label: '事件', when: '2026-09-19 22:40',
      props: [['业务编号', 'DEMO-E-003'], ['发生时间', '2026-09-19 22:40（UTC+8）'], ['记录类型', '停靠申报'],
              ['状态', '待核对：申报编号重复'], ['数据更新时间', AS_OF]] },
    'DEMO-E-004': { name: '演示出港记录（二批次）', type: 'event', label: '事件', when: '2026-10-02 07:15',
      props: [['业务编号', 'DEMO-E-004'], ['发生时间', '2026-10-02 07:15（UTC+8）'], ['记录类型', '出港登记'],
              ['关联对象', '2 人 · 1 船 · 1 地点'], ['数据更新时间', AS_OF]] }
  };

  /* 图谱：14 个对象 / 15 条关系。solid = 来源记录支持；dashed = 待核对 */
  var GRAPH = {
    nodes: [
      { id: 'DEMO-P-001', x: 66,  y: 120, t: 'person' },
      { id: 'DEMO-P-002', x: 66,  y: 250, t: 'person' },
      { id: 'DEMO-P-003', x: 66,  y: 400, t: 'person' },
      { id: 'DEMO-P-004', x: 66,  y: 535, t: 'person' },
      { id: 'DEMO-E-001', x: 272, y: 185, t: 'event' },
      { id: 'DEMO-E-002', x: 272, y: 460, t: 'event' },
      { id: 'DEMO-E-004', x: 272, y: 580, t: 'event' },
      { id: 'DEMO-L-001', x: 478, y: 50,  t: 'place' },
      { id: 'DEMO-S-001', x: 478, y: 300, t: 'vessel' },
      { id: 'DEMO-S-002', x: 478, y: 580, t: 'vessel' },
      { id: 'DEMO-E-003', x: 690, y: 190, t: 'event' },
      { id: 'DEMO-L-002', x: 690, y: 50,  t: 'place' },
      { id: 'DEMO-S-003', x: 690, y: 400, t: 'vessel', more: 3 },
      { id: 'DEMO-L-003', x: 690, y: 540, t: 'place' }
    ],
    edges: [
      { a: 'DEMO-P-001', b: 'DEMO-E-001', rel: '同船登记', src: ['1'], derived: true },
      { a: 'DEMO-P-002', b: 'DEMO-E-001', rel: '同船登记', src: ['1'], derived: true },
      { a: 'DEMO-P-003', b: 'DEMO-E-002', rel: '同船登记', src: ['3'], derived: true },
      { a: 'DEMO-P-004', b: 'DEMO-E-002', rel: '同船登记', src: ['3'], derived: true },
      { a: 'DEMO-P-003', b: 'DEMO-E-004', rel: '同船登记', src: ['5'], derived: true },
      { a: 'DEMO-P-004', b: 'DEMO-E-004', rel: '同船登记', src: ['5'], derived: true },
      { a: 'DEMO-E-001', b: 'DEMO-S-001', rel: '对应船舶', src: ['1'] },
      { a: 'DEMO-E-001', b: 'DEMO-L-001', rel: '出港于', src: ['1'] },
      { a: 'DEMO-E-002', b: 'DEMO-S-001', rel: '对应船舶', src: ['3'] },
      { a: 'DEMO-E-002', b: 'DEMO-L-001', rel: '进港于', src: ['3'] },
      { a: 'DEMO-E-004', b: 'DEMO-S-002', rel: '对应船舶', src: ['5'] },
      { a: 'DEMO-E-004', b: 'DEMO-L-003', rel: '出港于', src: ['5'] },
      { a: 'DEMO-S-001', b: 'DEMO-E-003', rel: '停靠申报', src: ['4'], pending: true },
      { a: 'DEMO-E-003', b: 'DEMO-L-002', rel: '停靠于', src: ['4'], pending: true },
      { a: 'DEMO-S-003', b: 'DEMO-L-003', rel: '关联申报', src: ['6'], pending: true }
    ]
  };

  /* 任务中心 */
  var TASKS = [
    { id: 'DEMO-T-0031', name: '演示资料导入 · 批次 A', type: '资料导入', batch: 'DEMO-B-2026-10-02-A',
      status: 'partial', total: 1000, ok: 998, fail: 2, progress: 100, last: '2026-10-02 08:47',
      note: '2 条待核对：来源编号重复' },
    { id: 'DEMO-T-0032', name: '演示图谱同步 · 2026-10-02', type: '图谱同步', batch: 'DEMO-B-2026-10-02-A',
      status: 'partial', total: 986, ok: 974, fail: 12, progress: 100, last: '2026-10-02 09:10',
      note: '12 条关系因来源缺口标记待核对，未写入图谱边' },
    { id: 'DEMO-T-0030', name: '演示资料导入 · 批次 C', type: '资料导入', batch: 'DEMO-B-2026-10-02-C',
      status: 'waiting', total: 240, ok: 0, fail: 0, progress: 0, last: '2026-10-02 09:02', note: '排在队列第 2 位' },
    { id: 'DEMO-T-0029', name: '演示资料导入 · 批次 B', type: '资料导入', batch: 'DEMO-B-2026-10-02-B',
      status: 'running', total: 1000, ok: 410, fail: 0, progress: 41, last: '2026-10-02 09:08', note: '当前处理第 411 条' },
    { id: 'DEMO-T-0028', name: '演示图谱同步 · 2026-10-01', type: '图谱同步', batch: 'DEMO-B-2026-10-01-A',
      status: 'canceling', total: 512, ok: 0, fail: 0, progress: 63, last: '2026-10-01 18:22',
      note: '已等待停止确认 4 分钟，可再次请求停止' },
    { id: 'DEMO-T-0027', name: '演示资料导入 · 批次 A（2026-09-30）', type: '资料导入', batch: 'DEMO-B-2026-09-30-A',
      status: 'failed', total: 320, ok: 0, fail: 320, progress: 100, last: '2026-09-30 15:04',
      note: '来源目录不可访问，任务已中止' },
    { id: 'DEMO-T-0026', name: '演示图谱同步 · 2026-09-25', type: '图谱同步', batch: 'DEMO-B-2026-09-25-A',
      status: 'cancelled', total: 260, ok: 128, fail: 0, progress: 49, last: '2026-09-25 11:40',
      note: '用户在 49% 处取消，已处理部分保留' },
    { id: 'DEMO-T-0025', name: '演示资料导入 · 批次 A（2026-09-18）', type: '资料导入', batch: 'DEMO-B-2026-09-18-A',
      status: 'success', total: 412, ok: 412, fail: 0, progress: 100, last: '2026-09-18 16:30', note: '' }
  ];

  /* 情报协同 */
  var INTEL = {
    id: 'DEMO-INTEL-2026-0007',
    title: '演示航次资料核对',
    from: '演示单位甲',
    status: 'sent',
    version: 'v2',
    sentAt: '2026-10-02 10:20',
    sources: ['1', '2'],
    body: [
      '来源事实：演示人员甲与演示人员乙出现在同一条出港登记中，对应船舶为演示海船甲，登记时间为 2026-09-18 14:20。[1]',
      '整理摘要：该条出港登记同时关联演示港口甲与演示海船甲；演示航次说明为教学用合成资料，不证明实际活动性质。[1][2]',
      '待核实项：现有资料未说明两人的实际活动背景；同船登记关系不足以得出其他行为或风险结论。'
    ],
    review: { by: '演示账号 · 研判甲', at: '2026-10-02 10:05', version: 'v2', note: '已核对引用 [1][2] 与图谱边来源，保留待核实项表述。' },
    receipts: [
      { unit: '演示单位乙', sign: '已签收', signAt: '2026-10-02 11:05', fb: '已反馈', fbAt: '2026-10-02 14:30', 
        fbText: '已按本单位资料核对，无补充材料。' },
      { unit: '演示单位丙', sign: '待签收', signAt: '—', fb: '未反馈', fbAt: '—', fbText: '' }
    ],
    timeline: [
      { t: '2026-10-02 10:20', unit: '演示单位甲', actor: '演示账号 · 研判甲', action: '发送情报', desc: '发送至演示单位乙、演示单位丙，正文快照 v2 已锁定。', kind: 'accent' },
      { t: '2026-10-02 11:05', unit: '演示单位乙', actor: '演示账号 · 协同乙', action: '签收', desc: '接收方确认收到本情报。', kind: 'success' },
      { t: '2026-10-02 14:30', unit: '演示单位乙', actor: '演示账号 · 协同乙', action: '追加反馈', desc: '已按本单位资料核对，无补充材料。追加反馈不覆盖原正文。', kind: 'success' },
      { t: '—', unit: '演示单位丙', actor: '—', action: '待签收', desc: '尚未签收，系统在 24 小时后提醒接收单位。', kind: '' }
    ]
  };

  /* 用户与授权 */
  var USERS = [
    { acct: 'demo.yanpan.a', name: '演示人员甲', unit: '演示单位甲', role: '单位业务人员', status: 'on',  last: '2026-10-02 09:12' },
    { acct: 'demo.xietong.b', name: '演示人员乙', unit: '演示单位乙', role: '单位业务人员', status: 'on',  last: '2026-10-01 16:40' },
    { acct: 'demo.admin.c',   name: '演示人员丙', unit: '演示单位甲', role: '系统管理员',   status: 'on',  last: '2026-10-02 08:05' },
    { acct: 'demo.view.d',    name: '演示人员丁', unit: '演示单位丙', role: '普通账号',     status: 'off', last: '2026-09-25 11:30' },
    { acct: 'demo.yjy.e',     name: '演示人员戊', unit: '演示单位甲', role: '单位业务人员', status: 'on',  last: '2026-09-30 14:22' },
    { acct: 'demo.xietong.f', name: '演示人员己', unit: '演示单位乙', role: '普通账号',     status: 'on',  last: '2026-09-29 10:05' },
    { acct: 'demo.admin.g',   name: '演示人员庚', unit: '演示单位丙', role: '系统管理员',   status: 'off', last: '2026-09-11 09:48' },
    { acct: 'demo.view.h',    name: '演示人员辛', unit: '演示单位甲', role: '普通账号',     status: 'on',  last: '2026-10-01 08:31' }
  ];

  /* ------------------------------- 工具 ------------------------------- */
  var $  = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };

  var store = {
    get: function (k, d) { try { var v = localStorage.getItem('hf.' + k); return v === null ? d : v; } catch (e) { return d; } },
    set: function (k, v) { try { localStorage.setItem('hf.' + k, v); } catch (e) {} },
    getJSON: function (k, d) { try { var v = localStorage.getItem('hf.' + k); return v ? JSON.parse(v) : d; } catch (e) { return d; } },
    setJSON: function (k, v) { try { localStorage.setItem('hf.' + k, JSON.stringify(v)); } catch (e) {} }
  };

  /* ------------------------------- 主题 ------------------------------- */
  function applyTheme(t) {
    document.documentElement.setAttribute('data-theme', t);
    $$('[data-theme-btn]').forEach(function (b) {
      b.setAttribute('aria-pressed', String(b.getAttribute('data-theme-btn') === t));
    });
    var meta = document.querySelector('meta[name="color-scheme"]');
    if (meta) meta.setAttribute('content', t === 'dark' ? 'dark light' : 'light dark');
  }
  function currentTheme() {
    var q = new URLSearchParams(location.search).get('theme');
    if (q === 'dark' || q === 'light') return q;
    var saved = store.get('theme', '');
    if (saved === 'dark' || saved === 'light') return saved;
    return 'light';
  }
  function setTheme(t) { store.set('theme', t); applyTheme(t); }
  function toggleTheme() { setTheme(document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark'); }

  /* ------------------------------- Toast ------------------------------- */
  function toast(o) {
    var host = $('.toast-stack');
    if (!host) { host = document.createElement('div'); host.className = 'toast-stack'; host.setAttribute('role', 'status'); host.setAttribute('aria-live', 'polite'); document.body.appendChild(host); }
    var map = { success: 'check', warning: 'warning', danger: 'error', info: 'info' };
    var el = document.createElement('div');
    el.className = 'toast toast--' + (o.type || 'info');
    el.innerHTML = icon(map[o.type || 'info'], 'toast__icon') +
      '<div><div class="toast__title">' + o.title + '</div>' + (o.desc ? '<div class="toast__desc">' + o.desc + '</div>' : '') + '</div>';
    host.appendChild(el);
    setTimeout(function () { el.remove(); }, o.duration || 4200);
  }

  /* ----------------------- 抽屉 / 弹窗：焦点管理 ----------------------- */
  var lastFocus = null;
  function focusables(root) {
    return $$('a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])', root)
      .filter(function (el) { return el.offsetParent !== null || el === document.activeElement; });
  }
  function openLayer(id, scrimId) {
    var el = typeof id === 'string' ? document.getElementById(id) : id;
    if (!el) return;
    lastFocus = document.activeElement;
    el.setAttribute('data-open', 'true');
    el.removeAttribute('aria-hidden');
    var scrim = scrimId ? document.getElementById(scrimId) : null;
    if (scrim) scrim.setAttribute('data-open', 'true');
    var f = focusables(el);
    if (f.length) f[0].focus();
  }
  function closeLayer(id, scrimId) {
    var el = typeof id === 'string' ? document.getElementById(id) : id;
    if (!el) return;
    el.setAttribute('data-open', 'false');
    el.setAttribute('aria-hidden', 'true');
    var scrim = scrimId ? document.getElementById(scrimId) : null;
    if (scrim) scrim.setAttribute('data-open', 'false');
    if (lastFocus && lastFocus.focus) lastFocus.focus();
  }
  function trapTab(e) {
    var open = $$('.drawer[data-open="true"], .modal[data-open="true"]');
    if (!open.length) return;
    var host = open[open.length - 1];
    var f = focusables(host);
    if (!f.length) return;
    var first = f[0], last = f[f.length - 1];
    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
  }
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Tab') trapTab(e);
    if (e.key === 'Escape') {
      var openD = $$('.drawer[data-open="true"]');
      var openM = $$('.modal[data-open="true"]');
      if (openM.length) { closeLayer(openM[openM.length - 1]); }
      else if (openD.length) { closeLayer(openD[openD.length - 1], 'scrim'); }
      else $$('[data-popover]').forEach(function (p) { p.hidden = true; });
    }
  });

  /* ------------------------------- 导航 ------------------------------- */
  var NAV = [
    { key: 'graph',  label: '数据图谱', icon: 'graph', href: 'p01-graph-workbench.html' },
    { key: 'ai',     label: 'AI 研判',  icon: 'spark', href: 'p02-ai-workbench.html', badge: '1' },
    { key: 'task',   label: '数据任务', icon: 'task',  href: 'p03-data-tasks.html', badge: '8' },
    { key: 'intel',  label: '情报协同', icon: 'share', href: 'p05-intel-collaboration.html', badge: '2' },
    { key: 'admin',  label: '系统管理', icon: 'admin', href: '', sub: [
        { key: 'user',  label: '用户管理', icon: 'users',  href: 'p04-user-management.html' },
        { key: 'role',  label: '角色',     icon: 'shield', href: '' },
        { key: 'unit',  label: '单位',     icon: 'org',    href: '' }
      ] }
  ];

  function navHTML(active) {
    var h = '';
    h += '<div class="nav__head"><div class="nav__mark">HF</div>' +
         '<div style="min-width:0"><div class="nav__title">海防研判工作台</div><div class="nav__sub">演示环境 · 合成数据</div></div></div>';
    h += '<div class="nav__body">';
    h += '<button class="nav__item" type="button" data-nav-toggle data-od-id="nav-collapse">' +
         icon('collapse', 'nav__icon') + '<span class="nav__label">收起导航</span></button>';
    h += '<div class="nav__group"><div class="nav__group-label">业务工作区</div>';
    NAV.forEach(function (n) {
      if (n.sub) {
        h += '<button class="nav__item" type="button" data-nav-group ' + (active === n.key || n.sub.some(function (s) { return s.key === active; }) ? 'aria-current="page"' : '') + '>' +
             icon(n.icon, 'nav__icon') + '<span class="nav__label">' + n.label + '</span>' + icon('chevronDown', 'nav__icon') + '</button>';
        n.sub.forEach(function (s) {
          if (s.href) {
            h += '<a class="nav__item nav__item--sub" href="' + s.href + '"' + (active === s.key ? ' aria-current="page"' : '') + '>' +
                 icon(s.icon, 'nav__icon') + '<span class="nav__label">' + s.label + '</span></a>';
          } else {
            h += '<button class="nav__item nav__item--sub" type="button" data-todo="' + s.label + '">' +
                 icon(s.icon, 'nav__icon') + '<span class="nav__label">' + s.label + '</span></button>';
          }
        });
      } else {
        h += '<a class="nav__item" href="' + n.href + '"' + (active === n.key ? ' aria-current="page"' : '') + '>' +
             icon(n.icon, 'nav__icon') + '<span class="nav__label">' + n.label + '</span>' +
             (n.badge ? '<span class="nav__badge">' + n.badge + '</span>' : '') + '</a>';
      }
    });
    h += '</div>';
    h += '<div class="nav__group"><div class="nav__group-label">交付件</div>' +
         '<a class="nav__item" href="index.html">' + icon('grid', 'nav__icon') + '<span class="nav__label">交付总览与预览</span></a>' +
         '<a class="nav__item" href="design-system.html">' + icon('layers', 'nav__icon') + '<span class="nav__label">设计系统</span></a>' +
         '</div>';
    h += '</div>';
    h += '<div class="nav__foot"><div class="row fs-11 muted" style="padding:6px 8px;line-height:1.5">' +
         '<span class="mono">v0.19.0-demo</span><span class="spacer"></span><span>桌面工作台</span></div></div>';
    return h;
  }

  function topbarHTML(opt) {
    opt = opt || {};
    var sync = opt.sync || { level: 'ok', text: '图谱数据 ' + AS_OF + ' 同步完成' };
    var syncIcon = sync.level === 'ok' ? 'check' : (sync.level === 'warn' ? 'warning' : 'error');
    var h = '';
    h += '<button class="btn btn--ghost btn--icon" type="button" data-nav-toggle title="折叠/展开导航" aria-label="折叠或展开导航">' + icon('collapse') + '</button>';
    h += '<div class="topbar__crumbs">' + (opt.crumb || []).map(function (c, i, arr) {
            return (i === arr.length - 1 ? '<b>' + c + '</b>' : '<span>' + c + '</span><span class="crumb-sep">/</span>');
         }).join('') + '</div>';
    h += '<div class="topbar__spacer"></div>';
    h += '<div class="topbar__right">';
    h += '<a class="tag ' + (sync.level === 'ok' ? 'tag--success' : (sync.level === 'warn' ? 'tag--warning' : 'tag--danger')) + '" href="p03-data-tasks.html" data-od-id="topbar-sync" title="查看数据任务">' +
         icon(syncIcon, 'btn__icon') + '<span>' + sync.text + '</span></a>';
    h += '<div class="seg" role="group" aria-label="主题模式" data-od-id="theme-switch">' +
         '<button class="seg__btn" type="button" data-theme-btn="light" aria-pressed="true">' + icon('sun') + '亮色</button>' +
         '<button class="seg__btn" type="button" data-theme-btn="dark" aria-pressed="false">' + icon('moon') + '暗黑</button></div>';
    h += '<div class="topbar__user">';
    var usr = opt.user || { initial: '甲', name: '演示账号 · 研判甲', meta: '演示单位甲 · 单位业务人员' };
    h += '<div class="avatar" aria-hidden="true">' + usr.initial + '</div>';
    h += '<div style="min-width:0"><div class="fs-12 fw-strong" style="line-height:1.3">' + usr.name + '</div>' +
         '<div class="fs-11 muted" style="line-height:1.3">' + usr.meta + '</div></div>';
    h += '</div>';
    h += '</div>';
    return h;
  }

  function mountShell(opt) {
    var nav = $('#nav'), top = $('#topbar');
    if (nav) nav.innerHTML = navHTML(opt.active);
    if (top) top.innerHTML = topbarHTML(opt);
    var saved = store.get('nav', 'expanded');
    setNav(saved);
    document.addEventListener('click', function (e) {
      var t = e.target.closest && e.target.closest('[data-nav-toggle]');
      if (t) { setNav(document.getElementById('app').getAttribute('data-nav') === 'collapsed' ? 'expanded' : 'collapsed'); }
      var td = e.target.closest && e.target.closest('[data-todo]');
      if (td) { toast({ type: 'info', title: td.getAttribute('data-todo') + ' 本轮未展开', desc: '首轮以局部组件说明，见设计系统页的权限选择与单位树。' }); }
      var tb = e.target.closest && e.target.closest('[data-theme-btn]');
      if (tb) { setTheme(tb.getAttribute('data-theme-btn')); }
    });
  }
  function setNav(state) {
    var app = document.getElementById('app');
    if (!app) return;
    app.setAttribute('data-nav', state);
    store.set('nav', state);
    $$('[data-nav-toggle]').forEach(function (b) { b.setAttribute('aria-expanded', String(state === 'expanded')); });
  }

  /* --------------------------- 跨页：证据与草稿 --------------------------- */
  function addEvidence(items) {
    var cur = store.getJSON('evidence', []);
    items.forEach(function (it) {
      if (!cur.some(function (c) { return c.key === it.key; })) cur.push(it);
    });
    store.setJSON('evidence', cur);
    return cur;
  }
  function getEvidence() { return store.getJSON('evidence', []); }
  function clearEvidence() { store.setJSON('evidence', []); }

  function saveDraft(d) { store.setJSON('draft', d); }
  function getDraft() { return store.getJSON('draft', null); }

  /* ------------------------------- 选项卡 ------------------------------- */
  function initTabs(root) {
    $$('[data-tabs]', root || document).forEach(function (bar) {
      if (bar.dataset.bound) return;
      bar.dataset.bound = '1';
      bar.addEventListener('click', function (e) {
        var btn = e.target.closest('[role="tab"]');
        if (!btn) return;
        var name = btn.getAttribute('data-tab');
        $$('[role="tab"]', bar).forEach(function (b) { b.setAttribute('aria-selected', String(b === btn)); });
        var scope = document.getElementById(bar.getAttribute('data-tabs')) || document;
        $$('[data-tab-panel]', scope).forEach(function (p) { p.hidden = p.getAttribute('data-tab-panel') !== name; });
      });
      bar.addEventListener('keydown', function (e) {
        if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
        var btns = $$('[role="tab"]', bar);
        var i = btns.indexOf(document.activeElement);
        if (i < 0) return;
        e.preventDefault();
        var n = e.key === 'ArrowRight' ? (i + 1) % btns.length : (i - 1 + btns.length) % btns.length;
        btns[n].focus(); btns[n].click();
      });
    });
  }

  /* ------------------------------- 导出 ------------------------------- */
  window.HF = {
    icon: icon, icons: ICONS, $: $, $$: $$, store: store, toast: toast,
    theme: { apply: applyTheme, current: currentTheme, set: setTheme, toggle: toggleTheme },
    layer: { open: openLayer, close: closeLayer },
    shell: { mount: mountShell, setNav: setNav },
    tabs: initTabs,
    evidence: { add: addEvidence, get: getEvidence, clear: clearEvidence },
    draft: { save: saveDraft, get: getDraft },
    data: { AS_OF: AS_OF, AS_OF_SRC: AS_OF_SRC, SOURCES: SOURCES, OBJECTS: OBJECTS, GRAPH: GRAPH,
            TASKS: TASKS, INTEL: INTEL, USERS: USERS },
    entity: { person: '人员', vessel: '船舶', place: '地点', event: '事件' },
    fmt: {
      num: function (n) { return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ','); },
      objTag: function (id) {
        var o = OBJECTS[id]; if (!o) return '';
        return '<span class="tag tag--' + o.type + '"><i class="eg eg--' + o.type + '"></i>' + o.name + '</span>';
      }
    }
  };

  document.addEventListener('DOMContentLoaded', function () {
    applyTheme(currentTheme());
    initTabs(document);
  });
})();
