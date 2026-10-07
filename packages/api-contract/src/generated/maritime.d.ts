export interface paths {
  '/api/maritime/anchorages': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 分页查询锚地 */
    get: operations['anchorageList'];
    put?: never;
    /** 新建锚地 */
    post: operations['anchorageCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/anchorages/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询锚地完整档案 */
    get: operations['anchorageDetail'];
    /** 编辑或启停锚地，需最新版本 */
    put: operations['anchorageUpdate'];
    post?: never;
    /** 删除未被引用的锚地，需最新版本 */
    delete: operations['anchorageDelete'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/islands': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 分页查询海岛 */
    get: operations['islandList'];
    put?: never;
    /** 新建海岛 */
    post: operations['islandCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/islands/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询海岛完整档案 */
    get: operations['islandDetail'];
    /** 编辑或启停海岛，需最新版本 */
    put: operations['islandUpdate'];
    post?: never;
    /** 删除未被引用的海岛，需最新版本 */
    delete: operations['islandDelete'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/options/police-stations': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['maritimeOptionsStations'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/options/port-officers': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['maritimeOptionsOfficers'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/options/ports': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['maritimeOptionsPorts'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/options/wharf-relations': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['maritimeOptionsWharfs'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/police-stations': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 分页查询派出所 */
    get: operations['policeStationList'];
    put?: never;
    /** 新建派出所 */
    post: operations['policeStationCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/police-stations/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询派出所完整档案 */
    get: operations['policeStationDetail'];
    /** 编辑或启停派出所，需最新版本 */
    put: operations['policeStationUpdate'];
    post?: never;
    /** 删除未被引用的派出所，需最新版本 */
    delete: operations['policeStationDelete'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/port-officers': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 分页查询民警 */
    get: operations['portOfficerList'];
    put?: never;
    /** 新建民警 */
    post: operations['portOfficerCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/port-officers/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询民警完整档案 */
    get: operations['portOfficerDetail'];
    /** 编辑或启停民警，需最新版本 */
    put: operations['portOfficerUpdate'];
    post?: never;
    /** 删除未被引用的民警，需最新版本 */
    delete: operations['portOfficerDelete'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/ports': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 分页查询港口 */
    get: operations['portList'];
    put?: never;
    /** 新建港口 */
    post: operations['portCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/ports/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询港口完整档案 */
    get: operations['portDetail'];
    /** 编辑或启停港口，需最新版本 */
    put: operations['portUpdate'];
    post?: never;
    /** 删除未被引用的港口，需最新版本 */
    delete: operations['portDelete'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/wharfs': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 分页查询码头 */
    get: operations['wharfList'];
    put?: never;
    /** 新建码头 */
    post: operations['wharfCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/maritime/wharfs/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询码头完整档案 */
    get: operations['wharfDetail'];
    /** 编辑或启停码头，需最新版本 */
    put: operations['wharfUpdate'];
    post?: never;
    /** 删除未被引用的码头，需最新版本 */
    delete: operations['wharfDelete'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
}
export type webhooks = Record<string, never>;
export interface components {
  schemas: {
    AnchorageView: {
      /** Format: date-time */
      createdAt: string;
      fixtureKey: string | null;
      id: string;
      location: string | null;
      name: string;
      purpose: string | null;
      region: string;
      status: string;
      /** Format: date-time */
      updatedAt: string;
      /** Format: int32 */
      version: number;
    };
    ApiResponseAnchorageView: {
      code: string;
      data: components['schemas']['AnchorageView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseIslandView: {
      code: string;
      data: components['schemas']['IslandView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultAnchorageView: {
      code: string;
      data: components['schemas']['PageResultAnchorageView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultArchiveOption: {
      code: string;
      data: components['schemas']['PageResultArchiveOption'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultIslandView: {
      code: string;
      data: components['schemas']['PageResultIslandView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultPoliceStationView: {
      code: string;
      data: components['schemas']['PageResultPoliceStationView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultPortOfficerView: {
      code: string;
      data: components['schemas']['PageResultPortOfficerView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultPortView: {
      code: string;
      data: components['schemas']['PageResultPortView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultWharfRelationView: {
      code: string;
      data: components['schemas']['PageResultWharfRelationView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultWharfView: {
      code: string;
      data: components['schemas']['PageResultWharfView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePoliceStationView: {
      code: string;
      data: components['schemas']['PoliceStationView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePortOfficerView: {
      code: string;
      data: components['schemas']['PortOfficerView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePortView: {
      code: string;
      data: components['schemas']['PortView'];
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
    ApiResponseWharfView: {
      code: string;
      data: components['schemas']['WharfView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ArchiveOption: {
      id: string;
      name: string;
      policeStationId: string | null;
      status: string;
    };
    FieldError: {
      field: string;
      message: string;
    };
    IslandView: {
      /** Format: date-time */
      createdAt: string;
      fixtureKey: string | null;
      id: string;
      inhabitationType: string;
      location: string | null;
      name: string;
      region: string;
      status: string;
      /** Format: date-time */
      updatedAt: string;
      /** Format: int32 */
      version: number;
    };
    PageResultAnchorageView: {
      items: components['schemas']['AnchorageView'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultArchiveOption: {
      items: components['schemas']['ArchiveOption'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultIslandView: {
      items: components['schemas']['IslandView'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultPoliceStationView: {
      items: components['schemas']['PoliceStationView'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultPortOfficerView: {
      items: components['schemas']['PortOfficerView'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultPortView: {
      items: components['schemas']['PortView'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultWharfRelationView: {
      items: components['schemas']['WharfRelationView'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultWharfView: {
      items: components['schemas']['WharfView'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PoliceStationView: {
      /** Format: date-time */
      createdAt: string;
      fixtureKey: string | null;
      id: string;
      location: string | null;
      name: string;
      region: string;
      status: string;
      /** Format: date-time */
      updatedAt: string;
      /** Format: int32 */
      version: number;
    };
    PortOfficerView: {
      /** Format: date-time */
      createdAt: string;
      duty: string | null;
      fixtureKey: string | null;
      id: string;
      name: string;
      policeStationId: string;
      policeStationName: string | null;
      policeStationStatus: string | null;
      region: string;
      status: string;
      /** Format: date-time */
      updatedAt: string;
      /** Format: int32 */
      version: number;
    };
    PortView: {
      /** Format: date-time */
      createdAt: string;
      fixtureKey: string | null;
      id: string;
      location: string | null;
      name: string;
      purpose: string | null;
      region: string;
      status: string;
      /** Format: date-time */
      updatedAt: string;
      /** Format: int32 */
      version: number;
    };
    WharfRelationView: {
      id: string;
      name: string;
      responsibleOfficerId: string | null;
      responsibleOfficerName: string | null;
      status: string;
    };
    WharfView: {
      /** Format: date-time */
      createdAt: string;
      fixtureKey: string | null;
      id: string;
      location: string | null;
      name: string;
      policeStationId: string | null;
      policeStationName: string | null;
      policeStationStatus: string | null;
      portId: string | null;
      portName: string | null;
      portStatus: string | null;
      purpose: string | null;
      region: string;
      responsibleOfficerId: string | null;
      responsibleOfficerName: string | null;
      responsibleOfficerStatus: string | null;
      status: string;
      /** Format: date-time */
      updatedAt: string;
      /** Format: int32 */
      version: number;
    };
    WriteAnchorage: {
      location?: string | null;
      name: string;
      purpose?: string | null;
      region: string;
      status: string;
      /**
       * Format: int32
       * @description 新建不传；编辑必填，缺失返回400，旧版本返回409
       */
      version?: number | null;
    };
    WriteIsland: {
      inhabitationType: string;
      location?: string | null;
      name: string;
      region: string;
      status: string;
      /**
       * Format: int32
       * @description 新建不传；编辑必填，缺失返回400，旧版本返回409
       */
      version?: number | null;
    };
    WritePoliceStation: {
      location?: string | null;
      name: string;
      region: string;
      status: string;
      /**
       * Format: int32
       * @description 新建不传；编辑必填，缺失返回400，旧版本返回409
       */
      version?: number | null;
    };
    WritePort: {
      location?: string | null;
      name: string;
      purpose?: string | null;
      region: string;
      status: string;
      /**
       * Format: int32
       * @description 新建不传；编辑必填，缺失返回400，旧版本返回409
       */
      version?: number | null;
    };
    WritePortOfficer: {
      duty?: string | null;
      name: string;
      policeStationId: string;
      status: string;
      /**
       * Format: int32
       * @description 新建不传；编辑必填，缺失返回400，旧版本返回409
       */
      version?: number | null;
    };
    WriteWharf: {
      location?: string | null;
      name: string;
      policeStationId?: string | null;
      portId?: string | null;
      purpose?: string | null;
      region: string;
      responsibleOfficerId?: string | null;
      status: string;
      /**
       * Format: int32
       * @description 新建不传；编辑必填，缺失返回400，旧版本返回409
       */
      version?: number | null;
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
  anchorageList: {
    parameters: {
      query?: {
        keyword?: string;
        region?: string;
        status?: string;
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
          '*/*': components['schemas']['ApiResponsePageResultAnchorageView'];
        };
      };
    };
  };
  anchorageCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['WriteAnchorage'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseAnchorageView'];
        };
      };
    };
  };
  anchorageDetail: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: number;
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
          '*/*': components['schemas']['ApiResponseAnchorageView'];
        };
      };
    };
  };
  anchorageUpdate: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: number;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['WriteAnchorage'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseAnchorageView'];
        };
      };
    };
  };
  anchorageDelete: {
    parameters: {
      query: {
        version: number;
      };
      header?: never;
      path: {
        id: number;
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
          '*/*': components['schemas']['ApiResponseVoid'];
        };
      };
    };
  };
  islandList: {
    parameters: {
      query?: {
        keyword?: string;
        region?: string;
        status?: string;
        inhabitationType?: string;
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
          '*/*': components['schemas']['ApiResponsePageResultIslandView'];
        };
      };
    };
  };
  islandCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['WriteIsland'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseIslandView'];
        };
      };
    };
  };
  islandDetail: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: number;
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
          '*/*': components['schemas']['ApiResponseIslandView'];
        };
      };
    };
  };
  islandUpdate: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: number;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['WriteIsland'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseIslandView'];
        };
      };
    };
  };
  islandDelete: {
    parameters: {
      query: {
        version: number;
      };
      header?: never;
      path: {
        id: number;
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
          '*/*': components['schemas']['ApiResponseVoid'];
        };
      };
    };
  };
  maritimeOptionsStations: {
    parameters: {
      query?: {
        keyword?: string;
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
          '*/*': components['schemas']['ApiResponsePageResultArchiveOption'];
        };
      };
    };
  };
  maritimeOptionsOfficers: {
    parameters: {
      query?: {
        keyword?: string;
        policeStationId?: number;
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
          '*/*': components['schemas']['ApiResponsePageResultArchiveOption'];
        };
      };
    };
  };
  maritimeOptionsPorts: {
    parameters: {
      query?: {
        keyword?: string;
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
          '*/*': components['schemas']['ApiResponsePageResultArchiveOption'];
        };
      };
    };
  };
  maritimeOptionsWharfs: {
    parameters: {
      query?: {
        policeStationId?: number;
        responsibleOfficerId?: number;
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
          '*/*': components['schemas']['ApiResponsePageResultWharfRelationView'];
        };
      };
    };
  };
  policeStationList: {
    parameters: {
      query?: {
        keyword?: string;
        region?: string;
        status?: string;
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
          '*/*': components['schemas']['ApiResponsePageResultPoliceStationView'];
        };
      };
    };
  };
  policeStationCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['WritePoliceStation'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponsePoliceStationView'];
        };
      };
    };
  };
  policeStationDetail: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: number;
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
          '*/*': components['schemas']['ApiResponsePoliceStationView'];
        };
      };
    };
  };
  policeStationUpdate: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: number;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['WritePoliceStation'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponsePoliceStationView'];
        };
      };
    };
  };
  policeStationDelete: {
    parameters: {
      query: {
        version: number;
      };
      header?: never;
      path: {
        id: number;
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
          '*/*': components['schemas']['ApiResponseVoid'];
        };
      };
    };
  };
  portOfficerList: {
    parameters: {
      query?: {
        keyword?: string;
        region?: string;
        status?: string;
        policeStationId?: number;
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
          '*/*': components['schemas']['ApiResponsePageResultPortOfficerView'];
        };
      };
    };
  };
  portOfficerCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['WritePortOfficer'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponsePortOfficerView'];
        };
      };
    };
  };
  portOfficerDetail: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: number;
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
          '*/*': components['schemas']['ApiResponsePortOfficerView'];
        };
      };
    };
  };
  portOfficerUpdate: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: number;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['WritePortOfficer'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponsePortOfficerView'];
        };
      };
    };
  };
  portOfficerDelete: {
    parameters: {
      query: {
        version: number;
      };
      header?: never;
      path: {
        id: number;
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
          '*/*': components['schemas']['ApiResponseVoid'];
        };
      };
    };
  };
  portList: {
    parameters: {
      query?: {
        keyword?: string;
        region?: string;
        status?: string;
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
          '*/*': components['schemas']['ApiResponsePageResultPortView'];
        };
      };
    };
  };
  portCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['WritePort'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponsePortView'];
        };
      };
    };
  };
  portDetail: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: number;
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
          '*/*': components['schemas']['ApiResponsePortView'];
        };
      };
    };
  };
  portUpdate: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: number;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['WritePort'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponsePortView'];
        };
      };
    };
  };
  portDelete: {
    parameters: {
      query: {
        version: number;
      };
      header?: never;
      path: {
        id: number;
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
          '*/*': components['schemas']['ApiResponseVoid'];
        };
      };
    };
  };
  wharfList: {
    parameters: {
      query?: {
        keyword?: string;
        region?: string;
        status?: string;
        portId?: number;
        policeStationId?: number;
        responsibleOfficerId?: number;
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
          '*/*': components['schemas']['ApiResponsePageResultWharfView'];
        };
      };
    };
  };
  wharfCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['WriteWharf'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseWharfView'];
        };
      };
    };
  };
  wharfDetail: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: number;
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
          '*/*': components['schemas']['ApiResponseWharfView'];
        };
      };
    };
  };
  wharfUpdate: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        id: number;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['WriteWharf'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseWharfView'];
        };
      };
    };
  };
  wharfDelete: {
    parameters: {
      query: {
        version: number;
      };
      header?: never;
      path: {
        id: number;
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
          '*/*': components['schemas']['ApiResponseVoid'];
        };
      };
    };
  };
}
