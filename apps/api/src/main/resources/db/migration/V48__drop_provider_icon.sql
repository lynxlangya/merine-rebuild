-- 供应商标识改为使用各家官方图标（apps/web/public/vendor-icons），配置里的自选图标码不再有意义。
-- 契约与界面同步移除该字段：界面按 vendor 取官方标识，不再读取本列。
ALTER TABLE ai_model_provider DROP COLUMN icon;
