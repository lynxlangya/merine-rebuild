package com.merine.rebuild.agent.chat.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 会话、消息与执行记录的持久化入口。
 *
 * 引用完整性由应用层保证：写入前先校验会话归属（`findConversation` + `userId` 比对），
 * 删除会话时在同事务内先删消息与执行记录。查询一律带 `user_id`，避免越权读。
 */
@Mapper
public interface ChatHistoryMapper {

    // ---------- 会话 ----------

    void insertConversation(@Param("id") String id, @Param("userId") long userId,
                            @Param("title") String title, @Param("providerId") String providerId,
                            @Param("providerName") String providerName, @Param("modelId") String modelId,
                            @Param("reasoningEffort") String reasoningEffort);

    ConversationRow findConversation(@Param("id") String id);

    /**
     * 锁定会话行（SELECT ... FOR UPDATE）。
     * 写一轮与删会话都先取这把锁：一致性读会停在取锁前的快照，所以必须先锁再用锁定读校验。
     */
    ConversationRow lockConversation(@Param("id") String id);

    List<ConversationRow> listConversations(@Param("userId") long userId,
                                            @Param("limit") int limit, @Param("offset") long offset);

    long countConversations(@Param("userId") long userId);

    /** 一轮结束后刷新最近使用的目标与消息条数；`messageCount` 用增量累加，避免并发覆盖。 */
    int touchConversation(@Param("id") String id, @Param("providerId") String providerId,
                          @Param("providerName") String providerName, @Param("modelId") String modelId,
                          @Param("reasoningEffort") String reasoningEffort,
                          @Param("addedMessages") int addedMessages);

    int renameConversation(@Param("id") String id, @Param("userId") long userId,
                           @Param("title") String title, @Param("version") int version);

    int deleteConversation(@Param("id") String id, @Param("userId") long userId,
                           @Param("version") int version);

    // ---------- 消息 ----------

    int nextSeq(@Param("conversationId") String conversationId);

    void insertMessage(@Param("id") String id, @Param("conversationId") String conversationId,
                       @Param("runId") String runId, @Param("seq") int seq,
                       @Param("role") String role, @Param("text") String text,
                       @Param("status") String status);

    List<MessageRow> listMessages(@Param("conversationId") String conversationId);

    void deleteMessagesOfConversation(@Param("conversationId") String conversationId);

    // ---------- 执行记录 ----------

    int insertRun(ChatRunRow row);

    /** 执行记录列表；`state`/`providerId`/`modelId` 为空表示不过滤，`since` 为空表示不限时间。 */
    List<ChatRunRow> listRuns(@Param("userId") long userId, @Param("state") String state,
                              @Param("providerId") String providerId, @Param("modelId") String modelId,
                              @Param("since") Instant since, @Param("limit") int limit,
                              @Param("offset") long offset);

    long countRuns(@Param("userId") long userId, @Param("state") String state,
                   @Param("providerId") String providerId, @Param("modelId") String modelId,
                   @Param("since") Instant since);

    void deleteRunsOfConversation(@Param("conversationId") String conversationId);

    // ---------- 用量聚合 ----------

    /** 合计；`since`/`offset`/`providerId`/`modelId` 都由服务层收敛后传入。 */
    ChatRunStatsRow totals(@Param("userId") long userId, @Param("since") Instant since,
                           @Param("offset") String offset, @Param("providerId") String providerId,
                           @Param("modelId") String modelId);

    List<ChatRunDailyRow> daily(@Param("userId") long userId, @Param("since") Instant since,
                                @Param("offset") String offset, @Param("providerId") String providerId,
                                @Param("modelId") String modelId);

    List<ChatRunModelRow> byModel(@Param("userId") long userId, @Param("since") Instant since,
                                  @Param("providerId") String providerId,
                                  @Param("modelId") String modelId, @Param("limit") int limit);

    record ChatRunStatsRow(int runs, int succeeded, int failed, int aborted, int runsWithoutUsage,
                           long promptTokens, long completionTokens, long inputChars,
                           long outputChars, long durationMs) {}

    record ChatRunDailyRow(String date, int runs, int failed, long promptTokens,
                           long completionTokens, long inputChars, long outputChars, long durationMs) {}

    record ChatRunModelRow(String providerId, String providerName, String modelId, int runs,
                           int failed, long promptTokens, long completionTokens, long durationMs) {}

    record MessageRow(String id, String role, String text, String status, Instant createdAt) {}
}
