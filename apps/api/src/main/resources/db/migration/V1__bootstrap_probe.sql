CREATE TABLE bootstrap_probe (
    id CHAR(36) NOT NULL PRIMARY KEY,
    note VARCHAR(120) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_bootstrap_probe_created_at (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO bootstrap_probe (id, note)
VALUES ('00000000-0000-4000-8000-000000000001', '这是新项目的本地合成联调数据');
