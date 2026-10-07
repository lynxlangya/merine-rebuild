package com.merine.rebuild.maritime.persistence;

public record ArchiveQuery(String keyword, String region, String status, Long portId,
                           Long policeStationId, Long responsibleOfficerId,
                           String inhabitationType, int page, int pageSize) {
    public long offset() { return (long) (page - 1) * pageSize; }
}
