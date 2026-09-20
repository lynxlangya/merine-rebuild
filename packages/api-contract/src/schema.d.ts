export interface paths {
    "/api/system/users/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询用户详情 */
        get: operations["detail"];
        /** 编辑用户的基本信息、角色，或重置密码 */
        put: operations["update"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/units/{code}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 编辑单位名称、上级或行政区划 */
        put: operations["update_1"];
        post?: never;
        /** 删除无下级、无用户的单位 */
        delete: operations["delete"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询用户 */
        get: operations["list"];
        put?: never;
        /** 新建用户 */
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/users/enable": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 启用账号（单个或批量） */
        post: operations["enable"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/users/disable": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 停用账号（单个或批量）；停用后该账号的已有会话立即失效 */
        post: operations["disable"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/units": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询全部单位选项 */
        get: operations["list_1"];
        put?: never;
        /** 新增单位 */
        post: operations["create_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/bootstrap/probes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 写入一条本地合成联调记录 */
        post: operations["create_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/session": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 获取当前身份 */
        get: operations["current"];
        put?: never;
        /** 登录并建立会话 */
        post: operations["login"];
        /** 退出并作废当前会话 */
        delete: operations["logout"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/units/tree": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询完整单位组织树 */
        get: operations["tree"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/roles": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询全部角色 */
        get: operations["list_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/bootstrap": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询本地工程与数据库联调状态 */
        get: operations["status"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/csrf": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 获取 CSRF 令牌 */
        get: operations["csrf"];
        put?: never;
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
        UpdateUser: {
            /**
             * Format: int32
             * @description 打开编辑表单时取得的用户版本
             */
            version: number;
            displayName: string;
            /** @description 单位业务编码 */
            unitCode: string;
            /** @description 角色编码列表 */
            roleCodes: string[];
            /** @description 重置密码；省略或为 null 表示不修改；至少 6 个字符，UTF-8 编码不超过 72 字节 */
            newPassword?: string;
        };
        ApiResponseUserSummary: {
            code: string;
            message: string;
            data: components["schemas"]["UserSummary"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        FieldError: {
            field: string;
            message: string;
        };
        UserSummary: {
            /** @description 用户技术主键，以十进制字符串返回 */
            id: string;
            loginName: string;
            displayName: string;
            unitCode: string;
            unitName: string;
            /** @description 角色编码，按编码升序，与 roleNames 下标一一对应 */
            roleCodes: string[];
            /** @description 角色名称，与 roleCodes 下标一一对应 */
            roleNames: string[];
            /** @description 账号状态：ENABLED 启用，DISABLED 停用 */
            status: string;
            /**
             * Format: int32
             * @description 编辑版本；保存时原样提交，冲突须重新读取
             */
            version: number;
            /**
             * Format: date-time
             * @description 最近一次成功登录时刻（UTC）；从未登录为 null
             */
            lastLoginAt?: string;
        };
        UpdateUnit: {
            /**
             * Format: int32
             * @description 打开编辑表单时取得的单位版本
             */
            version: number;
            name: string;
            /** @description 上级单位编码；一级单位为 null */
            parentCode?: string | null;
            /** @description 2–12 位数字 */
            areaCode: string;
        };
        ApiResponseUnitView: {
            code: string;
            message: string;
            data: components["schemas"]["UnitView"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        UnitView: {
            code: string;
            name: string;
            /** @description 单位状态：ENABLED 启用，DISABLED 停用 */
            status: string;
            /** @description 上级单位编码；一级单位为 null */
            parentCode?: string | null;
            /**
             * Format: int32
             * @description 单位层级：1 总队，2 支队，3 大队
             */
            level: number;
            /** @description 行政区划代码；历史合成数据可能为 null */
            areaCode?: string | null;
            /** Format: int64 */
            childCount: number;
            /** Format: int64 */
            userCount: number;
            /**
             * Format: int32
             * @description 编辑版本；保存时原样提交，冲突须重新读取
             */
            version: number;
            /**
             * Format: date-time
             * @description 最近修改时刻（UTC）
             */
            updatedAt: string;
        };
        CreateUser: {
            loginName: string;
            displayName: string;
            /** @description 单位业务编码 */
            unitCode: string;
            /** @description 角色编码列表 */
            roleCodes: string[];
            /** @description 至少 6 个字符，UTF-8 编码不超过 72 字节 */
            password: string;
        };
        ChangeStatus: {
            /** @description 用户 id 列表 */
            userIds: string[];
        };
        ApiResponseListUserSummary: {
            code: string;
            message: string;
            data: components["schemas"]["UserSummary"][];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        CreateUnit: {
            code: string;
            name: string;
            /** @description 上级单位编码；一级单位为 null */
            parentCode?: string | null;
            /** @description 2–12 位数字 */
            areaCode: string;
        };
        CreateProbe: {
            note: string;
        };
        ApiResponseProbeRecord: {
            code: string;
            message: string;
            data: components["schemas"]["ProbeRecord"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        ProbeRecord: {
            id: string;
            note: string;
            /** Format: date-time */
            createdAt: string;
        };
        LoginRequest: {
            loginName: string;
            /** @description UTF-8 编码不超过 72 字节 */
            password: string;
            /** @description 勾选后延长本次会话的空闲超时；省略或为 null 表示不保持登录 */
            rememberMe?: boolean;
        };
        ApiResponseAuthUserResponse: {
            code: string;
            message: string;
            data: components["schemas"]["AuthUserResponse"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        AuthUserResponse: {
            id: string;
            loginName: string;
            displayName: string;
            unitName: string;
            roleNames: string[];
            /**
             * Format: int32
             * @description 授权版本；角色或数据范围变化时递增，旧会话据此失效
             */
            authorizationVersion: number;
        };
        ApiResponsePageResultUserSummary: {
            code: string;
            message: string;
            data: components["schemas"]["PageResultUserSummary"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        PageResultUserSummary: {
            items: components["schemas"]["UserSummary"][];
            /** Format: int64 */
            total: number;
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            pageSize: number;
        };
        ApiResponseListUnitSummary: {
            code: string;
            message: string;
            data: components["schemas"]["UnitSummary"][];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        UnitSummary: {
            /** @description 单位业务编码，区分大小写，全局唯一 */
            code: string;
            name: string;
            /** @description 单位状态：ENABLED 启用，DISABLED 停用 */
            status: string;
            /** @description 上级单位编码；一级单位为 null */
            parentCode?: string | null;
            /**
             * Format: int32
             * @description 单位层级：1 总队，2 支队，3 大队
             */
            level: number;
        };
        ApiResponseListUnitTreeNode: {
            code: string;
            message: string;
            data: components["schemas"]["UnitTreeNode"][];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        UnitTreeNode: {
            code: string;
            name: string;
            /** @description 单位状态：ENABLED 启用，DISABLED 停用 */
            status: string;
            /** @description 上级单位编码；一级单位为 null */
            parentCode?: string | null;
            /**
             * Format: int32
             * @description 单位层级：1 总队，2 支队，3 大队
             */
            level: number;
            /** @description 行政区划代码；历史合成数据可能为 null */
            areaCode?: string | null;
            /**
             * Format: int64
             * @description 直属下级单位数
             */
            childCount: number;
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
            /**
             * Format: date-time
             * @description 最近修改时刻（UTC）
             */
            updatedAt: string;
            /** @description 直属下级节点，按单位编码升序 */
            children: components["schemas"]["UnitTreeNode"][];
        };
        ApiResponseListRoleSummary: {
            code: string;
            message: string;
            data: components["schemas"]["RoleSummary"][];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        RoleSummary: {
            /** @description 角色编码，区分大小写，全局唯一 */
            code: string;
            name: string;
            /** @description 角色状态：ENABLED 启用，DISABLED 停用 */
            status: string;
        };
        ApiResponseBootstrapStatus: {
            code: string;
            message: string;
            data: components["schemas"]["BootstrapStatus"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        BootstrapStatus: {
            application: string;
            database: string;
            /** Format: date-time */
            serverTime: string;
            /** Format: int64 */
            totalProbes: number;
            recentProbes: components["schemas"]["ProbeRecord"][];
        };
        CsrfToken: {
            parameterName?: string;
            token?: string;
            headerName?: string;
        };
        ApiResponseVoid: {
            code: string;
            message: string;
            data: unknown;
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
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
    detail: {
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
                    "*/*": components["schemas"]["ApiResponseUserSummary"];
                };
            };
        };
    };
    update: {
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
                "application/json": components["schemas"]["UpdateUser"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseUserSummary"];
                };
            };
        };
    };
    update_1: {
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
                "application/json": components["schemas"]["UpdateUnit"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseUnitView"];
                };
            };
        };
    };
    delete: {
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
                    "*/*": components["schemas"]["ApiResponseVoid"];
                };
            };
        };
    };
    list: {
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
                    "*/*": components["schemas"]["ApiResponsePageResultUserSummary"];
                };
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateUser"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseUserSummary"];
                };
            };
        };
    };
    enable: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChangeStatus"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListUserSummary"];
                };
            };
        };
    };
    disable: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChangeStatus"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListUserSummary"];
                };
            };
        };
    };
    list_1: {
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
                    "*/*": components["schemas"]["ApiResponseListUnitSummary"];
                };
            };
        };
    };
    create_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateUnit"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseUnitView"];
                };
            };
        };
    };
    create_2: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateProbe"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseProbeRecord"];
                };
            };
        };
    };
    current: {
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
                    "*/*": components["schemas"]["ApiResponseAuthUserResponse"];
                };
            };
        };
    };
    login: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseAuthUserResponse"];
                };
            };
        };
    };
    logout: {
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
                    "*/*": components["schemas"]["ApiResponseVoid"];
                };
            };
        };
    };
    tree: {
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
                    "*/*": components["schemas"]["ApiResponseListUnitTreeNode"];
                };
            };
        };
    };
    list_2: {
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
                    "*/*": components["schemas"]["ApiResponseListRoleSummary"];
                };
            };
        };
    };
    status: {
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
                    "*/*": components["schemas"]["ApiResponseBootstrapStatus"];
                };
            };
        };
    };
    csrf: {
        parameters: {
            query: {
                csrfToken: components["schemas"]["CsrfToken"];
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
                    "*/*": components["schemas"]["ApiResponseVoid"];
                };
            };
        };
    };
}
