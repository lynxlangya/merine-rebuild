package com.merine.rebuild.intelligence.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** intelligence 业务写入口；sys_user/sys_unit 仅作当前身份与组织投影读取。 */
@Mapper
public interface IntelligenceMapper {
    record UnitRow(long id, String code, String name, Long parentId, int level, String status) { }
    record ActorRow(long userId, long unitId, String userName, long authorizationVersion) { }
    record TopicRow(long id, String topicNo, long sourceUnitId, long sourceUserId,
                    String sourceUnitName, String sourceUserName, String title, String body,
                    String status, int version, String draftNote, Instant createdAt, Instant publishedAt) { }
    record ListRow(long id, String topicNo, String title, String status, String sourceUnitName,
                   Instant createdAt, Instant publishedAt, long pendingReceiptCount, long myReceiptCount) { }
    record ReceiptRow(long id, long sendId, long topicId, Long parentReceiptId,
                      long fromUnitId, String fromUnitName, String senderName, String note, Instant sentAt,
                      long toUnitId, String toUnitName, String signedByName, Instant signedAt) { }
    record ScopeRow(long unitId, String unitCode) { }
    record FeedbackRow(long id, long receiptId, String body, String unitName, String userName, Instant createdAt) { }
    record SupplementRow(long id, String kind, String body, String unitName, String userName, Instant createdAt) { }
    record CommandRow(String requestDigest, Long topicId) { }

    ActorRow actor(@Param("userId") long userId);
    List<UnitRow> units();
    List<UnitRow> lockUnits();
    TopicRow topic(@Param("id") long id);
    TopicRow lockTopic(@Param("id") long id);
    int received(@Param("topicId") long topicId, @Param("unitId") long unitId);
    long count(@Param("unitId") long unitId, @Param("view") String view, @Param("status") String status, @Param("keyword") String keyword);
    List<ListRow> list(@Param("unitId") long unitId, @Param("view") String view, @Param("status") String status,
                      @Param("keyword") String keyword, @Param("offset") long offset, @Param("limit") int limit);
    List<ScopeRow> scope(@Param("topicId") long topicId);
    List<ScopeRow> draftTargets(@Param("topicId") long topicId);
    List<ReceiptRow> receipts(@Param("topicId") long topicId);
    List<FeedbackRow> feedbacks(@Param("topicId") long topicId);
    List<SupplementRow> supplements(@Param("topicId") long topicId);
    long lastId();
    void insertTopic(@Param("number") String number, @Param("actor") ActorRow actor, @Param("unit") UnitRow unit,
                     @Param("title") String title, @Param("body") String body, @Param("note") String note, @Param("now") Instant now);
    int updateDraft(@Param("id") long id, @Param("title") String title, @Param("body") String body,
                    @Param("note") String note, @Param("version") int version);
    void clearScope(@Param("id") long id);
    void clearTargets(@Param("id") long id);
    void insertScope(@Param("id") long id, @Param("unit") UnitRow unit);
    void insertDraftTarget(@Param("id") long id, @Param("unit") UnitRow unit);
    void freezeScopeNames(@Param("id") long id);
    void publish(@Param("id") long id, @Param("now") Instant now);
    void insertSend(@Param("id") long id, @Param("parent") Long parent, @Param("actor") ActorRow actor,
                    @Param("unit") UnitRow unit, @Param("note") String note, @Param("now") Instant now);
    void insertReceipt(@Param("sendId") long sendId, @Param("unit") UnitRow unit);
    int sign(@Param("id") long id, @Param("actor") ActorRow actor, @Param("now") Instant now);
    void insertFeedback(@Param("id") long id, @Param("actor") ActorRow actor, @Param("unit") UnitRow unit,
                        @Param("body") String body, @Param("now") Instant now);
    void insertSupplement(@Param("id") long id, @Param("kind") String kind, @Param("actor") ActorRow actor,
                          @Param("unit") UnitRow unit, @Param("body") String body, @Param("now") Instant now);
    void reserveNumber(@Param("day") LocalDate day);
    int nextNumber(@Param("day") LocalDate day);
    void incrementNumber(@Param("day") LocalDate day);
    int reserveCommand(@Param("unitId") long unitId, @Param("action") String action, @Param("key") String key,
                       @Param("digest") String digest, @Param("now") Instant now);
    CommandRow command(@Param("unitId") long unitId, @Param("action") String action, @Param("key") String key);
    void completeCommand(@Param("unitId") long unitId, @Param("action") String action, @Param("key") String key, @Param("id") long id);
    List<Long> scopeReferences(@Param("id") long id);
    List<Long> targetReferences(@Param("id") long id);
    List<Long> unitReferences(@Param("id") long id);
}
