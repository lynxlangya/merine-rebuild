export interface paths {
  '/api/intelligence-topics/{id}/task-context': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['taskIntelligenceContext'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/intelligence-topics/{id}/tasks': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['taskIntelligenceList'];
    put?: never;
    post: operations['taskIntelligenceCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 按当前单位权限与参与范围查询任务 */
    get: operations['taskList'];
    put?: never;
    /** 创建任务并向多个直属下级下发 */
    post: operations['taskCreate'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/target-units': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询当前动作可选的直属下级或同级支队 */
    get: operations['taskTargets'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    /** 查询任务详情和可见分支 */
    get: operations['taskDetail'];
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/branches/{branchId}/accept': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['taskAccept'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/branches/{branchId}/dispatch': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['taskDispatch'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/branches/{branchId}/progress': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['taskProgress'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/branches/{branchId}/reassign': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['taskReassign'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/branches/{branchId}/recall': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['taskRecall'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/branches/{branchId}/results': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['taskResult'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/branches/{branchId}/return': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['taskReturnTask'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/branches/{branchId}/transfer-requests': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['taskTransfer'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/branches/{branchId}/transfer-requests/{transferId}/decide': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['taskDecide'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/branches/{branchId}/transfer-requests/{transferId}/respond': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['taskRespond'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/branches/{branchId}/transfer-requests/{transferId}/withdraw': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['taskWithdraw'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/close': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: operations['taskClose'];
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/tasks/{id}/intelligence-source': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: operations['taskIntelligenceSource'];
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
    Action: {
      actorUnitName?: string;
      actorUserName?: string;
      branchId?: string;
      code?: string;
      /** Format: date-time */
      newDueAt?: string;
      note?: string;
      /** Format: date-time */
      occurredAt?: string;
      /** Format: date-time */
      oldDueAt?: string;
      reasonCode?: string;
      targetUnitName?: string;
    };
    AllowedAction: {
      code?: string;
      enabled?: boolean;
      reason?: string;
      reasonCode?: string;
      transferId?: string;
    };
    ApiResponseIntelligenceTaskContext: {
      code: string;
      data: components['schemas']['IntelligenceTaskContext'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseListUnitOption: {
      code: string;
      data: components['schemas']['UnitOption'][];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultLinkedIntelligenceTask: {
      code: string;
      data: components['schemas']['PageResultLinkedIntelligenceTask'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponsePageResultTaskListItem: {
      code: string;
      data: components['schemas']['PageResultTaskListItem'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseTaskDetail: {
      code: string;
      data: components['schemas']['TaskDetail'];
      /** @description 字段级校验错误；无字段错误时为 null */
      fieldErrors?: components['schemas']['FieldError'][];
      message: string;
      requestId: string;
    };
    ApiResponseTaskIntelligenceSource: {
      code: string;
      data: components['schemas']['TaskIntelligenceSource'];
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
    Assignment: {
      /** Format: date-time */
      acceptedAt?: string;
      branchId?: string;
      /** Format: date-time */
      dueAt?: string;
      endReason?: string;
      endReasonCode?: string;
      /** Format: date-time */
      endedAt?: string;
      fromUnitName?: string;
      id?: string;
      sourceAction?: string;
      status?: string;
      toUnitName?: string;
    };
    Branch: {
      allowedActions?: components['schemas']['AllowedAction'][];
      assignments?: components['schemas']['Assignment'][];
      canDispatchDownward?: boolean;
      canTransferPeer?: boolean;
      /** Format: date-time */
      completedAt?: string;
      currentAssignmentId?: string;
      currentMine?: boolean;
      expectedResult?: string;
      id?: string;
      instruction?: string;
      parentBranchId?: string;
      result?: components['schemas']['Result'];
      status?: string;
      transfers?: components['schemas']['Transfer'][];
    };
    Close: {
      conclusion: string;
    };
    Create: {
      /** Format: date-time */
      dueAt: string;
      expectedResult: string;
      instruction: string;
      targetUnitCodes: string[];
      title: string;
    };
    CreateIntelligenceTask: {
      assessmentId?: string;
      backgroundSummary: string;
      /** Format: date-time */
      dueAt: string;
      expectedResult: string;
      instruction: string;
      newAssessment?: components['schemas']['RecordAssessment'];
      sourceSupplementCheckpoint?: string;
      targetUnitCodes: string[];
      title: string;
    };
    Dispatch: {
      /** Format: date-time */
      dueAt: string;
      expectedResult: string;
      instruction: string;
      targetUnitCodes: string[];
    };
    FieldError: {
      field: string;
      message: string;
    };
    IntelligenceTaskContext: {
      assessmentContext: components['schemas']['AssessmentContext'];
      assessments: components['schemas']['Assessment'][];
      canCreate: boolean;
      reason?: string;
      sourceSupplementCheckpoint?: string;
      targets: components['schemas']['UnitOption'][];
    };
    LinkedIntelligenceTask: {
      /** Format: date-time */
      dueAt: string;
      id: string;
      issuerUnitName: string;
      status: string;
      taskNo: string;
      title: string;
    };
    PageResultLinkedIntelligenceTask: {
      items: components['schemas']['LinkedIntelligenceTask'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    PageResultTaskListItem: {
      items: components['schemas']['TaskListItem'][];
      /** Format: int32 */
      page: number;
      /** Format: int32 */
      pageSize: number;
      /** Format: int64 */
      total: number;
    };
    Progress: {
      note: string;
    };
    Recall: {
      reason: string;
    };
    RecordAssessment: {
      analysis: string;
      receiptId?: string;
      recommendationCode: string;
    };
    Result: {
      branchId?: string;
      conclusion?: string;
      handlingDetail?: string;
      id?: string;
      outcomeCode?: string;
      /** Format: date-time */
      submittedAt?: string;
      submittedByName?: string;
      suggestedUnitCode?: string;
      suggestedUnitName?: string;
    };
    ReturnTask: {
      reason: string;
      reasonCode: string;
    };
    SignedReceipt: {
      fromUnitName: string;
      id: string;
      /** Format: date-time */
      signedAt: string;
    };
    SubmitResult: {
      conclusion: string;
      handlingDetail: string;
      outcomeCode: string;
    };
    TaskDetail: {
      actions?: components['schemas']['Action'][];
      allowedActions?: components['schemas']['AllowedAction'][];
      branches?: components['schemas']['Branch'][];
      closedByName?: string;
      /** Format: date-time */
      completedAt?: string;
      conclusion?: string;
      /** Format: date-time */
      createdAt?: string;
      /** Format: date-time */
      currentDueAt?: string;
      expectedResult?: string;
      id?: string;
      /** Format: date-time */
      initialDueAt?: string;
      instruction?: string;
      issuerMine?: boolean;
      issuerUnitName?: string;
      issuerUserName?: string;
      sourceResultId?: string;
      status?: string;
      taskNo?: string;
      title?: string;
    };
    TaskIntelligenceSource: {
      adoptedAssessment?: components['schemas']['Assessment'];
      backgroundSummary?: string;
      linked: boolean;
      sourceChanged: boolean;
      title?: string;
      topicId?: string;
      topicNo?: string;
    };
    TaskListItem: {
      /** Format: date-time */
      createdAt?: string;
      /** Format: date-time */
      currentDueAt?: string;
      currentResponsibleUnits?: string;
      id?: string;
      /** Format: date-time */
      initialDueAt?: string;
      issuerUnitName?: string;
      /** Format: date-time */
      lastActionAt?: string;
      myStatus?: string;
      /** Format: int64 */
      openBranchCount?: number;
      /** Format: int64 */
      overdueBranchCount?: number;
      /** Format: int64 */
      pendingTransferCount?: number;
      status?: string;
      taskNo?: string;
      title?: string;
    };
    Transfer: {
      /** Format: date-time */
      approvedDueAt?: string;
      branchId?: string;
      evidenceSummary?: string;
      fromUnitName?: string;
      id?: string;
      /** Format: date-time */
      issuerDecidedAt?: string;
      issuerDecisionReason?: string;
      reason?: string;
      remainingWork?: string;
      /** Format: date-time */
      requestedAt?: string;
      /** Format: int32 */
      requiredDurationMinutes?: number;
      status?: string;
      targetMine?: boolean;
      /** Format: date-time */
      targetRespondedAt?: string;
      targetResponseReason?: string;
      targetUnitCode?: string;
      targetUnitName?: string;
      workDone?: string;
    };
    TransferDecision: {
      approve?: boolean;
      /** Format: date-time */
      dueAt?: string;
      reason?: string;
    };
    TransferRequest: {
      evidenceSummary: string;
      reason: string;
      remainingWork: string;
      targetUnitCode: string;
      workDone: string;
    };
    TransferResponse: {
      accept?: boolean;
      reason?: string;
      /** Format: int32 */
      requiredDurationMinutes?: number;
    };
    UnitOption: {
      code?: string;
      /** Format: int32 */
      level?: number;
      name?: string;
      parentCode?: string;
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
  taskIntelligenceContext: {
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
          '*/*': components['schemas']['ApiResponseIntelligenceTaskContext'];
        };
      };
    };
  };
  taskIntelligenceList: {
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
          '*/*': components['schemas']['ApiResponsePageResultLinkedIntelligenceTask'];
        };
      };
    };
  };
  taskIntelligenceCreate: {
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
        'application/json': components['schemas']['CreateIntelligenceTask'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskList: {
    parameters: {
      query?: {
        tab?: string;
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
          '*/*': components['schemas']['ApiResponsePageResultTaskListItem'];
        };
      };
    };
  };
  taskCreate: {
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
        'application/json': components['schemas']['Create'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskTargets: {
    parameters: {
      query: {
        action: string;
        taskId?: string;
        branchId?: string;
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
          '*/*': components['schemas']['ApiResponseListUnitOption'];
        };
      };
    };
  };
  taskDetail: {
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
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskAccept: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        branchId: string;
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
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskDispatch: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        branchId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Dispatch'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskProgress: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        branchId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Progress'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskReassign: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        branchId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Dispatch'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskRecall: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        branchId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['Recall'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskResult: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        branchId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['SubmitResult'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskReturnTask: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        branchId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['ReturnTask'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskTransfer: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        branchId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['TransferRequest'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskDecide: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        branchId: string;
        transferId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['TransferDecision'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskRespond: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        branchId: string;
        transferId: string;
      };
      cookie?: never;
    };
    requestBody: {
      content: {
        'application/json': components['schemas']['TransferResponse'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskWithdraw: {
    parameters: {
      query?: never;
      header: {
        'Idempotency-Key': string;
      };
      path: {
        id: string;
        branchId: string;
        transferId: string;
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
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskClose: {
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
        'application/json': components['schemas']['Close'];
      };
    };
    responses: {
      /** @description OK */
      200: {
        headers: {
          [name: string]: unknown;
        };
        content: {
          '*/*': components['schemas']['ApiResponseTaskDetail'];
        };
      };
    };
  };
  taskIntelligenceSource: {
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
          '*/*': components['schemas']['ApiResponseTaskIntelligenceSource'];
        };
      };
    };
  };
}
