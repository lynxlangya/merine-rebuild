export interface paths {
  '/api/auth/csrf': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 获取 CSRF 令牌 */
    get: operations['authCsrf'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/auth/dev/accounts': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 列出本地开发环境可登录的账号 */
    get: operations['devLoginAccounts'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/auth/dev/session': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    /** 本地开发环境选择账号建立会话 */
    post: operations['devLoginLogin'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/auth/session': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 获取当前身份 */
    get: operations['authCurrent'];
    put?: never;
    /** 登录并建立会话 */
    post: operations['authLogin'];
    /** 退出并作废当前会话 */
    delete: operations['authLogout'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
}
export type webhooks = Record<string, never>;
export interface components {
  schemas: {
    ApiResponseAuthUserResponse: {
      code: string;
      data: components['schemas']['AuthUserResponse'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListDevLoginAccountOption: {
      code: string;
      data: components['schemas']['DevLoginAccountOption'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseVoid: {
      code: string;
      data: unknown;
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    AuthUserResponse: {
      /**
       * Format: int32
       * @description 授权版本；角色、角色权限或单位变化时递增，旧会话据此失效
       */
      authorizationVersion: number;
      displayName: string;
      id: string;
      loginName: string;
      /** @description 有效功能权限码；内置管理员角色已在此展开为全部权限码 */
      permissionCodes: string[];
      /** @description 角色编码，按编码升序，与 roleNames 下标一一对应 */
      roleCodes: string[];
      roleNames: string[];
      unitName: string;
    };
    CsrfToken: {
      headerName?: string;
      parameterName?: string;
      token?: string;
    };
    DevLoginAccountOption: {
      displayName: string;
      loginName: string;
      /** @description 直属上级单位名称；总队为 null */
      parentUnitName?: string | null;
      /**
       * Format: int32
       * @description 1 总队，2 支队，3 大队
       */
      unitLevel: number;
      unitName: string;
    };
    DevLoginRequest: {
      loginName: string;
      rememberMe?: boolean;
    };
    FieldError: {
      field: string;
      message: string;
    };
    LoginRequest: {
      loginName: string;
      /** @description UTF-8 编码不超过 72 字节 */
      password: string;
      /** @description 勾选后延长本次会话的空闲超时；省略或为 null 表示不保持登录 */
      rememberMe?: boolean;
    };
  };
  responses: never;
  parameters: never;
  requestBodies: never;
  headers: never;
  pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
  authCsrf: {
    parameters: {
      query: {
        csrfToken: components['schemas']['CsrfToken'];
      };
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseVoid'];
        };
      };
    };
  };
  devLoginAccounts: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseListDevLoginAccountOption'];
        };
      };
    };
  };
  devLoginLogin: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['DevLoginRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseAuthUserResponse'];
        };
      };
    };
  };
  authCurrent: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseAuthUserResponse'];
        };
      };
    };
  };
  authLogin: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['LoginRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseAuthUserResponse'];
        };
      };
    };
  };
  authLogout: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody?: never;
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseVoid'];
        };
      };
    };
  };
}
