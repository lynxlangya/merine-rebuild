package com.merine.rebuild.bootstrap;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BootstrapMapper {
    @Select("SELECT DATABASE()")
    String databaseName();

    @Select("SELECT COUNT(*) FROM bootstrap_probe")
    long count();

    @Select("SELECT id, note, created_at AS createdAt FROM bootstrap_probe ORDER BY created_at DESC, id DESC LIMIT 5")
    List<ProbeRecord> recent();

    @Insert("INSERT INTO bootstrap_probe (id, note) VALUES (#{id}, #{note})")
    void insert(@Param("id") String id, @Param("note") String note);

    @Select("SELECT id, note, created_at AS createdAt FROM bootstrap_probe WHERE id = #{id}")
    ProbeRecord findById(@Param("id") String id);
}
