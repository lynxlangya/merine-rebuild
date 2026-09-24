import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { MenuNode, NavigationNode } from '@merine/api-contract';
import {
  allowedChildTypes,
  defaultMenuExpandedKeys,
  findBreadcrumb,
  permissionCodePrefix,
  toHomeEntries,
  toMenuRows,
  toNavItems,
} from './model.ts';

const routes: Record<string, { path: string; icon: string }> = {
  'system.users': { path: '/system/users', icon: 'users-icon' },
  'system.roles': { path: '/system/roles', icon: 'roles-icon' },
};
const resolve = (key: string) => routes[key];

function navNode(partial: Partial<NavigationNode> & Pick<NavigationNode, 'id' | 'name' | 'type'>) {
  return {
    routeKey: null,
    iconName: null,
    description: null,
    children: [],
    ...partial,
  } as NavigationNode;
}

describe('导航树转换', () => {
  const tree: NavigationNode[] = [
    navNode({
      id: '1',
      name: '系统管理',
      type: 'DIRECTORY',
      children: [
        navNode({
          id: '2',
          name: '用户管理',
          type: 'PAGE',
          routeKey: 'system.users',
          description: '账号维护',
          children: [navNode({ id: '3', name: '新建用户', type: 'BUTTON' })],
        }),
        navNode({ id: '4', name: '角色管理', type: 'PAGE', routeKey: 'system.roles' }),
      ],
    }),
    navNode({ id: '5', name: '未注册页面', type: 'PAGE', routeKey: 'unknown.page' }),
  ];

  it('只保留目录与页面：页签/按钮属于页面内部，不进左侧菜单', () => {
    const items = toNavItems(tree, resolve);
    assert.deepEqual(
      items.map((item) => item.label),
      ['系统管理'],
    );
    assert.deepEqual(
      items[0].children?.map((child) => child.label),
      ['用户管理', '角色管理'],
    );
    assert.equal(items[0].children?.[0].path, '/system/users');
  });

  it('前端没有注册的 route key 被跳过，不会把用户带进死链', () => {
    const warn = console.warn;
    console.warn = () => {};
    try {
      const labels = toNavItems(
        [navNode({ id: '9', name: '未知页', type: 'PAGE', routeKey: 'unknown.page' })],
        resolve,
      );
      assert.deepEqual(labels, []);
    } finally {
      console.warn = warn;
    }
  });

  it('首页入口来自同一份导航树，并带上说明', () => {
    const entries = toHomeEntries(tree, resolve);
    assert.deepEqual(
      entries.map((entry) => entry.title),
      ['用户管理', '角色管理'],
    );
    assert.equal(entries[0].description, '账号维护');
    assert.equal(entries[0].path, '/system/users');
  });

  it('导航与首页入口原样带上菜单配置的图标名称，由渲染侧解析并回落到默认图标', () => {
    const configured: NavigationNode[] = [
      navNode({
        id: '1',
        name: '系统管理',
        type: 'DIRECTORY',
        iconName: 'FolderOutlined',
        children: [
          navNode({
            id: '2',
            name: '用户管理',
            type: 'PAGE',
            routeKey: 'system.users',
            iconName: 'TeamOutlined',
          }),
        ],
      }),
    ];
    const items = toNavItems(configured, resolve);
    assert.equal(items[0].iconName, 'FolderOutlined');
    assert.equal(items[0].children?.[0].iconName, 'TeamOutlined');
    assert.equal(
      items[0].children?.[0].icon,
      routes['system.users'].icon,
      '默认图标仍在，供回落用',
    );
    assert.equal(toHomeEntries(configured, resolve)[0].iconName, 'TeamOutlined');
  });

  it('面包屑按当前路径回溯「目录 → 页面」', () => {
    assert.deepEqual(findBreadcrumb(tree, '/system/users', resolve), ['系统管理', '用户管理']);
    assert.deepEqual(findBreadcrumb(tree, '/system/roles', resolve), ['系统管理', '角色管理']);
    assert.equal(findBreadcrumb(tree, '/dev/diagnostics', resolve), null);
  });
});

