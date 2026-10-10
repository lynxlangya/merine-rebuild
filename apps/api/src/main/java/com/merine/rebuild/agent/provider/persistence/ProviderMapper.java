package com.merine.rebuild.agent.provider.persistence;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.Save;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.State;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProviderMapper {
    String COLUMNS = "id, vendor, name, remark, website, base_url AS baseUrl, status, version, updated_at AS updatedAt";
    @Select("SELECT " + COLUMNS + " FROM ai_model_provider WHERE name LIKE CONCAT('%', #{search}, '%') ORDER BY updated_at DESC, id LIMIT #{limit} OFFSET #{offset}")
    List<ProviderRow> list(@Param("search") String search, @Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM ai_model_provider WHERE name LIKE CONCAT('%', #{search}, '%')")
    long count(String search);
    @Select("SELECT " + COLUMNS + " FROM ai_model_provider WHERE id = #{id}")
    ProviderRow find(String id);
    @Select("SELECT " + COLUMNS + " FROM ai_model_provider WHERE id = #{id} FOR UPDATE")
    ProviderRow lock(String id);
    @Insert("""
        INSERT INTO ai_model_provider(id,vendor,name,remark,website,base_url,api_key_cipher,status)
        VALUES(#{id},#{input.vendor},#{input.name},#{input.remark},#{input.website},#{input.baseUrl},#{encrypted},#{input.status})
        """)
    void insert(@Param("id") String id, @Param("input") Save input, @Param("encrypted") String encrypted);
    @Update("""
        UPDATE ai_model_provider SET vendor=#{input.vendor},name=#{input.name},
        remark=#{input.remark},website=#{input.website},base_url=#{input.baseUrl},
        api_key_cipher=COALESCE(#{encrypted},api_key_cipher),status=#{input.status},
        version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND version=#{input.version}
        """)
    int update(@Param("id") String id, @Param("input") Save input, @Param("encrypted") String encrypted);
    @Delete("DELETE FROM ai_model_provider WHERE id=#{id} AND version=#{version}")
    int delete(@Param("id") String id, @Param("version") int version);

    /** 单个连接下的模型，按展示顺序。 */
    @Select("""
        SELECT id, provider_id AS providerId, model_id AS modelId, display_name AS displayName,
               remark, reasoning_efforts AS reasoningEfforts, status, sort_order AS sortOrder
          FROM ai_model_provider_model WHERE provider_id = #{providerId} ORDER BY sort_order, id
        """)
    List<ProviderModelRow> modelsOfProvider(String providerId);

    /** 使用侧模型选项：只返回启用连接下的启用模型，动态 IN + 关联查询见 XML。 */
    List<ProviderModelOptionRow> modelOptions();

    /** 出站调用所需：取连接基础地址、密文与状态。 */
    @Select("""
        SELECT id, vendor, name, base_url AS baseUrl, api_key_cipher AS apiKeyCipher, status
          FROM ai_model_provider WHERE id = #{id}
        """)
    ProviderSecretRow findSecret(String id);

    /** 按连接 + 模型标识取一条模型配置；唯一键保证最多一条。 */
    @Select("""
        SELECT id, provider_id AS providerId, model_id AS modelId, display_name AS displayName,
               remark, reasoning_efforts AS reasoningEfforts, status, sort_order AS sortOrder
          FROM ai_model_provider_model WHERE provider_id = #{providerId} AND model_id = #{modelId}
        """)
    ProviderModelRow findModel(@Param("providerId") String providerId, @Param("modelId") String modelId);

    /** 列表页批量补装模型，避免逐条连接查询。 */
    List<ProviderModelRow> modelsOf(@Param("providerIds") List<String> providerIds);

    @Insert("""
        INSERT INTO ai_model_provider_model(id,provider_id,model_id,display_name,remark,reasoning_efforts,status,sort_order)
        VALUES(#{id},#{providerId},#{modelId},#{displayName},#{remark},#{reasoningEfforts},#{status},#{sortOrder})
        """)
    void insertModel(@Param("id") String id, @Param("providerId") String providerId,
                     @Param("modelId") String modelId, @Param("displayName") String displayName,
                     @Param("remark") String remark, @Param("reasoningEfforts") String reasoningEfforts,
                     @Param("status") State status, @Param("sortOrder") int sortOrder);

    @Update("""
        UPDATE ai_model_provider_model SET display_name=#{displayName},remark=#{remark},
        reasoning_efforts=#{reasoningEfforts},status=#{status},sort_order=#{sortOrder},
        version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=#{id}
        """)
    int updateModel(@Param("id") String id, @Param("displayName") String displayName,
                    @Param("remark") String remark, @Param("reasoningEfforts") String reasoningEfforts,
                    @Param("status") State status, @Param("sortOrder") int sortOrder);

    @Delete("DELETE FROM ai_model_provider_model WHERE id=#{id}")
    int deleteModel(String id);

    @Delete("DELETE FROM ai_model_provider_model WHERE provider_id=#{providerId}")
    int deleteModelsOfProvider(String providerId);
}
