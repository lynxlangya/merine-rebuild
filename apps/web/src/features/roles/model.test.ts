import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  EMPTY_ROLE_FILTERS,
  collectPermissionCodes,
  deriveCheckState,
  deleteBlockedReason,
  holdsRole,
  isPageOutOfRangeError,
  isRoleEnabled,
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

  it('目录与页面按子树收集权限码，目录自己不带码', () => {
    assert.deepEqual(collectPermissionCodes(tree[0]), ['system:user:read', 'system:user:create']);
    assert.equal(tree[0].code, null);
    assert.equal(tree[0].key, 'menu:1');
  });

  it('全选时父行为勾选，只选页面时父行为半选——回显与保存的集合一致', () => {
    const all = deriveCheckState(tree, new Set(['system:user:read', 'system:user:create']));
    // 目录的 key 是菜单 id，页面与按钮的 key 就是各自的权限码
    assert.deepEqual(all.checked, ['menu:1', 'system:user:read', 'system:user:create']);
    assert.deepEqual(all.halfChecked, []);

    const pageOnly = deriveCheckState(tree, new Set(['system:user:read']));
    assert.deepEqual(pageOnly.checked, []);
    assert.deepEqual(pageOnly.halfChecked.sort(), ['system:user:read', 'menu:1'].sort());

    const none = deriveCheckState(tree, new Set());
    assert.deepEqual(none.checked, []);
    assert.deepEqual(none.halfChecked, []);
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
