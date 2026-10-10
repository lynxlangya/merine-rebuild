export interface paths {
  '/api/dictionaries': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 批量读取字典；不传 codes 时返回全部字典（含停用项） */
    get: operations['dictionaryList'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/me/menus': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询当前账号可见的导航树 */
    get: operations['menuNavigationMyMenus'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/audit-logs': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询审计流水（服务端筛选 + 分页，时间缺省最近 7 天） */
    get: operations['auditLogList'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/audit-logs/modules': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 审计模块筛选项（代码侧注册表） */
    get: operations['auditLogModules'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/audit-logs/purge': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    /** 立即清理超过保留期的流水（默认 30 天，删除不可恢复） */
    post: operations['auditLogPurge'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/dictionaries': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询字典类型列表 */
    get: operations['dictionaryAdminList'];
    put?: never;
    /** 新建字典类型 */
    post: operations['dictionaryAdminCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/dictionaries/{code}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询字典详情（含全部字典项与停用项） */
    get: operations['dictionaryAdminDetail'];
    /** 编辑字典名称、说明与状态 */
    put: operations['dictionaryAdminUpdate'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/dictionaries/{code}/items': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    /** 新增字典项（取值创建后不可修改） */
    post: operations['dictionaryAdminCreateItem'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/dictionaries/{code}/items/{value}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    /** 编辑字典项标签、说明、排序与状态 */
    put: operations['dictionaryAdminUpdateItem'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/menus': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询完整菜单树（含停用节点） */
    get: operations['menuTree'];
    put?: never;
    /** 新增菜单节点（目录/页面/页签/按钮） */
    post: operations['menuCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/menus/icons': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询前端已注册的导航图标（目录与页面节点只能从中选择） */
    get: operations['menuIcons'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/menus/restore': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    /** 恢复默认菜单：只补缺失的引导节点与权限码 */
    post: operations['menuRestore'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/menus/route-keys': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询前端已注册的路由 key（页面节点只能从中选择） */
    get: operations['menuRouteKeys'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/menus/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    /** 编辑节点名称、上级、排序、状态与说明 */
    put: operations['menuUpdate'];
    post?: never;
    /** 删除节点及其子树，并解除相关角色的授权 */
    delete: operations['menuDelete'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/menus/{id}/delete-impact': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 预览删除影响：子树节点数与将失去授权的角色数 */
    get: operations['menuDeleteImpact'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/roles': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 分页查询角色 */
    get: operations['roleList'];
    put?: never;
    /** 新建角色 */
    post: operations['roleCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/roles/disable': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    /** 停用角色（单个或批量）；持有者会话立即失效 */
    post: operations['roleDisable'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/roles/enable': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    /** 启用角色（单个或批量） */
    post: operations['roleEnable'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/roles/options': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询全部角色选项 */
    get: operations['roleOptions'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/roles/permission-tree': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询权限勾选树（菜单资源树 + 未挂在菜单上的权限码） */
    get: operations['rolePermissionTree'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/roles/{code}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询角色详情（含权限集合与成员数） */
    get: operations['roleDetail'];
    /** 编辑角色的名称、说明与功能权限 */
    put: operations['roleUpdate'];
    post?: never;
    /** 删除无成员、非内置的角色；需提交打开页面时的版本号 */
    delete: operations['roleDelete'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/roles/{code}/members': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 分页查询角色成员（只读） */
    get: operations['roleMembers'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/units': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询全部单位选项 */
    get: operations['unitList'];
    put?: never;
    /** 新增单位 */
    post: operations['unitCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/units/tree': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询完整单位组织树 */
    get: operations['unitTree'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/units/{code}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    /** 编辑单位名称、上级或行政区划 */
    put: operations['unitUpdate'];
    post?: never;
    /** 删除无下级、无用户的单位 */
    delete: operations['unitDelete'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/units/{code}/police-stations': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询单位直属派出所摘要 */
    get: operations['unitPoliceStations'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/users': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 分页查询用户 */
    get: operations['userAdminList'];
    put?: never;
    /** 新建用户 */
    post: operations['userAdminCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/users/disable': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    /** 停用账号（单个或批量）；停用后该账号的已有会话立即失效 */
    post: operations['userAdminDisable'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/users/enable': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    /** 启用账号（单个或批量） */
    post: operations['userAdminEnable'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/users/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询用户详情 */
    get: operations['userAdminDetail'];
    /** 编辑用户的姓名、所属单位与角色 */
    put: operations['userAdminUpdate'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/system/users/{id}/reset-password': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    /** 重置账号密码；该账号的已有会话立即失效 */
    post: operations['userAdminResetPassword'];
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
    ApiResponseAuditPurgeResult: {
      code: string;
      data: components['schemas']['AuditPurgeResult'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseDictionaryItemView: {
      code: string;
      data: components['schemas']['DictionaryItemView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseDictionaryView: {
      code: string;
      data: components['schemas']['DictionaryView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListAuditModuleOption: {
      code: string;
      data: components['schemas']['AuditModuleOption'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListDictionaryListItem: {
      code: string;
      data: components['schemas']['DictionaryListItem'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListDictionaryView: {
      code: string;
      data: components['schemas']['DictionaryView'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListIconOption: {
      code: string;
      data: components['schemas']['IconOption'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListMenuNode: {
      code: string;
      data: components['schemas']['MenuNode'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListNavigationNode: {
      code: string;
      data: components['schemas']['NavigationNode'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListRoleListItem: {
      code: string;
      data: components['schemas']['RoleListItem'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListRoleSummary: {
      code: string;
      data: components['schemas']['RoleSummary'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListRouteKeyOption: {
      code: string;
      data: components['schemas']['RouteKeyOption'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListUnitSummary: {
      code: string;
      data: components['schemas']['UnitSummary'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListUnitTreeNode: {
      code: string;
      data: components['schemas']['UnitTreeNode'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListUserSummary: {
      code: string;
      data: components['schemas']['UserSummary'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseMenuDeleteImpact: {
      code: string;
      data: components['schemas']['MenuDeleteImpact'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseMenuNode: {
      code: string;
      data: components['schemas']['MenuNode'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultAuditLogEntry: {
      code: string;
      data: components['schemas']['PageResultAuditLogEntry'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultRoleListItem: {
      code: string;
      data: components['schemas']['PageResultRoleListItem'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultRoleMember: {
      code: string;
      data: components['schemas']['PageResultRoleMember'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultUnitPoliceStation: {
      code: string;
      data: components['schemas']['PageResultUnitPoliceStation'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultUserSummary: {
      code: string;
      data: components['schemas']['PageResultUserSummary'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseRestoreMenusResult: {
      code: string;
      data: components['schemas']['RestoreMenusResult'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseRoleDetail: {
      code: string;
      data: components['schemas']['RoleDetail'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseUnitView: {
      code: string;
      data: components['schemas']['UnitView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseUserSummary: {
      code: string;
      data: components['schemas']['UserSummary'];
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
    AuditLogEntry: {
      action: string;
      actorLogin: string;
      actorName: string;
      actorUnitName: string;
      clientIp: string;
      id: string;
      module: string;
      moduleName: string;
      /** Format: date-time */
      occurredAt: string;
      requestId: string;
      result: string;
      summary: string;
      targetId: string;
      targetLabel: string;
      targetType: string;
    };
    AuditModuleOption: {
      code: string;
      name: string;
    };
    AuditPurgeResult: {
      /**
       * Format: int64
       * @description 本次删除的条数；0 表示没有过期记录
       */
      deleted: number;
      /**
       * Format: date-time
       * @description 被删记录中最早的发生时刻，UTC；没有删除时为 null
       */
      earliest?: string | null;
      /**
       * Format: date-time
       * @description 被删记录中最晚的发生时刻，UTC；没有删除时为 null
       */
      latest?: string | null;
      /**
       * Format: int32
       * @description 保留期（天），只删除早于这个窗口的记录
       */
      retentionDays: number;
    };
    ChangeRoleStatus: {
      /** @description 角色编码列表 */
      roleCodes: string[];
    };
    ChangeStatus: {
      /** @description 用户 id 列表 */
      userIds: string[];
    };
    CreateDictionary: {
      code: string;
      description?: string | null;
      name: string;
    };
    CreateDictionaryItem: {
      description?: string | null;
      label: string;
      /**
       * Format: int32
       * @description 同类型内排序，越小越靠前；缺省为 0
       */
      sortOrder?: number;
      /** @description 业务数据里保存的值，创建后不可修改 */
      value: string;
    };
    CreateMenu: {
      description?: string | null;
      /** @description 前端已注册的图标名称；仅目录与页面可设置，留空表示默认图标 */
      iconName?: string | null;
      name: string;
      /** @description 上级节点 id；顶层节点为 null */
      parentId?: string | null;
      /** @description 权限码；页面、页签、按钮必填，目录留空 */
      permissionCode?: string | null;
      /** @description 前端已注册的路由 key；仅页面节点需要 */
      routeKey?: string | null;
      /**
       * Format: int32
       * @description 同级排序，越小越靠前；缺省为 0
       */
      sortOrder?: number;
      type: string;
    };
    CreateRole: {
      code: string;
      /** @description 角色说明，用于解释这个角色能用什么功能 */
      description?: string | null;
      name: string;
      /** @description 功能权限码列表，可为空数组 */
      permissionCodes: string[];
    };
    CreateUnit: {
      /** @description 2–12 位数字 */
      areaCode: string;
      code: string;
      name: string;
      /** @description 上级单位编码；一级单位为 null */
      parentCode?: string | null;
    };
    CreateUser: {
      displayName: string;
      loginName: string;
      /** @description 至少 6 个字符，UTF-8 编码不超过 72 字节 */
      password: string;
      /** @description 角色编码列表 */
      roleCodes: string[];
      /** @description 单位业务编码 */
      unitCode: string;
    };
    DictionaryItemView: {
      /** @description 字典项说明；未填写为 null */
      description?: string | null;
      label: string;
      /** Format: int32 */
      sortOrder: number;
      /** @description 字典项状态：ENABLED 启用，DISABLED 停用 */
      status: string;
      /** @description 字典项取值，在所属字典内唯一，创建后不可修改 */
      value: string;
      /**
       * Format: int32
       * @description 编辑版本；保存时原样提交，冲突须重新读取
       */
      version: number;
    };
    DictionaryListItem: {
      code: string;
      /** @description 字典说明；未填写为 null */
      description?: string | null;
      /**
       * Format: int32
       * @description 字典项数量（含停用项）
       */
      itemCount: number;
      name: string;
      /** @description 字典状态：ENABLED 启用，DISABLED 停用 */
      status: string;
      /**
       * Format: date-time
       * @description 最近修改时刻（UTC）
       */
      updatedAt?: string | null;
      /** Format: int32 */
      version: number;
    };
    DictionaryView: {
      /** @description 字典编码，形如 common.status 或 vessel.type */
      code: string;
      /** @description 字典说明；未填写为 null */
      description?: string | null;
      /** @description 字典项，按排序升序，含停用项 */
      items: components['schemas']['DictionaryItemView'][];
      name: string;
      /** @description 字典状态：ENABLED 启用，DISABLED 停用；停用的字典不返回字典项 */
      status: string;
      /**
       * Format: date-time
       * @description 最近修改时刻（UTC）
       */
      updatedAt?: string | null;
      /**
       * Format: int32
       * @description 编辑版本
       */
      version: number;
    };
    FieldError: {
      field: string;
      message: string;
    };
    IconOption: {
      /** @description 图标含义，便于选择 */
      label: string;
      /** @description 图标名称，与前端 iconRegistry 注册的组件一致 */
      name: string;
    };
    MenuDeleteImpact: {
      /**
       * Format: int32
       * @description 将失去授权/已失去授权的角色数
       */
      affectedRoles: number;
      /**
       * Format: int32
       * @description 将删除/已删除的节点数（含子树）
       */
      deletedNodes: number;
    };
    MenuNode: {
      /** @description 下级节点，按排序升序 */
      children: components['schemas']['MenuNode'][];
      /** @description 节点说明；未填写为 null */
      description?: string | null;
      /** @description 前端已注册的图标名称；仅目录与页面可设置，为空表示默认图标 */
      iconName?: string | null;
      id: string;
      name: string;
      /** @description 上级节点 id；顶层为 null */
      parentId?: string | null;
      /** @description 权限码；目录为 null */
      permissionCode?: string | null;
      /** @description 前端已注册的路由 key；仅页面节点有值 */
      routeKey?: string | null;
      /** Format: int32 */
      sortOrder: number;
      /** @description 节点状态：ENABLED 启用，DISABLED 停用；停用只影响导航与可分配性 */
      status: string;
      /** @description 节点类型：DIRECTORY 目录，PAGE 页面，TAB 页签，BUTTON 按钮 */
      type: string;
      /**
       * Format: date-time
       * @description 最近修改时刻（UTC）
       */
      updatedAt: string;
      /**
       * Format: int32
       * @description 编辑版本；保存时原样提交，冲突须重新读取
       */
      version: number;
    };
    NavigationNode: {
      children: components['schemas']['NavigationNode'][];
      /** @description 节点说明；未填写为 null */
      description?: string | null;
      /** @description 前端已注册的图标名称；为空时前端回落到默认图标 */
      iconName?: string | null;
      id: string;
      name: string;
      /** @description 前端已注册的路由 key；仅页面节点有值 */
      routeKey?: string | null;
      /** @description 节点类型：DIRECTORY 目录，PAGE 页面，TAB 页签，BUTTON 按钮 */
      type: string;
    };
    PageResultAuditLogEntry: {
      items: components['schemas']['AuditLogEntry'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultRoleListItem: {
      items: components['schemas']['RoleListItem'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultRoleMember: {
      items: components['schemas']['RoleMember'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultUnitPoliceStation: {
      items: components['schemas']['UnitPoliceStation'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultUserSummary: {
      items: components['schemas']['UserSummary'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    ResetPassword: {
      /** @description 至少 6 个字符，UTF-8 编码不超过 72 字节 */
      newPassword: string;
      /**
       * Format: int32
       * @description 打开操作前的用户版本
       */
      version: number;
    };
    RestoreMenusResult: {
      /**
       * Format: int32
       * @description 新建的菜单节点数
       */
      createdMenus: number;
      /**
       * Format: int32
       * @description 新建的权限码数
       */
      createdPermissions: number;
    };
    RoleDetail: {
      /** @description 是否内置管理员角色：不可删除，且恒拥有全部权限 */
      builtin: boolean;
      code: string;
      /** @description 角色说明；未填写为 null */
      description?: string | null;
      name: string;
      /** @description 已分配的功能权限码；内置角色返回全部权限码 */
      permissionCodes: string[];
      /** @description 角色状态：ENABLED 启用，DISABLED 停用 */
      status: string;
      /**
       * Format: date-time
       * @description 最近修改时刻（UTC）
       */
      updatedAt: string;
      /**
       * Format: int64
       * @description 持有该角色的账号数，含已停用账号
       */
      userCount: number;
      /**
       * Format: int32
       * @description 编辑版本；保存时原样提交，冲突须重新读取
       */
      version: number;
    };
    RoleListItem: {
      /** @description 是否内置管理员角色：不可删除，恒拥有全部权限 */
      builtin: boolean;
      code: string;
      /** @description 角色说明；未填写为 null */
      description?: string | null;
      name: string;
      /**
       * Format: int32
       * @description 已分配的功能权限数；内置角色为全部权限码数量
       */
      permissionCount: number;
      /** @description 角色状态：ENABLED 启用，DISABLED 停用 */
      status: string;
      /**
       * Format: date-time
       * @description 最近修改时刻（UTC）
       */
      updatedAt: string;
      /**
       * Format: int64
       * @description 持有该角色的账号数，含已停用账号
       */
      userCount: number;
      /**
       * Format: int32
       * @description 编辑版本；保存时原样提交，冲突须重新读取
       */
      version: number;
    };
    RoleMember: {
      displayName: string;
      /** @description 用户技术主键，十进制字符串 */
      id: string;
      loginName: string;
      /** @description 账号状态：ENABLED 启用，DISABLED 停用 */
      status: string;
      unitName: string;
    };
    RoleSummary: {
      /** @description 角色编码，区分大小写，全局唯一 */
      code: string;
      name: string;
      /** @description 角色状态：ENABLED 启用，DISABLED 停用 */
      status: string;
    };
    RouteKeyOption: {
      /** @description 前端注册表里的路由 key */
      key: string;
      /** @description 页面名称，便于选择 */
      label: string;
    };
    UnitPoliceStation: {
      id: string;
      name: string;
      status: string;
    };
    UnitSummary: {
      /** @description 单位业务编码，区分大小写，全局唯一 */
      code: string;
      /**
       * Format: int32
       * @description 单位层级：1 总队，2 支队，3 大队
       */
      level: number;
      name: string;
      /** @description 上级单位编码；一级单位为 null */
      parentCode?: string | null;
      /** @description 单位状态：ENABLED 启用，DISABLED 停用 */
      status: string;
    };
    UnitTreeNode: {
      /** @description 行政区划代码；历史合成数据可能为 null */
      areaCode?: string | null;
      /**
       * Format: int64
       * @description 直属下级单位数
       */
      childCount: number;
      /** @description 直属下级节点，按单位编码升序 */
      children: components['schemas']['UnitTreeNode'][];
      code: string;
      /**
       * Format: int32
       * @description 单位层级：1 总队，2 支队，3 大队
       */
      level: number;
      name: string;
      /** @description 上级单位编码；一级单位为 null */
      parentCode?: string | null;
      /** @description 单位状态：ENABLED 启用，DISABLED 停用 */
      status: string;
      /**
       * Format: date-time
       * @description 最近修改时刻（UTC）
       */
      updatedAt: string;
      /**
       * Format: int64
       * @description 直属用户数
       */
      userCount: number;
      /**
       * Format: int32
       * @description 编辑版本；保存时原样提交，冲突须重新读取
       */
      version: number;
    };
    UnitView: {
      /** @description 行政区划代码；历史合成数据可能为 null */
      areaCode?: string | null;
      /** Format: int64 */
      childCount: number;
      code: string;
      /**
       * Format: int32
       * @description 单位层级：1 总队，2 支队，3 大队
       */
      level: number;
      name: string;
      /** @description 上级单位编码；一级单位为 null */
      parentCode?: string | null;
      /** @description 单位状态：ENABLED 启用，DISABLED 停用 */
      status: string;
      /**
       * Format: date-time
       * @description 最近修改时刻（UTC）
       */
      updatedAt: string;
      /** Format: int64 */
      userCount: number;
      /**
       * Format: int32
       * @description 编辑版本；保存时原样提交，冲突须重新读取
       */
      version: number;
    };
    UpdateDictionary: {
      description?: string | null;
      name: string;
      status: string;
      /** Format: int32 */
      version: number;
    };
    UpdateDictionaryItem: {
      description?: string | null;
      label: string;
      /** Format: int32 */
      sortOrder: number;
      status: string;
      /** Format: int32 */
      version: number;
    };
    UpdateMenu: {
      description?: string | null;
      /** @description 前端已注册的图标名称；仅目录与页面可设置，留空表示默认图标 */
      iconName?: string | null;
      name: string;
      /** @description 上级节点 id；顶层节点为 null */
      parentId?: string | null;
      /** @description 前端已注册的路由 key；仅页面节点需要 */
      routeKey?: string | null;
      /** Format: int32 */
      sortOrder: number;
      status: string;
      /** Format: int32 */
      version: number;
    };
    UpdateRole: {
      /** @description 角色说明，用于解释这个角色能用什么功能 */
      description?: string | null;
      name: string;
      /** @description 功能权限码列表，可为空数组 */
      permissionCodes: string[];
      /**
       * Format: int32
       * @description 打开编辑表单时取得的角色版本
       */
      version: number;
    };
    UpdateUnit: {
      /** @description 2–12 位数字 */
      areaCode: string;
      name: string;
      /** @description 上级单位编码；一级单位为 null */
      parentCode?: string | null;
      /**
       * Format: int32
       * @description 打开编辑表单时取得的单位版本
       */
      version: number;
    };
    UpdateUser: {
      displayName: string;
      /** @description 角色编码列表 */
      roleCodes: string[];
      /** @description 单位业务编码 */
      unitCode: string;
      /**
       * Format: int32
       * @description 打开编辑表单时取得的用户版本
       */
      version: number;
    };
    UserSummary: {
      displayName: string;
      /** @description 用户技术主键，以十进制字符串返回 */
      id: string;
      /**
       * Format: date-time
       * @description 最近一次成功登录时刻（UTC）；从未登录为 null
       */
      lastLoginAt?: string;
      loginName: string;
      /** @description 角色编码，按编码升序，与 roleNames 下标一一对应 */
      roleCodes: string[];
      /** @description 角色名称，与 roleCodes 下标一一对应 */
      roleNames: string[];
      /** @description 账号状态：ENABLED 启用，DISABLED 停用 */
      status: string;
      unitCode: string;
      unitName: string;
      /**
       * Format: int32
       * @description 编辑版本；保存时原样提交，冲突须重新读取
       */
      version: number;
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
  dictionaryList: {
    parameters: {
      query?: {
        codes?: string;
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
          '*/*': components['schemas']['ApiResponseListDictionaryView'];
        };
      };
    };
  };
  menuNavigationMyMenus: {
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
          '*/*': components['schemas']['ApiResponseListNavigationNode'];
        };
      };
    };
  };
  auditLogList: {
    parameters: {
      query?: {
        from?: string;
        to?: string;
        actor?: string;
        module?: string;
        action?: string;
        result?: string;
        targetType?: string;
        targetId?: string;
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
          '*/*': components['schemas']['ApiResponsePageResultAuditLogEntry'];
        };
      };
    };
  };
  auditLogModules: {
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
          '*/*': components['schemas']['ApiResponseListAuditModuleOption'];
        };
      };
    };
  };
  auditLogPurge: {
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
          '*/*': components['schemas']['ApiResponseAuditPurgeResult'];
        };
      };
    };
  };
  dictionaryAdminList: {
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
          '*/*': components['schemas']['ApiResponseListDictionaryListItem'];
        };
      };
    };
  };
  dictionaryAdminCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateDictionary'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseDictionaryView'];
        };
      };
    };
  };
  dictionaryAdminDetail: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        code: string;
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
          '*/*': components['schemas']['ApiResponseDictionaryView'];
        };
      };
    };
  };
  dictionaryAdminUpdate: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        code: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['UpdateDictionary'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseDictionaryView'];
        };
      };
    };
  };
  dictionaryAdminCreateItem: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        code: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateDictionaryItem'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseDictionaryItemView'];
        };
      };
    };
  };
  dictionaryAdminUpdateItem: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        code: string;
        value: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['UpdateDictionaryItem'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseDictionaryItemView'];
        };
      };
    };
  };
  menuTree: {
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
          '*/*': components['schemas']['ApiResponseListMenuNode'];
        };
      };
    };
  };
  menuCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateMenu'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseMenuNode'];
        };
      };
    };
  };
  menuIcons: {
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
          '*/*': components['schemas']['ApiResponseListIconOption'];
        };
      };
    };
  };
  menuRestore: {
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
          '*/*': components['schemas']['ApiResponseRestoreMenusResult'];
        };
      };
    };
  };
  menuRouteKeys: {
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
          '*/*': components['schemas']['ApiResponseListRouteKeyOption'];
        };
      };
    };
  };
  menuUpdate: {
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
        'application/json': components['schemas']['UpdateMenu'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseMenuNode'];
        };
      };
    };
  };
  menuDelete: {
    parameters: {
      query?: {
        version?: number;
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
          '*/*': components['schemas']['ApiResponseMenuDeleteImpact'];
        };
      };
    };
  };
  menuDeleteImpact: {
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
          '*/*': components['schemas']['ApiResponseMenuDeleteImpact'];
        };
      };
    };
  };
  roleList: {
    parameters: {
      query?: {
        keyword?: string;
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
          '*/*': components['schemas']['ApiResponsePageResultRoleListItem'];
        };
      };
    };
  };
  roleCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateRole'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseRoleDetail'];
        };
      };
    };
  };
  roleDisable: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ChangeRoleStatus'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseListRoleListItem'];
        };
      };
    };
  };
  roleEnable: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ChangeRoleStatus'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseListRoleListItem'];
        };
      };
    };
  };
  roleOptions: {
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
          '*/*': components['schemas']['ApiResponseListRoleSummary'];
        };
      };
    };
  };
  rolePermissionTree: {
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
          '*/*': components['schemas']['ApiResponseListMenuNode'];
        };
      };
    };
  };
  roleDetail: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        code: string;
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
          '*/*': components['schemas']['ApiResponseRoleDetail'];
        };
      };
    };
  };
  roleUpdate: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        code: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['UpdateRole'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseRoleDetail'];
        };
      };
    };
  };
  roleDelete: {
    parameters: {
      query?: {
        version?: number;
      };
      header?: never;
      path: {
        code: string;
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
  roleMembers: {
    parameters: {
      query?: {
        page?: number;
        pageSize?: number;
      };
      header?: never;
      path: {
        code: string;
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
          '*/*': components['schemas']['ApiResponsePageResultRoleMember'];
        };
      };
    };
  };
  unitList: {
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
          '*/*': components['schemas']['ApiResponseListUnitSummary'];
        };
      };
    };
  };
  unitCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateUnit'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseUnitView'];
        };
      };
    };
  };
  unitTree: {
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
          '*/*': components['schemas']['ApiResponseListUnitTreeNode'];
        };
      };
    };
  };
  unitUpdate: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        code: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['UpdateUnit'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseUnitView'];
        };
      };
    };
  };
  unitDelete: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        code: string;
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
  unitPoliceStations: {
    parameters: {
      query?: {
        page?: number;
        pageSize?: number;
      };
      header?: never;
      path: {
        code: string;
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
          '*/*': components['schemas']['ApiResponsePageResultUnitPoliceStation'];
        };
      };
    };
  };
  userAdminList: {
    parameters: {
      query?: {
        keyword?: string;
        unitCode?: string;
        roleCode?: string;
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
          '*/*': components['schemas']['ApiResponsePageResultUserSummary'];
        };
      };
    };
  };
  userAdminCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['CreateUser'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseUserSummary'];
        };
      };
    };
  };
  userAdminDisable: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ChangeStatus'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseListUserSummary'];
        };
      };
    };
  };
  userAdminEnable: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ChangeStatus'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseListUserSummary'];
        };
      };
    };
  };
  userAdminDetail: {
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
          '*/*': components['schemas']['ApiResponseUserSummary'];
        };
      };
    };
  };
  userAdminUpdate: {
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
        'application/json': components['schemas']['UpdateUser'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseUserSummary'];
        };
      };
    };
  };
  userAdminResetPassword: {
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
        'application/json': components['schemas']['ResetPassword'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseUserSummary'];
        };
      };
    };
  };
}
