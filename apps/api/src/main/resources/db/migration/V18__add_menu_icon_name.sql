-- 目的：菜单节点支持自定义导航图标（目录与页面）：数据库只保存图标名称，
--       具体图标组件由前端 iconRegistry 与后端 RegisteredIcons 两份同名清单共同维护。
-- 边界：
--   * 仅目录与页面参与导航渲染，页签/按钮的图标列必须为 NULL（CHECK 与应用层共同保证）；
--   * NULL 表示沿用默认图标：页面取路由注册表图标，目录取文件夹图标；不回填存量数据；
--   * 图标名称只影响展示，不参与权限判定，改图标不影响任何授权。
-- owner：system.menu
-- 说明：本文件在本地开发库与测试库重建后重放；已执行迁移保持不可变。

ALTER TABLE sys_menu
    ADD COLUMN icon_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '导航图标名称，取值须在 RegisteredIcons/iconRegistry 清单内；仅目录与页面设置，NULL 表示默认图标'
        AFTER route_key,
    ADD CONSTRAINT ck_sys_menu_icon
        CHECK (icon_name IS NULL OR menu_type IN ('DIRECTORY', 'PAGE'));
