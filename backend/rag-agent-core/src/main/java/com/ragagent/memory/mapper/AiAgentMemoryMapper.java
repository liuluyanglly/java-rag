package com.ragagent.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragagent.memory.entity.AiAgentMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiAgentMemoryMapper extends BaseMapper<AiAgentMemory> {

    /**
     * 更新指定记忆的 embedding 向量
     */
    @Update("UPDATE ai_agent_memory SET embedding = CAST(#{vectorStr} AS vector), update_time = NOW() WHERE id = #{id}")
    int updateEmbedding(@Param("id") String id, @Param("vectorStr") String vectorStr);

    /**
     * 基于 pgvector 语义相似度 + 记忆重要性 + 时间遗忘衰减(Stanford 算法) 进行混合加权召回
     */
    @Select("""
        SELECT id, user_id, agent_id, session_id, memory_type, content, importance, access_count, metadata,
               create_time, update_time, last_accessed_time,
               (
                   (1 - (embedding <=> CAST(#{vectorStr} AS vector))) * 0.6 +
                   (importance / 10.0) * 0.25 +
                   (EXP(-0.05 * (EXTRACT(EPOCH FROM (NOW() - last_accessed_time)) / 86400.0))) * 0.15
               ) AS score
        FROM ai_agent_memory
        WHERE user_id = #{userId}
          AND embedding IS NOT NULL
        ORDER BY score DESC
        LIMIT #{limit}
    """)
    List<AiAgentMemory> searchHybridMemories(@Param("userId") Long userId,
                                             @Param("vectorStr") String vectorStr,
                                             @Param("limit") int limit);

    /**
     * 强化记忆：递增访问频次并刷新最近访问时间
     */
    @Update("UPDATE ai_agent_memory SET access_count = access_count + 1, last_accessed_time = NOW() WHERE id = #{id}")
    int reinforceMemory(@Param("id") String id);
}