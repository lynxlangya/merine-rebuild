package com.merine.rebuild.task.persistence;

import com.merine.rebuild.task.dto.TaskIntelligenceViews.*;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TaskIntelligenceMapper {
    record SourceRow(long id,long taskId,long topicId,long assessmentId,Long lastSupplementId,String backgroundSummary) { }
    @Select("SELECT s.id,s.task_id AS taskId,s.topic_id AS topicId,s.assessment_id AS assessmentId,s.last_supplement_id AS lastSupplementId,o.background_summary AS backgroundSummary FROM task_intel_source s JOIN task_order o ON o.id=s.task_id WHERE s.task_id=#{taskId}")
    SourceRow source(long taskId);
    @Insert("INSERT INTO task_intel_source(task_id,topic_id,assessment_id,last_supplement_id,created_unit_id,created_user_id,created_unit_name,created_user_name,created_at) VALUES(#{taskId},#{topicId},#{assessmentId},#{checkpoint},#{unitId},#{userId},#{unitName},#{userName},#{now})")
    void sourceInsert(@Param("taskId") long taskId,@Param("topicId") long topicId,@Param("assessmentId") long assessmentId,
        @Param("checkpoint") Long checkpoint,@Param("unitId") long unitId,@Param("userId") long userId,
        @Param("unitName") String unitName,@Param("userName") String userName,@Param("now") Instant now);
    @Update("UPDATE task_order SET background_summary=#{summary} WHERE id=#{taskId} AND background_summary IS NULL")
    int background(@Param("taskId") long taskId,@Param("summary") String summary);
    long count(@Param("topicId") long topicId,@Param("unitId") long unitId);
    List<LinkedIntelligenceTask> list(@Param("topicId") long topicId,@Param("unitId") long unitId,@Param("offset") long offset,@Param("limit") int limit);
    @Select("SELECT id FROM task_intel_source WHERE created_unit_id=#{id} FOR SHARE") List<Long> sourceUnits(long id);
    @Select("SELECT id FROM task_intel_return WHERE unit_id=#{id} FOR SHARE") List<Long> returnUnits(long id);
}
