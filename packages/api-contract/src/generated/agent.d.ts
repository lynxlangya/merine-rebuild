export interface paths {
  '/api/agent/datasources': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['dataSourceList'];
    put?: never;
    post: operations['dataSourceCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/datasources/check': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['dataSourceCheck'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/datasources/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: operations['dataSourceUpdate'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/datasources/{id}/check': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['dataSourceCheckSaved'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/datasources/{id}/status': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['dataSourceUpdateStatus'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/query-scopes': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['queryScopeList'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/query-scopes/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['queryScopeDetail'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/query-scopes/{id}/inspect': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['queryScopeInspect'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/query-scopes/{id}/metadata': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: operations['queryScopeSaveMetadata'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/query-scopes/{id}/views': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: operations['queryScopeReplaceViews'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
}
export type webhooks = Record<string, never>;
export interface components {
  schemas: {
    ApiResponseDataSourceCheckResult: {
      code: string;
      data: components['schemas']['DataSourceCheckResult'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseDataSourceView: {
      code: string;
      data: components['schemas']['DataSourceView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultDataSourceView: {
      code: string;
      data: components['schemas']['PageResultDataSourceView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseQueryScopeDetail: {
      code: string;
      data: components['schemas']['QueryScopeDetail'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    CheckDataSourceDraft: {
      databaseName: string;
      /** @enum {string} */
      dbType: 'MYSQL';
      host: string;
      id?: string;
      password?: string;
      /** Format: int32 */
      port: number;
      username: string;
      views?: string[];
    };
    DataSourceCheckResult: {
      /** Format: date-time */
      checkedAt?: string;
      detectedViews?: string[];
      message?: string;
      /** @enum {string} */
      outcome?:
        | 'PASSED'
        | 'CONNECTION_FAILED'
        | 'WRITE_PRIVILEGE'
        | 'AUTHORIZATION_UNCONFIRMED'
        | 'VIEW_DENIED';
      /** Format: int32 */
      sourceVersion?: number;
      viewColumns?: components['schemas']['DataSourceViewColumn'][];
    };
    DataSourceStatusChange: {
      /** @enum {string} */
      status: 'ENABLED' | 'DISABLED';
      /** Format: int32 */
      version: number;
    };
    DataSourceView: {
      databaseName?: string;
      /** @enum {string} */
      dbType?: 'MYSQL';
      host?: string;
      id?: string;
      /** Format: date-time */
      lastCheckAt?: string;
      lastCheckMessage?: string;
      /** @enum {string} */
      lastCheckStatus?:
        | 'PASSED'
        | 'CONNECTION_FAILED'
        | 'WRITE_PRIVILEGE'
        | 'AUTHORIZATION_UNCONFIRMED'
        | 'VIEW_DENIED';
      name?: string;
      /** Format: int32 */
      port?: number;
      remark?: string;
      /** Format: int32 */
      semanticVersion?: number;
      /** @enum {string} */
      status?: 'ENABLED' | 'DISABLED';
      /** Format: date-time */
      updatedAt?: string;
      username?: string;
      /** Format: int32 */
      version?: number;
      views?: string[];
    };
    DataSourceViewColumn: {
      comment?: string;
      name?: string;
      nullable?: boolean;
      type?: string;
    };
    FieldError: {
      field: string;
      message: string;
    };
    PageResultDataSourceView: {
      items: components['schemas']['DataSourceView'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    QueryScopeColumn: {
      description?: string;
      name: string;
    };
    QueryScopeDetail: {
      meta?: components['schemas']['ScopeViewMeta'][];
      source?: components['schemas']['DataSourceView'];
    };
    ReplaceQueryScopeViews: {
      /** Format: int32 */
      version: number;
      views: string[];
    };
    SaveDataSource: {
      databaseName: string;
      /** @enum {string} */
      dbType: 'MYSQL';
      host: string;
      name: string;
      /** @description 新建必填；编辑留空保留原密码。只保存到服务端密文，不回显 */
      password?: string;
      /** Format: int32 */
      port: number;
      remark?: string;
      username: string;
      /** Format: int32 */
      version?: number;
      /**
       * @deprecated
       * @description 已忽略：连接保存不修改允许查询的视图，请使用查询范围页
       */
      views?: string[];
    };
    SaveQueryScopeMetadata: {
      columns?: components['schemas']['QueryScopeColumn'][];
      description?: string;
      displayName?: string;
      /** Format: int32 */
      semanticVersion: number;
      /** Format: int32 */
      version: number;
      view: string;
    };
    ScopeColumnMeta: {
      description?: string;
      name?: string;
    };
    ScopeViewMeta: {
      columns?: components['schemas']['ScopeColumnMeta'][];
      description?: string;
      displayName?: string;
      view?: string;
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
  dataSourceList: {
    parameters: {
      query?: {
        search?: string;
        page?: number;
        pageSize?: number;
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
          '*/*': components['schemas']['ApiResponsePageResultDataSourceView'];
        };
      };
    };
  };
  dataSourceCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['SaveDataSource'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseDataSourceView'];
        };
      };
    };
  };
  dataSourceCheck: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CheckDataSourceDraft'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseDataSourceCheckResult'];
        };
      };
    };
  };
  dataSourceUpdate: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['SaveDataSource'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseDataSourceView'];
        };
      };
    };
  };
  dataSourceCheckSaved: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
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
          '*/*': components['schemas']['ApiResponseDataSourceCheckResult'];
        };
      };
    };
  };
  dataSourceUpdateStatus: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['DataSourceStatusChange'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseDataSourceView'];
        };
      };
    };
  };
  queryScopeList: {
    parameters: {
      query?: {
        search?: string;
        page?: number;
        pageSize?: number;
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
          '*/*': components['schemas']['ApiResponsePageResultDataSourceView'];
        };
      };
    };
  };
  queryScopeDetail: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
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
          '*/*': components['schemas']['ApiResponseQueryScopeDetail'];
        };
      };
    };
  };
  queryScopeInspect: {
    parameters: {
      query?: {
        view?: string;
      };
      header?: never;
      path: {
        id: string;
      };
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
          '*/*': components['schemas']['ApiResponseDataSourceCheckResult'];
        };
      };
    };
  };
  queryScopeSaveMetadata: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['SaveQueryScopeMetadata'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseQueryScopeDetail'];
        };
      };
    };
  };
  queryScopeReplaceViews: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ReplaceQueryScopeViews'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseDataSourceView'];
        };
      };
    };
  };
}
