import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  ALL_PERMISSION_CODES,
  PERMISSIONS,
  hasAnyPermission,
  hasPermission,
} from './permissions.ts';

describe('权限码判断', () => {
  it('会话里没有的权限码一律视为无权限，而不是按角色名猜测', () => {
    assert.equal(hasPermission([PERMISSIONS.roleRead], PERMISSIONS.roleRead), true);
    assert.equal(hasPermission([PERMISSIONS.roleRead], PERMISSIONS.roleUpdate), false);
    assert.equal(hasPermission(undefined, PERMISSIONS.roleRead), false);
    assert.equal(hasPermission([], PERMISSIONS.unitRead), false);
  });

  it('任一权限命中即放行，用于选项类入口', () => {
    assert.equal(
      hasAnyPermission([PERMISSIONS.userRead], [PERMISSIONS.unitRead, PERMISSIONS.userRead]),
      true,
    );
    assert.equal(
      hasAnyPermission([PERMISSIONS.roleDelete], [PERMISSIONS.unitRead, PERMISSIONS.userRead]),
      false,
    );
  });
});

describe('权限码清单自检', () => {
  it('清单无重复，且都符合「模块:资源:动作」的编码格式', () => {
    assert.equal(new Set(ALL_PERMISSION_CODES).size, ALL_PERMISSION_CODES.length);
    for (const code of ALL_PERMISSION_CODES) {
      assert.match(code, /^[a-z][a-z0-9-]*(:[a-z0-9-]+){2}$/, `权限码格式不正确：${code}`);
    }
  });

  it('每个系统管理模块都有独立的查看码与按钮码', () => {
    for (const module of ['user', 'role', 'menu', 'unit', 'dict']) {
      assert.ok(ALL_PERMISSION_CODES.includes(`system:${module}:read`));
      assert.ok(
        ALL_PERMISSION_CODES.some(
          (code) => code.startsWith(`system:${module}:`) && !code.endsWith(':read'),
        ),
        `${module} 至少应有一个按钮级权限码`,
      );
    }
  });
});
