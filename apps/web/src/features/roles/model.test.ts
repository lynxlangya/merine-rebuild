import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  EMPTY_ROLE_FILTERS,
  deriveCheckState,
  deleteBlockedReason,
  holdsRole,
  isPageOutOfRangeError,
  isRoleEnabled,
  selectAllPermissions,
  togglePermission,
  toPermissionNodes,
  toRoleFormFieldErrors,
  toRoleListQuery,
} from './model.ts';
import { ApiError } from '../../shared/http.ts';

describe('角色查询模型', () => {
  it('条件变化回到第一页，每页条数与后端默认值一致', () => {
    assert.deepEqual(toRoleListQuery(EMPTY_ROLE_FILTERS), {
      keyword: '',
      status: '',
      page: 1,
      pageSize: 20,
    });
    assert.equal(toRoleListQuery({ keyword: '运维', status: 'ENABLED' }, 3).page, 3);
  });

  it('状态判定与错误分类只在这里比较一次', () => {
    assert.equal(isRoleEnabled('ENABLED'), true);
    assert.equal(isRoleEnabled('DISABLED'), false);
    assert.equal(isPageOutOfRangeError(new ApiError('越界', 400, 'PAGE_OUT_OF_RANGE')), true);
    assert.equal(isPageOutOfRangeError(new ApiError('其他', 400, 'INVALID_PAGE')), false);
  });
});

describe('删除按钮的可用性', () => {
  it('内置角色与仍有成员的角色都写明不能删除的原因', () => {
    assert.equal(deleteBlockedReason({ builtin: true, userCount: 0 }), '内置管理员角色不能删除');
    assert.equal(
      deleteBlockedReason({ builtin: false, userCount: 3 }),
      '还有 3 个账号在用，不能删除',
    );
    assert.equal(deleteBlockedReason({ builtin: false, userCount: 0 }), null);
  });
});

describe('当前登录人与角色的关系', () => {
  it('只有会话里的角色编码命中时才算持有', () => {
    assert.equal(holdsRole(['A', 'B'], 'B'), true);
    assert.equal(holdsRole(['A'], 'B'), false);
    assert.equal(holdsRole(undefined, 'B'), false);
  });
});

describe('权限勾选树', () => {
  const tree = toPermissionNodes([
    {
      id: '1',
      parentId: null,
      type: 'DIRECTORY',
      name: '系统管理',
      routeKey: null,
      permissionCode: null,
      description: null,
      sortOrder: 0,
      status: 'ENABLED',
      version: 0,
      updatedAt: '2026-09-20T00:00:00Z',
      children: [
        {
          id: '2',
          parentId: '1',
          type: 'PAGE',
          name: '用户管理',
          routeKey: 'system.users',
          permissionCode: 'system:user:read',
          description: null,
          sortOrder: 0,
          status: 'ENABLED',
          version: 0,
          updatedAt: '2026-09-20T00:00:00Z',
          children: [
            {
              id: '3',
              parentId: '2',
              type: 'BUTTON',
              name: '新建用户',
              routeKey: null,
              permissionCode: 'system:user:create',
              description: null,
              sortOrder: 0,
              status: 'ENABLED',
              version: 0,
              updatedAt: '2026-09-20T00:00:00Z',
              children: [],
            },
          ],
        },
      ],
    },
  ] as never);

  it('目录仅作分组，不带权限码，点击目录不修改已有授权', () => {
    assert.equal(tree[0].code, null);
    assert.equal(tree[0].key, 'menu:1');
    assert.deepEqual(togglePermission(tree[0], new Set(['system:user:read']), true), [
      'system:user:read',
    ]);
    assert.deepEqual(togglePermission(tree[0], new Set(['system:user:read']), false), [
      'system:user:read',
    ]);
  });

  it('只选页面仍完整勾选，按钮全选或全不选都不改变页面的回显', () => {
    const all = deriveCheckState(tree, new Set(['system:user:read', 'system:user:create']));
    assert.deepEqual(all.checked, ['system:user:read', 'system:user:create']);
    assert.deepEqual(all.halfChecked, []);

    const pageOnly = deriveCheckState(tree, new Set(['system:user:read']));
    assert.deepEqual(pageOnly.checked, ['system:user:read']);
    assert.deepEqual(pageOnly.halfChecked, []);

    const buttonOnly = deriveCheckState(tree, new Set(['system:user:create']));
    assert.deepEqual(buttonOnly.checked, ['system:user:create']);
    assert.deepEqual(buttonOnly.halfChecked, []);

    const none = deriveCheckState(tree, new Set());
    assert.deepEqual(none.checked, []);
    assert.deepEqual(none.halfChecked, []);
  });

  const page = tree[0].children![0];
  const button = page.children![0];

  it('勾选和取消页面只改页面自身，不添加或删除按钮权限', () => {
    assert.deepEqual(togglePermission(page, new Set(), true), ['system:user:read']);
    assert.deepEqual(togglePermission(page, new Set(['system:user:create']), true), [
      'system:user:create',
      'system:user:read',
    ]);
    assert.deepEqual(
      togglePermission(page, new Set(['system:user:read', 'system:user:create']), false),
      ['system:user:create'],
    );
  });

  it('只读角色添加再取消最后一个按钮，页面权限始终保留', () => {
    const initial = new Set(['system:user:read']);
    const withButton = togglePermission(button, initial, true);
    const removed = togglePermission(button, new Set(withButton), false);
    assert.deepEqual(removed, ['system:user:read']);
    assert.deepEqual([...initial], ['system:user:read']);
    assert.deepEqual(deriveCheckState(tree, new Set(removed)).checked, ['system:user:read']);
  });

  it('全选所有启用权限，重复执行不会产生重复，且保留树外权限', () => {
    const initial = new Set(['agent:chat:use']);
    const all = selectAllPermissions(tree, initial);
    assert.deepEqual(all, ['agent:chat:use', 'system:user:create', 'system:user:read']);
    assert.deepEqual(selectAllPermissions(tree, new Set(all)), all);
    assert.deepEqual([...initial], ['agent:chat:use']);
  });

  it('全选不新增停用权限，不抹掉已有停用授权；单项可明确移除', () => {
    const stopped = { ...button, status: 'DISABLED' };
    const stoppedTree = [{ ...page, children: [stopped] }];
    assert.deepEqual(selectAllPermissions(stoppedTree, new Set()), ['system:user:read']);
    assert.deepEqual(selectAllPermissions(stoppedTree, new Set(['system:user:create'])), [
      'system:user:create',
      'system:user:read',
    ]);
    assert.deepEqual(togglePermission(stopped, new Set(), true), []);
    assert.deepEqual(togglePermission(stopped, new Set(['system:user:create']), false), []);
  });

  it('清空后所有权限均取消，不产生目录勾选或半选；空树全选保留原集合', () => {
    assert.deepEqual(deriveCheckState(tree, new Set()), { checked: [], halfChecked: [] });
    assert.deepEqual(selectAllPermissions([], new Set(['agent:chat:use'])), ['agent:chat:use']);
  });
});

describe('表单字段错误映射', () => {
  it('带下标的权限字段落回 permissionCodes，认不出的字段作为整体提示返回', () => {
    const mapped = toRoleFormFieldErrors([
      { field: 'permissionCodes[0]', message: '权限不存在：system:role:delete' },
      { field: 'version', message: '缺少编辑版本' },
    ]);
    assert.deepEqual(mapped.fields.permissionCodes, '权限不存在：system:role:delete');
    assert.deepEqual(mapped.rest, ['缺少编辑版本']);
  });
});
