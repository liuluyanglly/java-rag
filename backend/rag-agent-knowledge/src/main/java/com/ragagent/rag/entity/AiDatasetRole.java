package com.ragagent.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_dataset_role")
public class AiDatasetRole implements Serializable {
    private Long datasetId;
    private Long roleId;
    private String permissionType; // READ / WRITE
}
