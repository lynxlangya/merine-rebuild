export interface paths {
  '/api/agent/chat': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    /** 发起或续接一次本地演示执行 */
    post: operations['chatChat'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/chat/config': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 读取对话运行模式，用于界面标注本地演示或真实模型 */
    get: operations['chatConfig'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/chat/conversations': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 列出我的助手会话 */
    get: operations['chatConversations'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/chat/conversations/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    /** 删除我的会话（进行中的轮次会先被拒绝） */
    delete: operations['chatDeleteConversation'];
    options?: never;
    head?: never;
    /** 重命名我的会话 */
    patch: operations['chatRenameConversation'];
    trace?: never;
  };
  '/api/agent/chat/conversations/{id}/messages': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 读取我的某个会话的消息 */
    get: operations['chatConversationMessages'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/chat/requests/{idempotencyKey}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 按幂等键查询当前身份的原执行状态与受限快照 */
    get: operations['chatStatus'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/chat/requests/{idempotencyKey}/stop': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    /** 请求停止本次演示执行 */
    post: operations['chatStop'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/chat/runs': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 列出我的助手执行记录 */
    get: operations['chatRuns'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/chat/runs/stats': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 我的助手用量聚合（KPI、按天趋势、模型分布） */
    get: operations['chatRunStats'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/conversations': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 列出我的助手会话（最近更新在前） */
    get: operations['conversationList'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/conversations/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post?: never;
    /** 删除我的会话 */
    delete: operations['conversationDelete'];
    options?: never;
    head?: never;
    /** 重命名我的会话 */
    patch: operations['conversationRename'];
    trace?: never;
  };
  '/api/agent/conversations/{id}/messages': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 读取我的某个会话的消息 */
    get: operations['conversationMessages'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/models': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 读取已启用连接下的启用模型；不返回地址、备注与密钥 */
    get: operations['modelOptionList'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/providers': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['providerList'];
    put?: never;
    post: operations['providerCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/providers/models/discover': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['providerDiscover'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/providers/reasoning-efforts': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['providerReasoningEfforts'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/agent/providers/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: operations['providerUpdate'];
    post?: never;
    delete: operations['providerDelete'];
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
}
export type webhooks = Record<string, never>;
export interface components {
  schemas: {
    ApiResponseChatConversation: {
      code: string;
      data: components['schemas']['ChatConversation'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseChatRunStats: {
      code: string;
      data: components['schemas']['ChatRunStats'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseChatRunView: {
      code: string;
      data: components['schemas']['ChatRunView'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseChatRuntime: {
      code: string;
      data: components['schemas']['ChatRuntime'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListChatConversationMessage: {
      code: string;
      data: components['schemas']['ChatConversationMessage'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListModelOption: {
      code: string;
      data: components['schemas']['ModelOption'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseModelProvider: {
      code: string;
      data: components['schemas']['ModelProvider'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultChatConversation: {
      code: string;
      data: components['schemas']['PageResultChatConversation'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultChatRunRecord: {
      code: string;
      data: components['schemas']['PageResultChatRunRecord'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultModelProvider: {
      code: string;
      data: components['schemas']['PageResultModelProvider'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseProviderEffortCatalog: {
      code: string;
      data: components['schemas']['ProviderEffortCatalog'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseProviderModelCatalog: {
      code: string;
      data: components['schemas']['ProviderModelCatalog'];
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
    CancelRequested: {
      messageId: string;
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'CANCEL_REQUESTED';
    };
    Chart: {
      spec: components['schemas']['ChartSpec'];
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'CHART';
    };
    ChartMeta: {
      coverage?: string;
      metricId?: string;
      period?: string;
      updatedAt?: string;
    };
    ChartSeries: {
      data: number[];
      name: string;
    };
    ChartSpec: {
      categories?: string[];
      /** @enum {string} */
      chartType: 'LINE' | 'BAR' | 'STACKED_BAR' | 'PIE';
      meta?: components['schemas']['ChartMeta'];
      series: components['schemas']['ChartSeries'][];
      title?: string;
      unit?: string;
    };
    ChatAnswer: {
      freeText?: string;
      questionId: string;
      skipped?: boolean;
      values?: string[];
    };
    ChatContext: {
      period?: string;
      topic?: string;
    };
    ChatConversation: {
      /** Format: date-time */
      createdAt: string;
      id: string;
      lastModelId: string;
      lastProviderId: string;
      lastProviderName: string;
      /** @enum {string} */
      lastReasoningEffort?: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'XHIGH' | 'MAX';
      /** Format: int32 */
      messageCount: number;
      title: string;
      /** Format: date-time */
      updatedAt: string;
      /** Format: int32 */
      version: number;
    };
    ChatConversationMessage: {
      /** Format: date-time */
      createdAt: string;
      id: string;
      role: string;
      /** @enum {string} */
      status: 'RUNNING' | 'AWAITING_INPUT' | 'SUCCEEDED' | 'FAILED' | 'ABORTED';
      text: string;
    };
    ChatError: {
      /** @enum {string} */
      code:
        | 'MODEL_AUTH_FAILED'
        | 'MODEL_RATE_LIMITED'
        | 'MODEL_TIMEOUT'
        | 'MODEL_UNAVAILABLE'
        | 'MODEL_REQUEST_REJECTED'
        | 'UPSTREAM_PROTOCOL_ERROR'
        | 'INTERNAL';
      message: string;
      messageId?: string;
      retryable: boolean;
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'ERROR';
    };
    ChatPartView: {
      part?: components['schemas']['MessagePart'];
      partId?: string;
      /** @enum {string} */
      status?: 'STREAMING' | 'DONE' | 'FAILED' | 'ABORTED';
    };
    ChatRequest: {
      answers?: components['schemas']['ChatAnswer'][];
      clientConversationId: string;
      clientTimeZone: string;
      context?: components['schemas']['ChatContext'];
      conversationId?: string;
      generationId?: string;
      idempotencyKey: string;
      messages: components['schemas']['ChatTurn'][];
      modelId?: string;
      providerId?: string;
      /** @enum {string} */
      reasoningEffort?: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'XHIGH' | 'MAX';
    };
    ChatRunDailyPoint: {
      /** Format: int64 */
      completionTokens: number;
      date: string;
      /** Format: int64 */
      durationMs: number;
      /** Format: int32 */
      failed: number;
      /** Format: int64 */
      inputChars: number;
      /** Format: int64 */
      outputChars: number;
      /** Format: int64 */
      promptTokens: number;
      /** Format: int32 */
      runs: number;
    };
    ChatRunModelSlice: {
      /** Format: int64 */
      completionTokens: number;
      /** Format: int64 */
      durationMs: number;
      /** Format: int32 */
      failed: number;
      modelId: string;
      /** Format: int64 */
      promptTokens: number;
      providerId: string;
      providerName: string;
      /** Format: int32 */
      runs: number;
    };
    ChatRunRecord: {
      /** Format: int32 */
      completionTokens?: number;
      conversationId: string;
      /** Format: int32 */
      durationMs: number;
      errorCode: string;
      errorMessage: string;
      /** Format: date-time */
      finishedAt: string;
      id: string;
      /** Format: int32 */
      inputChars: number;
      modelId: string;
      /** Format: int32 */
      outputChars: number;
      /** Format: int32 */
      promptTokens?: number;
      providerName: string;
      /** Format: date-time */
      startedAt: string;
      /** @enum {string} */
      state?: 'RUNNING' | 'AWAITING_INPUT' | 'SUCCEEDED' | 'FAILED' | 'ABORTED';
    };
    ChatRunStats: {
      models: components['schemas']['ChatRunModelSlice'][];
      /** Format: int32 */
      offsetMinutes: number;
      series: components['schemas']['ChatRunDailyPoint'][];
      totals: components['schemas']['ChatRunStatsTotals'];
    };
    ChatRunStatsTotals: {
      /** Format: int32 */
      aborted: number;
      /** Format: int64 */
      completionTokens: number;
      /** Format: int64 */
      durationMs: number;
      /** Format: int32 */
      failed: number;
      /** Format: int64 */
      inputChars: number;
      /** Format: int64 */
      outputChars: number;
      /** Format: int64 */
      promptTokens: number;
      /** Format: int32 */
      runs: number;
      /** Format: int32 */
      runsWithoutUsage: number;
      /** Format: int32 */
      succeeded: number;
    };
    ChatRunView: {
      cancelRequested: boolean;
      clientConversationId: string;
      /** Format: date-time */
      finishedAt?: string;
      generationId: string;
      idempotencyKey: string;
      parts: components['schemas']['ChatPartView'][];
      question?: components['schemas']['QuestionAnswerState'];
      /** Format: date-time */
      startedAt: string;
      /** @enum {string} */
      status: 'RUNNING' | 'AWAITING_INPUT' | 'SUCCEEDED' | 'FAILED' | 'ABORTED';
    };
    ChatRuntime: {
      /** @enum {string} */
      executionMode: 'LOCAL_STUB' | 'PROVIDER';
    };
    /** @description SSE data 载荷；type 判别联合 */
    ChatStreamEvent: {
      type: string;
    } & (
      | components['schemas']['StreamStart']
      | components['schemas']['PartStart']
      | components['schemas']['TextDelta']
      | components['schemas']['PartSnapshot']
      | components['schemas']['PartDone']
      | components['schemas']['CancelRequested']
      | components['schemas']['MessageDone']
      | components['schemas']['ChatError']
    );
    ChatTurn: {
      /** @enum {string} */
      role: 'USER' | 'ASSISTANT';
      text: string;
    };
    DiscoverProviderModels: {
      apiKey?: string;
      baseUrl: string;
      providerId?: string;
      /** @enum {string} */
      vendor?:
        | 'DEEPSEEK'
        | 'QWEN'
        | 'ZHIPU'
        | 'MOONSHOT'
        | 'DOUBAO'
        | 'BAIDU'
        | 'TENCENT'
        | 'MINIMAX'
        | 'CUSTOM';
    };
    DiscoveredProviderModel: {
      modelId: string;
      /** @description 上游返回的归属方，可能为空 */
      ownedBy?: string;
      reasoningEfforts: ('NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'XHIGH' | 'MAX')[];
    };
    FieldError: {
      field: string;
      message: string;
    };
    MessageDone: {
      messageId: string;
      /** @enum {string} */
      status: 'SUCCEEDED' | 'AWAITING_INPUT' | 'ABORTED';
      usage?: components['schemas']['TokenUsage'];
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'MESSAGE_DONE';
    };
    MessagePart: {
      type: string;
    } & (
      | components['schemas']['Text']
      | components['schemas']['Table']
      | components['schemas']['Chart']
      | components['schemas']['Sources']
      | components['schemas']['Steps']
      | components['schemas']['Question']
      | components['schemas']['Notice']
    );
    ModelOption: {
      /** @enum {string} */
      defaultReasoningEffort?: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'XHIGH' | 'MAX';
      displayName: string;
      id: string;
      modelId: string;
      providerId: string;
      providerName: string;
      reasoningEfforts: ('NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'XHIGH' | 'MAX')[];
      /** @enum {string} */
      vendor:
        | 'DEEPSEEK'
        | 'QWEN'
        | 'ZHIPU'
        | 'MOONSHOT'
        | 'DOUBAO'
        | 'BAIDU'
        | 'TENCENT'
        | 'MINIMAX'
        | 'CUSTOM';
    };
    ModelProvider: {
      baseUrl: string;
      id: string;
      models: components['schemas']['ProviderModel'][];
      name: string;
      remark: string;
      /** @enum {string} */
      status: 'ENABLED' | 'DISABLED';
      /** Format: date-time */
      updatedAt: string;
      /** @enum {string} */
      vendor:
        | 'DEEPSEEK'
        | 'QWEN'
        | 'ZHIPU'
        | 'MOONSHOT'
        | 'DOUBAO'
        | 'BAIDU'
        | 'TENCENT'
        | 'MINIMAX'
        | 'CUSTOM';
      /** Format: int32 */
      version: number;
      website: string;
    };
    Notice: {
      code?: string;
      /** @enum {string} */
      level: 'INFO' | 'WARNING' | 'ERROR';
      text: string;
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'NOTICE';
    };
    PageResultChatConversation: {
      items: components['schemas']['ChatConversation'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultChatRunRecord: {
      items: components['schemas']['ChatRunRecord'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultModelProvider: {
      items: components['schemas']['ModelProvider'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PartDone: {
      messageId: string;
      partId: string;
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'PART_DONE';
    };
    PartSnapshot: {
      messageId: string;
      part: components['schemas']['MessagePart'];
      partId: string;
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'PART_SNAPSHOT';
    };
    PartStart: {
      messageId: string;
      partId: string;
      partType: string;
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'PART_START';
    };
    ProviderEffortCatalog: {
      covered: boolean;
      models: components['schemas']['ProviderModelEfforts'][];
    };
    ProviderEffortLookup: {
      modelIds?: string[];
      /** @enum {string} */
      vendor:
        | 'DEEPSEEK'
        | 'QWEN'
        | 'ZHIPU'
        | 'MOONSHOT'
        | 'DOUBAO'
        | 'BAIDU'
        | 'TENCENT'
        | 'MINIMAX'
        | 'CUSTOM';
    };
    ProviderModel: {
      displayName: string;
      id: string;
      modelId: string;
      reasoningEfforts: ('NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'XHIGH' | 'MAX')[];
      remark: string;
      /** Format: int32 */
      sortOrder: number;
      /** @enum {string} */
      status: 'ENABLED' | 'DISABLED';
    };
    ProviderModelCatalog: {
      models: components['schemas']['DiscoveredProviderModel'][];
    };
    ProviderModelEfforts: {
      /** @enum {string} */
      defaultReasoningEffort?: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'XHIGH' | 'MAX';
      modelId: string;
      reasoningEfforts: ('NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'XHIGH' | 'MAX')[];
    };
    Question: {
      allowOther?: boolean;
      /** Format: int32 */
      maxLength?: number;
      /** Format: int32 */
      maxSelections?: number;
      /** Format: int32 */
      minSelections?: number;
      /** @enum {string} */
      mode: 'SINGLE' | 'MULTIPLE' | 'TEXT';
      options?: components['schemas']['QuestionOption'][];
      placeholder?: string;
      prompt: string;
      questionId: string;
      required: boolean;
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'QUESTION';
    };
    QuestionAnswerState: {
      answered?: boolean;
      freeText?: string;
      questionId?: string;
      values?: string[];
    };
    QuestionOption: {
      hint?: string;
      label?: string;
      value: string;
    };
    RenameChatConversation: {
      title: string;
      /** Format: int32 */
      version?: number;
    };
    SaveModelProvider: {
      /** @description 新建必填；编辑留空保留原密钥；更换供应商或地址时须重新输入 */
      apiKey?: string;
      baseUrl: string;
      models?: components['schemas']['SaveProviderModel'][];
      name: string;
      remark: string;
      /** @enum {string} */
      status: 'ENABLED' | 'DISABLED';
      /** @enum {string} */
      vendor:
        | 'DEEPSEEK'
        | 'QWEN'
        | 'ZHIPU'
        | 'MOONSHOT'
        | 'DOUBAO'
        | 'BAIDU'
        | 'TENCENT'
        | 'MINIMAX'
        | 'CUSTOM';
      /** Format: int32 */
      version?: number;
      website: string;
    };
    SaveProviderModel: {
      displayName?: string;
      modelId: string;
      reasoningEfforts?: ('NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'XHIGH' | 'MAX')[];
      remark?: string;
      /** @enum {string} */
      status: 'ENABLED' | 'DISABLED';
    };
    SourceRef: {
      /** @enum {string} */
      kind: 'TASK' | 'FLOW' | 'INTEL' | 'MENU' | 'EXTERNAL';
      refId?: string;
      routeKey?: string;
      snippet?: string;
      title: string;
      url?: string;
    };
    Sources: {
      items: components['schemas']['SourceRef'][];
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'SOURCES';
    };
    StepItem: {
      /** @enum {string} */
      label: 'RETRIEVE' | 'PLAN' | 'QUERY' | 'ANALYZE' | 'CITE';
      /** @enum {string} */
      status: 'RUNNING' | 'DONE' | 'FAILED';
      text: string;
    };
    Steps: {
      items: components['schemas']['StepItem'][];
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'STEPS';
    };
    StreamStart: {
      clientConversationId: string;
      conversationId: string;
      /** @enum {string} */
      executionMode: 'LOCAL_STUB' | 'PROVIDER';
      generationId: string;
      messageId: string;
      model?: string;
      /** Format: int32 */
      protocolVersion: number;
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'STREAM_START';
    };
    Table: {
      columns: components['schemas']['TableColumn'][];
      note?: string;
      rows: string[][];
      title?: string;
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'TABLE';
    };
    TableColumn: {
      key?: string;
      numeric?: boolean;
      title?: string;
      unit?: string;
    };
    Text: {
      text: string;
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'TEXT';
    };
    TextDelta: {
      delta: string;
      messageId: string;
      partId: string;
    } & {
      /**
       * @description discriminator enum property added by openapi-typescript
       * @enum {string}
       */
      type: 'TEXT_DELTA';
    };
    TokenUsage: {
      /** Format: int32 */
      inputTokens?: number;
      /** Format: int32 */
      outputTokens?: number;
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
  chatChat: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ChatRequest'];
      };
    };
    responses: {
      /** @description SSE 事件流，或同 key 已完成时的快照 JSON */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          'application/json': components['schemas']['ChatRunView'];
          'text/event-stream': components['schemas']['ChatStreamEvent'];
        };
      };
    };
  };
  chatConfig: {
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
          '*/*': components['schemas']['ApiResponseChatRuntime'];
        };
      };
    };
  };
  chatConversations: {
    parameters: {
      query?: {
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
          '*/*': components['schemas']['ApiResponsePageResultChatConversation'];
        };
      };
    };
  };
  chatDeleteConversation: {
    parameters: {
      query: {
        version: number;
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
          '*/*': components['schemas']['ApiResponseVoid'];
        };
      };
    };
  };
  chatRenameConversation: {
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
        'application/json': components['schemas']['RenameChatConversation'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseChatConversation'];
        };
      };
    };
  };
  chatConversationMessages: {
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
          '*/*': components['schemas']['ApiResponseListChatConversationMessage'];
        };
      };
    };
  };
  chatStatus: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        idempotencyKey: string;
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
          '*/*': components['schemas']['ApiResponseChatRunView'];
        };
      };
    };
  };
  chatStop: {
    parameters: {
      query?: never;
      header?: never;
      path: {
        idempotencyKey: string;
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
          '*/*': components['schemas']['ApiResponseChatRunView'];
        };
      };
    };
  };
  chatRuns: {
    parameters: {
      query?: {
        page?: number;
        pageSize?: number;
        state?: string;
        providerId?: string;
        modelId?: string;
        days?: number;
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
          '*/*': components['schemas']['ApiResponsePageResultChatRunRecord'];
        };
      };
    };
  };
  chatRunStats: {
    parameters: {
      query?: {
        days?: number;
        providerId?: string;
        modelId?: string;
        offsetMinutes?: number;
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
          '*/*': components['schemas']['ApiResponseChatRunStats'];
        };
      };
    };
  };
  conversationList: {
    parameters: {
      query?: {
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
          '*/*': components['schemas']['ApiResponsePageResultChatConversation'];
        };
      };
    };
  };
  conversationDelete: {
    parameters: {
      query: {
        version: number;
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
          '*/*': components['schemas']['ApiResponseVoid'];
        };
      };
    };
  };
  conversationRename: {
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
        'application/json': components['schemas']['RenameChatConversation'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseChatConversation'];
        };
      };
    };
  };
  conversationMessages: {
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
          '*/*': components['schemas']['ApiResponseListChatConversationMessage'];
        };
      };
    };
  };
  modelOptionList: {
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
          '*/*': components['schemas']['ApiResponseListModelOption'];
        };
      };
    };
  };
  providerList: {
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
          '*/*': components['schemas']['ApiResponsePageResultModelProvider'];
        };
      };
    };
  };
  providerCreate: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['SaveModelProvider'];
      };
    };
    responses: {
      /** @description Created */
      201: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseModelProvider'];
        };
      };
    };
  };
  providerDiscover: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['DiscoverProviderModels'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseProviderModelCatalog'];
        };
      };
    };
  };
  providerReasoningEfforts: {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ProviderEffortLookup'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseProviderEffortCatalog'];
        };
      };
    };
  };
  providerUpdate: {
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
        'application/json': components['schemas']['SaveModelProvider'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseModelProvider'];
        };
      };
    };
  };
  providerDelete: {
    parameters: {
      query: {
        version: number;
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
          '*/*': components['schemas']['ApiResponseVoid'];
        };
      };
    };
  };
}
