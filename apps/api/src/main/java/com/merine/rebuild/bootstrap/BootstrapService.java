package com.merine.rebuild.bootstrap;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapService {
    private final BootstrapMapper mapper;

    public BootstrapService(BootstrapMapper mapper) {
        this.mapper = mapper;
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
        return mapper.findById(id);
    }
}
