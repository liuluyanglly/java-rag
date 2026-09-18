package com.ragagent.rag.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ragagent.rag.entity.AiDataset;

import java.util.List;

/**
 * 知识库数据集业务服务接口
 */
public interface AiDatasetService extends IService<AiDataset> {

    Page<AiDataset> pageDatasets(Integer pageNum, Integer pageSize, String name);

    List<AiDataset> listAccessibleDatasets(Long userId);

    void createDataset(AiDataset dataset, Long userId);

    void updateDataset(AiDataset dataset);

    void deleteDataset(Long id);
}
