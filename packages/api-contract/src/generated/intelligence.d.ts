export interface paths {
  '/api/intelligence-topics': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询本单位收到或发出的情报，不扩大数据范围 */
    get: operations['intelligenceList'];
    put?: never;
    post: operations['intelligenceCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/intelligence-topics/unit-options': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['intelligenceOptions'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/intelligence-topics/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查看情报及当前单位可见的流转历史 */
    get: operations['intelligenceDetail'];
    put: operations['intelligenceUpdate'];
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/intelligence-topics/{id}/assessment-context': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['assessmentContext'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/intelligence-topics/{id}/assessments': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['assessmentList'];
    put?: never;
    post: operations['assessmentRecord'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/intelligence-topics/{id}/receipts/{receiptId}/feedbacks': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['intelligenceFeedback'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/intelligence-topics/{id}/receipts/{receiptId}/forward': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['intelligenceForward'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/intelligence-topics/{id}/receipts/{receiptId}/sign': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['intelligenceSign'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/intelligence-topics/{id}/send': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['intelligenceSend'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/intelligence-topics/{id}/supplements': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['intelligenceSupplement'];
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
    ApiResponseAssessment: {
      code: string;
      data: components['schemas']['Assessment'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseAssessmentContext: {
      code: string;
      data: components['schemas']['AssessmentContext'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseIntelligenceDetail: {
      code: string;
      data: components['schemas']['IntelligenceDetail'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListIntelligenceUnitOption: {
      code: string;
      data: components['schemas']['IntelligenceUnitOption'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultAssessment: {
      code: string;
      data: components['schemas']['PageResultAssessment'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultIntelligenceListItem: {
      code: string;
      data: components['schemas']['PageResultIntelligenceListItem'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    Assessment: {
      analysis: string;
      /** Format: date-time */
      createdAt: string;
      id: string;
      receiptId?: string;
      recommendationCode: string;
      unitName: string;
      userName: string;
    };
    AssessmentContext: {
      canAssess: boolean;
      reason?: string;
      reasonCode?: string;
      signedReceipts: components['schemas']['SignedReceipt'][];
      sourceMine: boolean;
    };
    FieldError: {
      field: string;
      message: string;
    };
    IntelligenceAction: {
      code: string;
      enabled: boolean;
      reason: string | null;
      reasonCode: string | null;
    };
    IntelligenceDetail: {
      allowedActions: components['schemas']['IntelligenceAction'][];
      body: string;
      /** Format: date-time */
      createdAt: string;
      draftNote: string | null;
      draftTargetUnitCodes: string[];
      id: string;
      /** Format: date-time */
      publishedAt: string | null;
      receipts: components['schemas']['IntelligenceReceipt'][];
      scopeUnitCodes: string[];
      sourceMine: boolean;
      sourceUnitName: string;
      sourceUserName: string;
      status: string;
      supplements: components['schemas']['IntelligenceSupplement'][];
      title: string;
      topicNo: string;
      /** Format: int32 */
      version: number;
    };
    IntelligenceDraftRequest: {
      body: string;
      note?: string;
      scopeUnitCodes: string[];
      targetUnitCodes: string[];
      title: string;
      /** Format: int32 */
      version: number;
    };
    IntelligenceFeedback: {
      body: string;
      /** Format: date-time */
      createdAt: string;
      id: string;
      unitName: string;
      userName: string;
    };
    IntelligenceFeedbackRequest: {
      body: string;
    };
    IntelligenceListItem: {
      /** Format: date-time */
      createdAt: string;
      id: string;
      /** Format: int64 */
      myReceiptCount: number;
      /** Format: int64 */
      pendingReceiptCount: number;
      /** Format: date-time */
      publishedAt: string | null;
      sourceUnitName: string;
      status: string;
      title: string;
      topicNo: string;
    };
    IntelligenceReceipt: {
      allowedActions: components['schemas']['IntelligenceAction'][];
      assessmentSummary?: string | null;
      feedbacks: components['schemas']['IntelligenceFeedback'][];
      fromUnitName: string;
      id: string;
      mine: boolean;
      note: string;
      parentReceiptId: string | null;
      sendId: string;
      senderName: string;
      /** Format: date-time */
      sentAt: string;
      /** Format: date-time */
      signedAt: string | null;
      signedByName: string | null;
      toUnitName: string;
    };
    IntelligenceSendRequest: {
      assessmentId?: string;
      assessmentSummary?: string;
      note?: string;
      targetUnitCodes: string[];
    };
    IntelligenceSupplement: {
      body: string;
      /** Format: date-time */
      createdAt: string;
      id: string;
      kind: string;
      unitName: string;
      userName: string;
    };
    IntelligenceSupplementRequest: {
      body: string;
      kind: string;
    };
    IntelligenceUnitOption: {
      code: string;
      enabled: boolean;
      /** Format: int32 */
      level: number;
      name: string;
      parentCode: string | null;
      targetEligible: boolean;
    };
    PageResultAssessment: {
      items: components['schemas']['Assessment'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultIntelligenceListItem: {
      items: components['schemas']['IntelligenceListItem'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    RecordAssessment: {
      analysis: string;
      receiptId?: string;
      recommendationCode: string;
    };
    SignedReceipt: {
      fromUnitName: string;
      id: string;
      /** Format: date-time */
      signedAt: string;
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
  intelligenceList: {
    parameters: {
      query?: {
        view?: string;
        status?: string;
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
          '*/*': components['schemas']['ApiResponsePageResultIntelligenceListItem'];
        };
      };
    };
  };
  intelligenceCreate: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path?: never;
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['IntelligenceDraftRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseIntelligenceDetail'];
        };
      };
    };
  };
  intelligenceOptions: {
    parameters: {
      query: {
        action: string;
        topicId?: string;
        receiptId?: string;
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
          '*/*': components['schemas']['ApiResponseListIntelligenceUnitOption'];
        };
      };
    };
  };
  intelligenceDetail: {
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
          '*/*': components['schemas']['ApiResponseIntelligenceDetail'];
        };
      };
    };
  };
  intelligenceUpdate: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['IntelligenceDraftRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseIntelligenceDetail'];
        };
      };
    };
  };
  assessmentContext: {
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
          '*/*': components['schemas']['ApiResponseAssessmentContext'];
        };
      };
    };
  };
  assessmentList: {
    parameters: {
      query?: {
        page?: number;
        pageSize?: number;
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
          '*/*': components['schemas']['ApiResponsePageResultAssessment'];
        };
      };
    };
  };
  assessmentRecord: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['RecordAssessment'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseAssessment'];
        };
      };
    };
  };
  intelligenceFeedback: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        receiptId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['IntelligenceFeedbackRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseIntelligenceDetail'];
        };
      };
    };
  };
  intelligenceForward: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        receiptId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['IntelligenceSendRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseIntelligenceDetail'];
        };
      };
    };
  };
  intelligenceSign: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        receiptId: string;
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
          '*/*': components['schemas']['ApiResponseIntelligenceDetail'];
        };
      };
    };
  };
  intelligenceSend: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['IntelligenceSendRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseIntelligenceDetail'];
        };
      };
    };
  };
  intelligenceSupplement: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['IntelligenceSupplementRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseIntelligenceDetail'];
        };
      };
    };
  };
}
