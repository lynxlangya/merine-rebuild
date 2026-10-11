package com.merine.rebuild.agent.datasource.persistence;

import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.Save;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DataSourceMapper {
    String COLUMNS = "id, name, remark, db_type AS dbType, host, port, "
            + "database_name AS databaseName, username, status, last_check_at AS lastCheckAt, "
            + "last_check_status AS lastCheckStatus, last_check_message AS lastCheckMessage, "
            + "checked_version AS checkedVersion, updated_at AS updatedAt, version";

    @Select("SELECT " + COLUMNS + " FROM ai_data_source "
            + "WHERE name LIKE CONCAT('%', #{search}, '%') ORDER BY updated_at DESC, id LIMIT #{limit} OFFSET #{offset}")
    List<DataSourceRow> list(@Param("search") String search, @Param("limit") int limit,
                             @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM ai_data_source WHERE name LIKE CONCAT('%', #{search}, '%')")
    long count(String search);

    @Select("SELECT " + COLUMNS + " FROM ai_data_source WHERE id = #{id}")
    DataSourceRow find(String id);

    /** 受控查询按视图解析来源：只有启用中的配置才算候选；多命中由调用方 fail closed。 */
    @Select("""
        SELECT d.id AS id, d.name AS name
          FROM ai_data_source d
          JOIN ai_data_source_view v ON v.data_source_id = d.id
         WHERE d.status = 'ENABLED' AND v.view_name = #{viewName}
         ORDER BY d.id
        """)
    List<DataSourceSourceRow> enabledSourcesForView(String viewName);

    /** 编辑与启停的串行锚点：先锁行再核对版本与视图，避免与并发检查互相覆盖。 */
    @Select("SELECT " + COLUMNS + " FROM ai_data_source WHERE id = #{id} FOR UPDATE")
    DataSourceRow lock(String id);

    /** 检查路径：只在服务端内部取密文与连接参数，不进入响应。 */
    @Select("""
        SELECT id, name, db_type AS dbType, host, port, database_name AS databaseName,
               username, password_cipher AS passwordCipher, status, version, updated_at AS updatedAt
          FROM ai_data_source WHERE id = #{id}
        """)
    DataSourceSecretRow findSecret(String id);

    @Insert("""
        INSERT INTO ai_data_source(id, name, remark, db_type, host, port, database_name, username,
                                   password_cipher, status)
        VALUES(#{id}, #{input.name}, #{input.remark}, #{input.dbType}, #{input.host}, #{input.port},
               #{input.databaseName}, #{input.username}, #{encrypted}, 'DISABLED')
        """)
    void insert(@Param("id") String id, @Param("input") Save input, @Param("encrypted") String encrypted);

    /**
     * 连接参数或视图范围变化：密码可留空保留原值，旧检查结论一并清除，并强制停用。
     * 条件是 id + 版本，防止并发编辑互相覆盖。
     */
    @Update("""
        UPDATE ai_data_source
           SET db_type = #{input.dbType}, host = #{input.host}, port = #{input.port},
               database_name = #{input.databaseName}, username = #{input.username},
               password_cipher = COALESCE(#{encrypted}, password_cipher),
               status = 'DISABLED', last_check_at = NULL, last_check_status = NULL,
               last_check_message = '', checked_version = NULL,
               version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
         WHERE id = #{id} AND version = #{version}
        """)
    int replaceConnection(@Param("id") String id, @Param("input") Save input,
                          @Param("encrypted") String encrypted, @Param("version") int version);

    /** 只改名称/备注：连接与视图没变，保留检查结论与启停状态。 */
    @Update("""
        UPDATE ai_data_source
           SET name = #{input.name}, remark = #{input.remark},
               version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
         WHERE id = #{id} AND version = #{version}
        """)
    int updateMetadata(@Param("id") String id, @Param("input") Save input, @Param("version") int version);

    /** 记录一次检查结论；版本在检查期间变化则更新 0 行，由调用方报冲突，不留下陈旧结果。 */
    @Update("""
        UPDATE ai_data_source
           SET last_check_at = #{checkedAt}, last_check_status = #{status},
               last_check_message = #{message}, checked_version = #{version}
         WHERE id = #{id} AND version = #{version}
        """)
    int recordCheck(@Param("id") String id, @Param("version") int version,
                    @Param("status") DataSourceCheckOutcome status, @Param("message") String message,
                    @Param("checkedAt") Instant checkedAt);

    /** 启用：同一次更新写入刚完成的检查结论，旧检查结果不会被复用。 */
    @Update("""
        UPDATE ai_data_source
           SET status = 'ENABLED', last_check_at = #{checkedAt}, last_check_status = 'PASSED',
               last_check_message = #{message}, checked_version = #{version} + 1,
               version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
         WHERE id = #{id} AND version = #{version} AND status = 'DISABLED'
        """)
    int enableChecked(@Param("id") String id, @Param("version") int version,
                      @Param("message") String message, @Param("checkedAt") Instant checkedAt);

    @Update("""
        UPDATE ai_data_source
           SET status = 'DISABLED', version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
         WHERE id = #{id} AND version = #{version} AND status = 'ENABLED'
        """)
    int disable(@Param("id") String id, @Param("version") int version);

    @Select("""
        SELECT id, data_source_id AS dataSourceId, view_name AS viewName
          FROM ai_data_source_view WHERE data_source_id = #{dataSourceId} ORDER BY view_name
        """)
    List<DataSourceViewRow> viewsOf(String dataSourceId);

    /** 列表页批量补装视图，避免逐条数据源查询；动态 IN 放 XML。 */
    List<DataSourceViewRow> viewsOfMany(@Param("ids") List<String> ids);

    @Delete("DELETE FROM ai_data_source_view WHERE data_source_id = #{dataSourceId}")
    int deleteViews(String dataSourceId);

    /** 整体替换前批量插入；foreach 放 XML。 */
    void insertViews(@Param("dataSourceId") String dataSourceId, @Param("views") List<String> views);
}
