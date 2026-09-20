/** 与服务端 BCrypt 编码边界一致；按 UTF-8 字节数校验，不截断用户输入。 */
export function validatePasswordLength(value = ''): Promise<void> {
  return new TextEncoder().encode(value).length > 72
    ? Promise.reject(new Error('密码不能超过 72 个 UTF-8 字节（中文通常占 3 字节）'))
    : Promise.resolve();
}
