export interface paths {
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
        post: operations["create"];
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
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        CreateProbe: {
            note: string;
        };
        ApiResponseProbeRecord: {
            code: string;
            message: string;
            data: components["schemas"]["ProbeRecord"];
            requestId: string;
        };
        ProbeRecord: {
            id: string;
            note: string;
            /** Format: date-time */
            createdAt: string;
        };
        ApiResponseBootstrapStatus: {
            code: string;
            message: string;
            data: components["schemas"]["BootstrapStatus"];
            requestId: string;
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
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    create: {
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
}
