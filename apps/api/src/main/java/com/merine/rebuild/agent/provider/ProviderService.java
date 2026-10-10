package com.merine.rebuild.agent.provider;

import com.merine.rebuild.agent.provider.dto.EffortCatalog;
import com.merine.rebuild.agent.provider.dto.ModelCatalog;
import com.merine.rebuild.agent.provider.dto.ModelOption;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.Discover;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort;
import com.merine.rebuild.agent.provider.dto.ProviderModel;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.ModelInput;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.Save;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.State;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.Vendor;
import com.merine.rebuild.agent.provider.dto.ProviderView;
import com.merine.rebuild.agent.provider.persistence.ProviderMapper;
import com.merine.rebuild.agent.provider.persistence.ProviderModelOptionRow;
import com.merine.rebuild.agent.provider.persistence.ProviderModelRow;
import com.merine.rebuild.agent.provider.persistence.ProviderRow;
import com.merine.rebuild.agent.provider.persistence.ProviderSecretRow;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.system.audit.AuditEvent;
import com.merine.rebuild.system.audit.AuditTrail;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderService implements ProviderTargetLookup {
    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);
    private final ProviderMapper mapper;
    private final ProviderKeyCipher cipher;
    private final ProviderCatalogClient catalog;
    private final AuditTrail audit;

    public ProviderService(ProviderMapper mapper, ProviderKeyCipher cipher,
                           ProviderCatalogClient catalog, AuditTrail audit) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.catalog = catalog;
        this.audit = audit;
    }

    /**
     * 批量查推理强度目录：给配置页逐模型的档位勾选提供依据，
     * 目录没收录的模型返回空集合，由界面按「不使用档位参数」呈现。
     */
    public EffortCatalog effortCatalog(Vendor vendor, List<String> modelIds) {
        List<EffortCatalog.ModelEfforts> models = (modelIds == null ? List.<String>of() : modelIds).stream()
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .map(id -> {
                    List<ReasoningEffort> efforts = ReasoningEffortCatalog.effortsFor(vendor, id);
                    ReasoningEffort preferred = ReasoningEffortCatalog.defaultFor(vendor, id);
                    return new EffortCatalog.ModelEfforts(id, efforts,
                            preferred != null && efforts.contains(preferred) ? preferred : null);
                })
                .toList();
        return new EffortCatalog(ReasoningEffortCatalog.covered(vendor), models);
    }

    /**
     * 拉取上游模型清单。地址与密钥先按保存时的同一套规则校验：
     * 新输入的密钥优先，其次用该连接已保存的密钥；两者都没有就拒绝，不发出站请求。
     */
    public ModelCatalog discover(Discover input) {
        String baseUrl = validUrl(input.baseUrl(), "baseUrl", true);
        String apiKey = sanitizeKey(input.apiKey());
        Vendor vendor = input.vendor();
        if (input.providerId() != null) {
            ProviderSecretRow provider = mapper.findSecret(input.providerId());
            if (provider == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", "供应商配置不存在");
            }
            vendor = provider.vendor();
            if (apiKey == null) apiKey = storedKey(input.providerId());
        }
        if (apiKey == null) {
            throw invalid("apiKey", "请先填写 API Key 再获取模型列表");
        }
        ModelCatalog catalogResult = catalog.fetch(vendor, baseUrl, apiKey);
        // 已保存的连接才拿得到名字；表单里刚填的密钥属于「还没保存就先看看有哪些模型」的场景。
        ProviderRow saved = input.providerId() == null ? null : mapper.find(input.providerId());
        int discovered = catalogResult.models().size();
        String label = saved == null ? "" : saved.name();
        String summary = saved == null
                ? "未保存的连接读取上游模型清单：返回 %d 个模型".formatted(discovered)
                : "「%s」读取上游模型清单：返回 %d 个模型".formatted(label, discovered);
        audit.recordCurrent(AuditEvent.succeeded("agent", "provider:discover-models", "PROVIDER",
                input.providerId() == null ? "" : input.providerId(), label, summary));
        return catalogResult;
    }

    /** 取已保存连接的密钥；连接不存在时按未找到处理，密钥解密失败按密钥不可用处理。 */
    String storedKey(String providerId) {
        ProviderSecretRow provider = mapper.findSecret(providerId);
        if (provider == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", "供应商配置不存在");
        }
        try {
            return cipher.decrypt(providerId, provider.apiKeyCipher());
        } catch (ApiException error) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_KEY_UNAVAILABLE",
                    "供应商密钥不可用，请重新保存配置");
        }
    }

    /** 密钥字符集校验：只允许可打印 ASCII，避免把换行或全角字符带进请求头。 */
    private static String sanitizeKey(String raw) {
        String key = raw == null || raw.isBlank() ? null : raw.trim();
        if (key != null && key.chars().anyMatch(c -> c <= 32 || c >= 127)) {
            throw invalid("apiKey", "API Key 不能含空格、换行或非 ASCII 字符");
        }
        return key;
    }

    @Transactional(readOnly = true)
    public PageResult<ProviderView> list(String search, int page, int pageSize) {
        String keyword = search.trim();
        List<ProviderRow> rows = mapper.list(keyword, pageSize, (long) (page - 1) * pageSize);
        Map<String, List<ProviderModel>> models = modelsByProvider(rows);
        return new PageResult<>(
                rows.stream()
                        .map(row -> view(row, models.getOrDefault(row.id(), List.of())))
                        .toList(),
                mapper.count(keyword), page, pageSize);
    }

    @Transactional(readOnly = true)
    public ProviderView find(String id) {
        ProviderRow row = mapper.find(id);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", "供应商配置不存在");
        }
        return view(row, modelsOf(id));
    }

    /** 使用侧只读：已启用连接下的启用模型，不返回地址、备注与密钥。 */
    @Transactional(readOnly = true)
    public List<ModelOption> modelOptions() {
        return mapper.modelOptions().stream()
                .map(row -> {
                    List<ReasoningEffort> efforts = parseEfforts(row.reasoningEfforts());
                    return new ModelOption(row.id(), row.providerId(), row.providerName(),
                            row.vendor(), row.modelId(), row.displayName(), efforts,
                            defaultEffort(row.vendor(), row.modelId(), efforts));
                })
                .toList();
    }

    /**
     * 供对话模块取可调用的连接：连接与模型都必须启用，密钥在这里解密后交给调用方；
     * 失败返回可预期业务错误，不把密文或解密异常细节带出去。
     */
    @Override
    @Transactional(readOnly = true)
    public ProviderTarget requireEnabled(String providerId, String modelId,
                                         ReasoningEffort reasoningEffort) {
        ProviderSecretRow provider = mapper.findSecret(providerId);
        if (provider == null || provider.status() != State.ENABLED) {
            throw unavailable("providerId", "供应商配置不存在或已停用");
        }
        ProviderModelRow model = mapper.findModel(providerId, modelId);
        if (model == null || model.status() != State.ENABLED) {
            throw unavailable("modelId", "该连接下没有启用这个模型");
        }
        if (reasoningEffort != null && !parseEfforts(model.reasoningEfforts()).contains(reasoningEffort)) {
            throw unavailable("reasoningEffort", "该模型未启用这个推理强度");
        }
        String apiKey;
        try {
            apiKey = cipher.decrypt(providerId, provider.apiKeyCipher());
        } catch (ApiException error) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_KEY_UNAVAILABLE",
                    "供应商密钥不可用，请重新保存配置");
        }
        return new ProviderTarget(provider.id(), provider.name(), model.modelId(),
                provider.baseUrl(), apiKey, reasoningEffort);
    }

    private static ApiException unavailable(String field, String message) {
        return new ApiException(HttpStatus.CONFLICT, "CHAT_MODEL_UNAVAILABLE", message,
                List.of(new ApiResponse.FieldError(field, message)));
    }

    @Transactional
    public ProviderView create(Save raw) {
        Save input = normalize(raw);
        requireKey(input.apiKey());
        String id = UUID.randomUUID().toString();
        try {
            mapper.insert(id, input, cipher.encrypt(id, input.apiKey()));
        } catch (DuplicateKeyException e) {
            throw duplicateName();
        }
        replaceModels(id, input.models());
        log.info("Provider created: id={} vendor={} models={}", id, input.vendor(), input.models().size());
        ProviderView created = find(id);
        audit.recordCurrent(AuditEvent.succeeded("agent", "provider:create", "PROVIDER", id,
                created.name(), "新建连接「%s」（%s，%d 个模型，%s）".formatted(created.name(),
                        created.vendor(), created.models().size(), created.baseUrl())));
        return created;
    }

    @Transactional
    public ProviderView update(String id, Save raw) {
        Save input = normalize(raw);
        ProviderRow current = mapper.lock(id);
        requireVersion(current, input.version());
        // 不允许通过更换目标地址，将原密钥隐式转交给另一供应商。
        if (current.vendor() != input.vendor() || !current.baseUrl().equals(input.baseUrl())) {
            requireKey(input.apiKey());
        }
        String encrypted = input.apiKey() == null ? null : cipher.encrypt(id, input.apiKey());
        try {
            if (mapper.update(id, input, encrypted) != 1) throw conflict();
        } catch (DuplicateKeyException e) {
            throw duplicateName();
        }
        // 模型集合随连接一起保存：先锁连接行（上面已完成），再按 modelId 对账增删改。
        int modelsBefore = modelsOf(id).size();
        replaceModels(id, input.models());
        log.info("Provider updated: id={} vendor={} models={}", id, input.vendor(), input.models().size());
        ProviderView updated = find(id);
        List<String> changes = new ArrayList<>();
        changes.add("模型 %d → %d".formatted(modelsBefore, updated.models().size()));
        if (!current.baseUrl().equals(updated.baseUrl())) {
            changes.add("请求地址改为 %s".formatted(updated.baseUrl()));
        }
        if (input.apiKey() != null) {
            changes.add("更换密钥");
        }
        if (current.status() != updated.status()) {
            changes.add("状态改为 %s".formatted(updated.status()));
        }
        if (current.vendor() != updated.vendor()) {
            changes.add("供应商标识改为 %s".formatted(updated.vendor()));
        }
        audit.recordCurrent(AuditEvent.succeeded("agent", "provider:update", "PROVIDER", id,
                updated.name(), "修改连接「%s」：%s".formatted(updated.name(),
                        String.join("；", changes))));
        return updated;
    }

    @Transactional
    public void delete(String id, int version) {
        ProviderRow current = mapper.lock(id);
        requireVersion(current, version);
        mapper.deleteModelsOfProvider(id);
        if (mapper.delete(id, version) != 1) throw conflict();
        log.info("Provider deleted: id={}", id);
        audit.recordCurrent(AuditEvent.succeeded("agent", "provider:delete", "PROVIDER", id,
                current.name(), "删除连接「%s」（%s）".formatted(current.name(), current.baseUrl())));
    }

    /** 列表页只发一次模型查询，再按 providerId 分组；不逐条连接查询。 */
    private Map<String, List<ProviderModel>> modelsByProvider(List<ProviderRow> rows) {
        if (rows.isEmpty()) return Map.of();
        List<String> ids = rows.stream().map(ProviderRow::id).toList();
        Map<String, List<ProviderModel>> grouped = new LinkedHashMap<>();
        for (ProviderModelRow row : mapper.modelsOf(ids)) {
            grouped.computeIfAbsent(row.providerId(), key -> new ArrayList<>()).add(toModel(row));
        }
        return grouped;
    }

    private List<ProviderModel> modelsOf(String providerId) {
        return mapper.modelsOfProvider(providerId).stream().map(ProviderService::toModel).toList();
    }

    private static ProviderModel toModel(ProviderModelRow row) {
        return new ProviderModel(row.id(), row.modelId(), row.displayName(), row.remark(),
                parseEfforts(row.reasoningEfforts()), row.status(), row.sortOrder());
    }

    /**
     * 使用侧默认档位：优先用官方默认值，它不在可用集合里（被管理员裁掉）时退回「高」，
     * 都不满足就交给界面取第一档。
     */
    private static ReasoningEffort defaultEffort(Vendor vendor, String modelId,
                                                List<ReasoningEffort> efforts) {
        ReasoningEffort preferred = ReasoningEffortCatalog.defaultFor(vendor, modelId);
        if (preferred != null && efforts.contains(preferred)) return preferred;
        return efforts.contains(ReasoningEffort.HIGH) ? ReasoningEffort.HIGH : null;
    }

    /** 列里是逗号分隔的枚举码；固定按官方顺序（NONE/LOW/HIGH/MAX）返回，去重并忽略未知值。 */
    private static List<ReasoningEffort> parseEfforts(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        Set<String> codes = Set.of(raw.split(","));
        return java.util.Arrays.stream(ReasoningEffort.values())
                .filter(effort -> codes.contains(effort.name()))
                .toList();
    }

    private static String joinEfforts(List<ReasoningEffort> efforts) {
        return String.join(",", efforts.stream().map(Enum::name).toList());
    }

    private static ProviderView view(ProviderRow row, List<ProviderModel> models) {
        return new ProviderView(row.id(), row.vendor(), row.name(), row.remark(),
                row.website(), row.baseUrl(), row.status(), models, row.version(), row.updatedAt());
    }

    /** 按 modelId 对账：保留的更新、新的插入、缺的删除；列表顺序即展示顺序。 */
    private void replaceModels(String providerId, List<ModelInput> inputs) {
        List<ProviderModelRow> existing = mapper.modelsOfProvider(providerId);
        Map<String, String> idsByModelId = new LinkedHashMap<>();
        existing.forEach(row -> idsByModelId.put(row.modelId(), row.id()));
        Set<String> kept = new HashSet<>();
        int order = 0;
        for (ModelInput input : inputs) {
            String currentId = idsByModelId.get(input.modelId());
            String efforts = joinEfforts(input.reasoningEfforts());
            if (currentId == null) {
                mapper.insertModel(UUID.randomUUID().toString(), providerId, input.modelId(),
                        input.displayName(), input.remark(), efforts, input.status(), order);
            } else {
                kept.add(currentId);
                mapper.updateModel(currentId, input.displayName(), input.remark(), efforts,
                        input.status(), order);
            }
            order += 10;
        }
        existing.stream()
                .filter(row -> !kept.contains(row.id()))
                .forEach(row -> mapper.deleteModel(row.id()));
    }

    private static Save normalize(Save input) {
        String key = sanitizeKey(input.apiKey());
        return new Save(input.vendor(), input.name().trim(), input.remark().trim(),
                validUrl(input.website(), "website", false), validUrl(input.baseUrl(), "baseUrl", true),
                key, input.status(), normalizeModels(input.models()), input.version());
    }

    private static List<ModelInput> normalizeModels(List<ModelInput> raw) {
        if (raw == null) return List.of();
        List<ModelInput> models = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ModelInput item : raw) {
            String modelId = item.modelId().trim();
            if (!seen.add(modelId)) {
                throw invalid("models", "同一连接下的模型标识不能重复：" + modelId);
            }
            String displayName = item.displayName() == null || item.displayName().isBlank()
                    ? modelId : item.displayName().trim();
            String remark = item.remark() == null ? "" : item.remark().trim();
            List<ReasoningEffort> efforts = item.reasoningEfforts() == null
                    ? List.of()
                    : java.util.Arrays.stream(ReasoningEffort.values())
                            .filter(effort -> item.reasoningEfforts().contains(effort))
                            .toList();
            models.add(new ModelInput(modelId, displayName, remark, efforts, item.status()));
        }
        return List.copyOf(models);
    }

    private static String validUrl(String value, String field, boolean base) {
        try {
            URI uri = new URI(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException();
            }
            String normalized = uri.toASCIIString().replaceAll("/+$", "");
            if (base && (normalized.endsWith("/chat/completions") || normalized.endsWith("/messages")
                    || normalized.endsWith("/responses") || normalized.endsWith("/anthropic"))) {
                throw invalid(field, "请填写 OpenAI 兼容基础地址，不含具体接口路径或 Anthropic 地址");
            }
            return normalized;
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw invalid(field, "请填写有效的 HTTPS 地址，不含账号、查询参数或片段");
        }
    }

    private static void requireKey(String key) {
        if (key == null) {
            throw invalid("apiKey", "新建或更换供应商、请求地址时，请输入对应的 API Key");
        }
    }

    private static void requireVersion(ProviderRow current, Integer version) {
        if (current == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", "供应商配置不存在");
        }
        if (version == null || version != current.version()) throw conflict();
    }

    private static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "PROVIDER_CONFLICT", "配置已变化，请刷新后重新编辑");
    }

    private static ApiException duplicateName() {
        String message = "已有同名配置，请用名称区分不同账号或地域";
        return new ApiException(HttpStatus.CONFLICT, "PROVIDER_NAME_EXISTS", message,
                List.of(new ApiResponse.FieldError("name", message)));
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message,
                List.of(new ApiResponse.FieldError(field, message)));
    }
}
