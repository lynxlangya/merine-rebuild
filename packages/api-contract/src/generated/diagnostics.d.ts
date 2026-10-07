export interface paths {
  '/api/bootstrap': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询本地工程与数据库联调状态 */
    get: operations['bootstrapStatus'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/bootstrap/probes': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    /** 写入一条本地合成联调记录 */
    post: operations['bootstrapCreate'];
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
    ApiResponseBootstrapStatus: {
      code: string;
      data: components['schemas']['BootstrapStatus'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseProbeRecord: {
      code: string;
      data: components['schemas']['ProbeRecord'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    BootstrapStatus: {
      application: string;
      database: string;
      recentProbes: components['schemas']['ProbeRecord'][];
      /** Format: date-time */
      serverTime: string;
      /** Format: int64 */
      totalProbes: number;
    };
    CreateProbe: {
      note: string;
    };
    FieldError: {
      field: string;
      message: string;
    };
    ProbeRecord: {
      /** Format: date-time */
      createdAt: string;
      id: string;
      note: string;
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
  bootstrapStatus: {
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
          '*/*': components['schemas']['ApiResponseBootstrapStatus'];
        };
      };
    };
  };
  bootstrapCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateProbe'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseProbeRecord'];
        };
      };
    };
  };
}
