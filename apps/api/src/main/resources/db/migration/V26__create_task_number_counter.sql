-- owner: task。仅用于新任务，旧 TASK 编号不改写。
CREATE TABLE task_no_counter (
    counter_date DATE NOT NULL COMMENT '按 Asia/Shanghai 自然日分组的发号日期',
    last_seq INT NOT NULL COMMENT '当日最近已分配序号；随创建事务提交或回滚，从 1 开始',
    PRIMARY KEY (counter_date),
    CONSTRAINT ck_task_no_counter_seq CHECK (last_seq > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='task 模块：新任务可读编号的每日事务计数器';
