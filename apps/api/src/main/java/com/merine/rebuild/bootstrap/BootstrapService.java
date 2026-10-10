package com.merine.rebuild.bootstrap;

import com.merine.rebuild.system.audit.AuditEvent;
import com.merine.rebuild.system.audit.AuditTrail;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapService {
    private final BootstrapMapper mapper;
    private final AuditTrail audit;

    public BootstrapService(BootstrapMapper mapper, AuditTrail audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public BootstrapStatus status() {
        return new BootstrapStatus("merine-rebuild", mapper.databaseName(), Instant.now(),
                mapper.count(), mapper.recent());
    }

    @Transactional
    public ProbeRecord create(String note) {
        String id = UUID.randomUUID().toString();
        mapper.insert(id, note.strip());
        ProbeRecord created = mapper.findById(id);
        audit.recordCurrent(AuditEvent.succeeded("bootstrap", "bootstrap:create-probe", "PROBE",
                created.id(), "初始化探针记录",
                "写入初始化探针记录（备注：%s）".formatted(created.note())));
        return created;
    }
}
