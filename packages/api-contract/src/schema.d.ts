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
        /** 编辑用户的姓名、所属单位与角色 */
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
    "/api/system/roles/{code}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询角色详情（含权限集合与成员数） */
        get: operations["detail_1"];
        /** 编辑角色的名称、说明与功能权限 */
        put: operations["update_2"];
        post?: never;
        /** 删除无成员、非内置的角色；需提交打开页面时的版本号 */
        delete: operations["delete_1"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/menus/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 编辑节点名称、上级、排序、状态与说明 */
        put: operations["update_3"];
        post?: never;
        /** 删除节点及其子树，并解除相关角色的授权 */
        delete: operations["delete_2"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/dictionaries/{code}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询字典详情（含全部字典项与停用项） */
        get: operations["detail_2"];
        /** 编辑字典名称、说明与状态 */
        put: operations["update_4"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/dictionaries/{code}/items/{value}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** 编辑字典项标签、说明、排序与状态 */
        put: operations["updateItem"];
        post?: never;
        delete?: never;
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
    "/api/system/users/{id}/reset-password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 重置账号密码；该账号的已有会话立即失效 */
        post: operations["resetPassword"];
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
    "/api/system/roles": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询角色 */
        get: operations["list_2"];
        put?: never;
        /** 新建角色 */
        post: operations["create_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/roles/enable": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 启用角色（单个或批量） */
        post: operations["enable_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/roles/disable": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 停用角色（单个或批量）；持有者会话立即失效 */
        post: operations["disable_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/menus": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询完整菜单树（含停用节点） */
        get: operations["tree"];
        put?: never;
        /** 新增菜单节点（目录/页面/页签/按钮） */
        post: operations["create_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/menus/restore": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 恢复默认菜单：只补缺失的引导节点与权限码 */
        post: operations["restore"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/dictionaries": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询字典类型列表 */
        get: operations["list_3"];
        put?: never;
        /** 新建字典类型 */
        post: operations["create_4"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/dictionaries/{code}/items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** 新增字典项（取值创建后不可修改） */
        post: operations["createItem"];
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
        post: operations["create_5"];
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
        get: operations["tree_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/roles/{code}/members": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 分页查询角色成员（只读） */
        get: operations["members"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/roles/permission-tree": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询权限勾选树（菜单资源树） */
        get: operations["permissionTree"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/roles/options": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询全部角色选项 */
        get: operations["options"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/menus/{id}/delete-impact": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 预览删除影响：子树节点数与将失去授权的角色数 */
        get: operations["deleteImpact"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/menus/route-keys": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询前端已注册的路由 key（页面节点只能从中选择） */
        get: operations["routeKeys"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/system/menus/icons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询前端已注册的导航图标（目录与页面节点只能从中选择） */
        get: operations["icons"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/me/menus": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 查询当前账号可见的导航树 */
        get: operations["myMenus"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/dictionaries": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** 批量读取字典；不传 codes 时返回全部字典（含停用项） */
        get: operations["list_4"];
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
        UpdateRole: {
            /**
             * Format: int32
             * @description 打开编辑表单时取得的角色版本
             */
            version: number;
            name: string;
            /** @description 角色说明，用于解释这个角色能用什么功能 */
            description?: string | null;
            /** @description 功能权限码列表，可为空数组 */
            permissionCodes: string[];
        };
        ApiResponseRoleDetail: {
            code: string;
            message: string;
            data: components["schemas"]["RoleDetail"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        RoleDetail: {
            code: string;
            name: string;
            /** @description 角色说明；未填写为 null */
            description?: string | null;
            /** @description 角色状态：ENABLED 启用，DISABLED 停用 */
            status: string;
            /** @description 是否内置管理员角色：不可删除，且恒拥有全部权限 */
            builtin: boolean;
            /** @description 已分配的功能权限码；内置角色返回全部权限码 */
            permissionCodes: string[];
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
            /**
             * Format: date-time
             * @description 最近修改时刻（UTC）
             */
            updatedAt: string;
        };
        UpdateMenu: {
            /** Format: int32 */
            version: number;
            /** @description 上级节点 id；顶层节点为 null */
            parentId?: string | null;
            name: string;
            /** @description 前端已注册的路由 key；仅页面节点需要 */
            routeKey?: string | null;
            /** @description 前端已注册的图标名称；仅目录与页面可设置，留空表示默认图标 */
            iconName?: string | null;
            description?: string | null;
            /** Format: int32 */
            sortOrder: number;
            status: string;
        };
        ApiResponseMenuNode: {
            code: string;
            message: string;
            data: components["schemas"]["MenuNode"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        MenuNode: {
            id: string;
            /** @description 上级节点 id；顶层为 null */
            parentId?: string | null;
            /** @description 节点类型：DIRECTORY 目录，PAGE 页面，TAB 页签，BUTTON 按钮 */
            type: string;
            name: string;
            /** @description 前端已注册的路由 key；仅页面节点有值 */
            routeKey?: string | null;
            /** @description 前端已注册的图标名称；仅目录与页面可设置，为空表示默认图标 */
            iconName?: string | null;
            /** @description 权限码；目录为 null */
            permissionCode?: string | null;
            /** @description 节点说明；未填写为 null */
            description?: string | null;
            /** Format: int32 */
            sortOrder: number;
            /** @description 节点状态：ENABLED 启用，DISABLED 停用；停用只影响导航与可分配性 */
            status: string;
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
            /** @description 下级节点，按排序升序 */
            children: components["schemas"]["MenuNode"][];
        };
        UpdateDictionary: {
            /** Format: int32 */
            version: number;
            name: string;
            description?: string | null;
            status: string;
        };
        ApiResponseDictionaryView: {
            code: string;
            message: string;
            data: components["schemas"]["DictionaryView"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        DictionaryItemView: {
            /** @description 字典项取值，在所属字典内唯一，创建后不可修改 */
            value: string;
            label: string;
            /** @description 字典项说明；未填写为 null */
            description?: string | null;
            /** Format: int32 */
            sortOrder: number;
            /** @description 字典项状态：ENABLED 启用，DISABLED 停用 */
            status: string;
            /**
             * Format: int32
             * @description 编辑版本；保存时原样提交，冲突须重新读取
             */
            version: number;
        };
        DictionaryView: {
            /** @description 字典编码，形如 common.status 或 vessel.type */
            code: string;
            name: string;
            /** @description 字典说明；未填写为 null */
            description?: string | null;
            /** @description 字典状态：ENABLED 启用，DISABLED 停用；停用的字典不返回字典项 */
            status: string;
            /**
             * Format: int32
             * @description 编辑版本
             */
            version: number;
            /**
             * Format: date-time
             * @description 最近修改时刻（UTC）
             */
            updatedAt?: string | null;
            /** @description 字典项，按排序升序，含停用项 */
            items: components["schemas"]["DictionaryItemView"][];
        };
        UpdateDictionaryItem: {
            /** Format: int32 */
            version: number;
            label: string;
            description?: string | null;
            /** Format: int32 */
            sortOrder: number;
            status: string;
        };
        ApiResponseDictionaryItemView: {
            code: string;
            message: string;
            data: components["schemas"]["DictionaryItemView"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
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
        ResetPassword: {
            /**
             * Format: int32
             * @description 打开操作前的用户版本
             */
            version: number;
            /** @description 至少 6 个字符，UTF-8 编码不超过 72 字节 */
            newPassword: string;
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
        CreateRole: {
            code: string;
            name: string;
            /** @description 角色说明，用于解释这个角色能用什么功能 */
            description?: string | null;
            /** @description 功能权限码列表，可为空数组 */
            permissionCodes: string[];
        };
        ChangeRoleStatus: {
            /** @description 角色编码列表 */
            roleCodes: string[];
        };
        ApiResponseListRoleListItem: {
            code: string;
            message: string;
            data: components["schemas"]["RoleListItem"][];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        RoleListItem: {
            code: string;
            name: string;
            /** @description 角色说明；未填写为 null */
            description?: string | null;
            /** @description 角色状态：ENABLED 启用，DISABLED 停用 */
            status: string;
            /** @description 是否内置管理员角色：不可删除，恒拥有全部权限 */
            builtin: boolean;
            /**
             * Format: int32
             * @description 已分配的功能权限数；内置角色为全部权限码数量
             */
            permissionCount: number;
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
            /**
             * Format: date-time
             * @description 最近修改时刻（UTC）
             */
            updatedAt: string;
        };
        CreateMenu: {
            /** @description 上级节点 id；顶层节点为 null */
            parentId?: string | null;
            type: string;
            name: string;
            /** @description 前端已注册的路由 key；仅页面节点需要 */
            routeKey?: string | null;
            /** @description 前端已注册的图标名称；仅目录与页面可设置，留空表示默认图标 */
            iconName?: string | null;
            /** @description 权限码；页面、页签、按钮必填，目录留空 */
            permissionCode?: string | null;
            description?: string | null;
            /**
             * Format: int32
             * @description 同级排序，越小越靠前；缺省为 0
             */
            sortOrder?: number;
        };
        ApiResponseRestoreMenusResult: {
            code: string;
            message: string;
            data: components["schemas"]["RestoreMenusResult"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
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
        CreateDictionary: {
            code: string;
            name: string;
            description?: string | null;
        };
        CreateDictionaryItem: {
            /** @description 业务数据里保存的值，创建后不可修改 */
            value: string;
            label: string;
            description?: string | null;
            /**
             * Format: int32
             * @description 同类型内排序，越小越靠前；缺省为 0
             */
            sortOrder?: number;
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
            /** @description 角色编码，按编码升序，与 roleNames 下标一一对应 */
            roleCodes: string[];
            roleNames: string[];
            /** @description 有效功能权限码；内置管理员角色已在此展开为全部权限码 */
            permissionCodes: string[];
            /**
             * Format: int32
             * @description 授权版本；角色、角色权限或单位变化时递增，旧会话据此失效
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
        ApiResponsePageResultRoleListItem: {
            code: string;
            message: string;
            data: components["schemas"]["PageResultRoleListItem"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        PageResultRoleListItem: {
            items: components["schemas"]["RoleListItem"][];
            /** Format: int64 */
            total: number;
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            pageSize: number;
        };
        ApiResponsePageResultRoleMember: {
            code: string;
            message: string;
            data: components["schemas"]["PageResultRoleMember"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        PageResultRoleMember: {
            items: components["schemas"]["RoleMember"][];
            /** Format: int64 */
            total: number;
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            pageSize: number;
        };
        RoleMember: {
            /** @description 用户技术主键，十进制字符串 */
            id: string;
            loginName: string;
            displayName: string;
            unitName: string;
            /** @description 账号状态：ENABLED 启用，DISABLED 停用 */
            status: string;
        };
        ApiResponseListMenuNode: {
            code: string;
            message: string;
            data: components["schemas"]["MenuNode"][];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
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
        ApiResponseMenuDeleteImpact: {
            code: string;
            message: string;
            data: components["schemas"]["MenuDeleteImpact"];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        MenuDeleteImpact: {
            /**
             * Format: int32
             * @description 将删除/已删除的节点数（含子树）
             */
            deletedNodes: number;
            /**
             * Format: int32
             * @description 将失去授权/已失去授权的角色数
             */
            affectedRoles: number;
        };
        ApiResponseListRouteKeyOption: {
            code: string;
            message: string;
            data: components["schemas"]["RouteKeyOption"][];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        RouteKeyOption: {
            /** @description 前端注册表里的路由 key */
            key: string;
            /** @description 页面名称，便于选择 */
            label: string;
        };
        ApiResponseListIconOption: {
            code: string;
            message: string;
            data: components["schemas"]["IconOption"][];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        IconOption: {
            /** @description 图标名称，与前端 iconRegistry 注册的组件一致 */
            name: string;
            /** @description 图标含义，便于选择 */
            label: string;
        };
        ApiResponseListDictionaryListItem: {
            code: string;
            message: string;
            data: components["schemas"]["DictionaryListItem"][];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        DictionaryListItem: {
            code: string;
            name: string;
            /** @description 字典说明；未填写为 null */
            description?: string | null;
            /** @description 字典状态：ENABLED 启用，DISABLED 停用 */
            status: string;
            /** Format: int32 */
            version: number;
            /**
             * Format: int32
             * @description 字典项数量（含停用项）
             */
            itemCount: number;
            /**
             * Format: date-time
             * @description 最近修改时刻（UTC）
             */
            updatedAt?: string | null;
        };
        ApiResponseListNavigationNode: {
            code: string;
            message: string;
            data: components["schemas"]["NavigationNode"][];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
        };
        NavigationNode: {
            id: string;
            name: string;
            /** @description 节点类型：DIRECTORY 目录，PAGE 页面，TAB 页签，BUTTON 按钮 */
            type: string;
            /** @description 前端已注册的路由 key；仅页面节点有值 */
            routeKey?: string | null;
            /** @description 前端已注册的图标名称；为空时前端回落到默认图标 */
            iconName?: string | null;
            /** @description 节点说明；未填写为 null */
            description?: string | null;
            children: components["schemas"]["NavigationNode"][];
        };
        ApiResponseListDictionaryView: {
            code: string;
            message: string;
            data: components["schemas"]["DictionaryView"][];
            requestId: string;
            /** @description 字段级校验错误；无字段错误时为 null */
            fieldErrors?: components["schemas"]["FieldError"][];
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
    detail_1: {
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
                    "*/*": components["schemas"]["ApiResponseRoleDetail"];
                };
            };
        };
    };
    update_2: {
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
                "application/json": components["schemas"]["UpdateRole"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseRoleDetail"];
                };
            };
        };
    };
    delete_1: {
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
                    "*/*": components["schemas"]["ApiResponseVoid"];
                };
            };
        };
    };
    update_3: {
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
                "application/json": components["schemas"]["UpdateMenu"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseMenuNode"];
                };
            };
        };
    };
    delete_2: {
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
                    "*/*": components["schemas"]["ApiResponseMenuDeleteImpact"];
                };
            };
        };
    };
    detail_2: {
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
                    "*/*": components["schemas"]["ApiResponseDictionaryView"];
                };
            };
        };
    };
    update_4: {
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
                "application/json": components["schemas"]["UpdateDictionary"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseDictionaryView"];
                };
            };
        };
    };
    updateItem: {
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
                "application/json": components["schemas"]["UpdateDictionaryItem"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseDictionaryItemView"];
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
    resetPassword: {
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
                "application/json": components["schemas"]["ResetPassword"];
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
    list_2: {
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
                    "*/*": components["schemas"]["ApiResponsePageResultRoleListItem"];
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
                "application/json": components["schemas"]["CreateRole"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseRoleDetail"];
                };
            };
        };
    };
    enable_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChangeRoleStatus"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListRoleListItem"];
                };
            };
        };
    };
    disable_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ChangeRoleStatus"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseListRoleListItem"];
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
                    "*/*": components["schemas"]["ApiResponseListMenuNode"];
                };
            };
        };
    };
    create_3: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateMenu"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseMenuNode"];
                };
            };
        };
    };
    restore: {
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
                    "*/*": components["schemas"]["ApiResponseRestoreMenusResult"];
                };
            };
        };
    };
    list_3: {
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
                    "*/*": components["schemas"]["ApiResponseListDictionaryListItem"];
                };
            };
        };
    };
    create_4: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateDictionary"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseDictionaryView"];
                };
            };
        };
    };
    createItem: {
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
                "application/json": components["schemas"]["CreateDictionaryItem"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ApiResponseDictionaryItemView"];
                };
            };
        };
    };
    create_5: {
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
    tree_1: {
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
    members: {
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
                    "*/*": components["schemas"]["ApiResponsePageResultRoleMember"];
                };
            };
        };
    };
    permissionTree: {
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
                    "*/*": components["schemas"]["ApiResponseListMenuNode"];
                };
            };
        };
    };
    options: {
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
    deleteImpact: {
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
                    "*/*": components["schemas"]["ApiResponseMenuDeleteImpact"];
                };
            };
        };
    };
    routeKeys: {
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
                    "*/*": components["schemas"]["ApiResponseListRouteKeyOption"];
                };
            };
        };
    };
    icons: {
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
                    "*/*": components["schemas"]["ApiResponseListIconOption"];
                };
            };
        };
    };
    myMenus: {
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
                    "*/*": components["schemas"]["ApiResponseListNavigationNode"];
                };
            };
        };
    };
    list_4: {
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
                    "*/*": components["schemas"]["ApiResponseListDictionaryView"];
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
