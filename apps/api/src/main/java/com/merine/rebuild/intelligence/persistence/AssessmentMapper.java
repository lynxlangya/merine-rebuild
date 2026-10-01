package com.merine.rebuild.intelligence.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AssessmentMapper {
    record Row(long id, long topicId, Long receiptId, String analysis, String recommendationCode,
               long unitId, String unitName, String userName, Instant createdAt, String requestDigest) { }
    @Select("SELECT id FROM intel_assessment WHERE unit_id=#{id} FOR SHARE") List<Long> unitReferences(long id);
    @Select("SELECT id,topic_id AS topicId,receipt_id AS receiptId,analysis,recommendation_code AS recommendationCode,unit_id AS unitId,unit_name AS unitName,user_name AS userName,created_at AS createdAt,request_digest AS requestDigest FROM intel_assessment WHERE id=#{id} FOR UPDATE")
    Row locked(long id);
    @Select("SELECT id,topic_id AS topicId,receipt_id AS receiptId,analysis,recommendation_code AS recommendationCode,unit_id AS unitId,unit_name AS unitName,user_name AS userName,created_at AS createdAt,request_digest AS requestDigest FROM intel_assessment WHERE id=#{id} AND topic_id=#{topicId} AND unit_id=#{unitId}")
    Row own(@Param("id") long id, @Param("topicId") long topicId, @Param("unitId") long unitId);
    @Select("SELECT id,topic_id AS topicId,receipt_id AS receiptId,analysis,recommendation_code AS recommendationCode,unit_id AS unitId,unit_name AS unitName,user_name AS userName,created_at AS createdAt,request_digest AS requestDigest FROM intel_assessment WHERE unit_id=#{unitId} AND idempotency_key=#{key} FOR UPDATE")
    Row replay(@Param("unitId") long unitId, @Param("key") String key);
    @Select("SELECT COUNT(*) FROM intel_assessment WHERE topic_id=#{topicId} AND unit_id=#{unitId}")
    long count(@Param("topicId") long topicId, @Param("unitId") long unitId);
    @Select("SELECT id,topic_id AS topicId,receipt_id AS receiptId,analysis,recommendation_code AS recommendationCode,unit_id AS unitId,unit_name AS unitName,user_name AS userName,created_at AS createdAt,request_digest AS requestDigest FROM intel_assessment WHERE topic_id=#{topicId} AND unit_id=#{unitId} ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Row> list(@Param("topicId") long topicId,@Param("unitId") long unitId,@Param("offset") long offset,@Param("limit") int limit);
    @Insert("INSERT INTO intel_assessment(topic_id,receipt_id,analysis,recommendation_code,unit_id,user_id,unit_name,user_name,idempotency_key,request_digest,created_at) VALUES(#{topicId},#{receiptId},#{analysis},#{recommendation},#{unitId},#{userId},#{unitName},#{userName},#{key},#{digest},#{now})")
    void insert(@Param("topicId") long topicId,@Param("receiptId") Long receiptId,@Param("analysis") String analysis,
                @Param("recommendation") String recommendation,@Param("unitId") long unitId,@Param("userId") long userId,
                @Param("unitName") String unitName,@Param("userName") String userName,@Param("key") String key,
                @Param("digest") String digest,@Param("now") Instant now);
}
