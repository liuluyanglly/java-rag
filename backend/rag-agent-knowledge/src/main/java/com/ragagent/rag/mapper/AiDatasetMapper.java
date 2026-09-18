package com.ragagent.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragagent.rag.entity.AiDataset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiDatasetMapper extends BaseMapper<AiDataset> {

    @Select("SELECT DISTINCT d.* FROM ai_dataset d " +
            "LEFT JOIN ai_dataset_role dr ON d.id = dr.dataset_id " +
            "LEFT JOIN sys_user_role ur ON dr.role_id = ur.role_id " +
            "WHERE d.is_public = TRUE OR ur.user_id = #{userId} OR d.created_by = #{userId}")
    List<AiDataset> selectAccessibleDatasets(@Param("userId") Long userId);
}
