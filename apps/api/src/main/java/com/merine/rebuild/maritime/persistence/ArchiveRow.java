package com.merine.rebuild.maritime.persistence;

/** 六类档案 SQL 的内部投射；HTTP 契约使用各自 DTO。 */
public class ArchiveRow {
    public Long id;
    public String name;
    public Long unitId;
    public String unitCode;
    public String unitName;
    public Integer unitLevel;
    public Long userId;
    public String loginName;
    public String userStatus;
    public String region;
    public String location;
    public String purpose;
    public String status;
    public Integer version;
    public java.time.Instant createdAt;
    public java.time.Instant updatedAt;
    public String fixtureKey;
    public Long portId;
    public Long policeStationId;
    public Long responsibleOfficerId;
    public String inhabitationType;
    public String duty;
    public String portName;
    public String portStatus;
    public String policeStationName;
    public String policeStationStatus;
    public String responsibleOfficerName;
    public String responsibleOfficerStatus;
}
