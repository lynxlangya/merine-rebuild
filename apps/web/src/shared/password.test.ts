import assert from 'node:assert/strict';
import { test } from 'node:test';
import { validatePasswordLength } from './password.ts';

test('接受 BCrypt 的 72 字节边界，空值交给必填规则处理', async () => {
  for (const value of ['', 'x'.repeat(72), '字'.repeat(24), '😀'.repeat(18)]) {
    await assert.doesNotReject(validatePasswordLength(value));
  }
});

test('按 UTF-8 字节数拒绝超限输入，不按字符数放行或截断', async () => {
  for (const value of ['x'.repeat(73), '字'.repeat(25), '😀'.repeat(19)]) {
    await assert.rejects(validatePasswordLength(value), /72 个 UTF-8 字节/);
  }
});