describe('菜单节点规则', () => {
  it('层级规则与后端一致：目录挂目录/页面，页面挂页签/按钮，页签挂按钮', () => {
    assert.deepEqual(allowedChildTypes(null), ['DIRECTORY', 'PAGE']);
    assert.deepEqual(allowedChildTypes('DIRECTORY'), ['DIRECTORY', 'PAGE']);
    assert.deepEqual(allowedChildTypes('PAGE'), ['TAB', 'BUTTON']);
    assert.deepEqual(allowedChildTypes('TAB'), ['BUTTON']);
    assert.deepEqual(allowedChildTypes('BUTTON'), []);
  });

  it('权限码提示：页面权限码可用于推导按钮权限码前缀', () => {
    assert.equal(permissionCodePrefix('system:user:read'), 'system:user:');
    assert.equal(permissionCodePrefix(null), '');
  });
});

describe('菜单树与权限码', () => {
  const node = (partial: {
    id: string;
    name: string;
    type: string;
    permissionCode: string | null;
    children?: MenuNode[];
  }): MenuNode =>
    ({
      parentId: null,
      routeKey: null,
      iconName: null,
      description: null,
      sortOrder: 0,
      status: 'ENABLED',
      version: 0,
      updatedAt: '2026-09-20T00:00:00Z',
      children: [],
      ...partial,
    }) as MenuNode;

  it('目录没有权限码，页面与按钮各带一个', () => {
    const tree = [
      node({
        id: '1',
        name: '系统管理',
        type: 'DIRECTORY',
        permissionCode: null,
        children: [
          node({ id: '2', name: '用户管理', type: 'PAGE', permissionCode: 'system:user:read' }),
        ],
      }),
    ];
    assert.equal(tree[0].permissionCode, null);
    assert.equal(tree[0].children[0].permissionCode, 'system:user:read');
  });
});

describe('菜单表格行与默认展开', () => {
  const node = (partial: {
    id: string;
    name: string;
    type: string;
    children?: MenuNode[];
  }): MenuNode =>
    ({
      parentId: null,
      routeKey: null,
      permissionCode: null,
      description: null,
      sortOrder: 0,
      status: 'ENABLED',
      version: 0,
      updatedAt: '2026-09-20T00:00:00Z',
      children: [],
      ...partial,
    }) as MenuNode;

  const tree = [
    node({
      id: 'dir:system',
      name: '系统管理',
      type: 'DIRECTORY',
      children: [
        node({
          id: 'page:users',
          name: '用户管理',
          type: 'PAGE',
          children: [
            node({ id: 'btn:user:create', name: '新建用户', type: 'BUTTON' }),
            node({ id: 'btn:user:update', name: '编辑用户', type: 'BUTTON' }),
          ],
        }),
        node({ id: 'page:roles', name: '角色管理', type: 'PAGE', children: [] }),
      ],
    }),
    node({
      id: 'dir:dev',
      name: '开发工具',
      type: 'DIRECTORY',
      children: [node({ id: 'page:diagnostics', name: '工程诊断', type: 'PAGE' })],
    }),
  ];

  it('叶子节点不带 children 字段，表格才不会给按钮画展开图标', () => {
    const rows = toMenuRows(tree);
    assert.equal('children' in rows[0].children![1], false);
    assert.equal('children' in rows[0].children![0].children![0], false);
    assert.equal(rows[0].children![0].children!.length, 2);
  });

  it('默认展开每个一级节点，再展开第一个有下级的二级节点', () => {
    assert.deepEqual(defaultMenuExpandedKeys(toMenuRows(tree)), [
      'dir:system',
      'page:users',
      'dir:dev',
    ]);
  });

  it('没有下级的树不产生任何展开项', () => {
    const flat = [node({ id: 'page:only', name: '孤立页面', type: 'PAGE' })];
    assert.deepEqual(defaultMenuExpandedKeys(toMenuRows(flat)), []);
  });
});
