import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { MenuNode, NavigationNode } from '@merine/api-contract';
import {
  allowedChildTypes,
  findBreadcrumb,
  menuTypeLabel,
  permissionCodePrefix,
  toHomeEntries,
  toNavItems,
} from './model.ts';

const routes: Record<string, { path: string; icon: string }> = {
  'system.users': { path: '/system/users', icon: 'users-icon' },
  'system.roles': { path: '/system/roles', icon: 'roles-icon' },
};
const resolve = (key: string) => routes[key];

function navNode(partial: Partial<NavigationNode> & Pick<NavigationNode, 'id' | 'name' | 'type'>) {
  return { routeKey: null, description: null, children: [], ...partial } as NavigationNode;
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

  it('类型与权限码提示：页面权限码可用于推导按钮权限码前缀', () => {
    assert.equal(menuTypeLabel('PAGE'), '页面');
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
