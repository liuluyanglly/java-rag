package com.ragagent.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ragagent.rag.entity.AiDataset;
import com.ragagent.rag.entity.AiDatasetRole;
import com.ragagent.rag.mapper.AiDatasetMapper;
import com.ragagent.rag.mapper.AiDatasetRoleMapper;
import com.ragagent.rag.service.AiDatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiDatasetServiceImpl extends ServiceImpl<AiDatasetMapper, AiDataset> implements AiDatasetService {

    private final AiDatasetRoleMapper datasetRoleMapper;

    @Override
    public Page<AiDataset> pageDatasets(Integer pageNum, Integer pageSize, String name) {
        Page<AiDataset> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiDataset> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(name), AiDataset::getName, name)
                .orderByDesc(AiDataset::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public List<AiDataset> listAccessibleDatasets(Long userId) {
        return baseMapper.selectAccessibleDatasets(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createDataset(AiDataset dataset, Long userId) {
        dataset.setCreatedBy(userId);
        dataset.setCreateTime(LocalDateTime.now());
        dataset.setUpdateTime(LocalDateTime.now());
        save(dataset);

        if (dataset.getAuthorizedRoleIds() != null && !dataset.getAuthorizedRoleIds().isEmpty()) {
            for (Long roleId : dataset.getAuthorizedRoleIds()) {
                datasetRoleMapper.insert(AiDatasetRole.builder()
                        .datasetId(dataset.getId())
                        .roleId(roleId)
                        .permissionType("READ")
                        .build());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDataset(AiDataset dataset) {
        dataset.setUpdateTime(LocalDateTime.now());
        updateById(dataset);

        if (dataset.getAuthorizedRoleIds() != null) {
            datasetRoleMapper.delete(new LambdaQueryWrapper<AiDatasetRole>().eq(AiDatasetRole::getDatasetId, dataset.getId()));
            for (Long roleId : dataset.getAuthorizedRoleIds()) {
                datasetRoleMapper.insert(AiDatasetRole.builder()
                        .datasetId(dataset.getId())
                        .roleId(roleId)
                        .permissionType("READ")
                        .build());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataset(Long id) {
        removeById(id);
        datasetRoleMapper.delete(new LambdaQueryWrapper<AiDatasetRole>().eq(AiDatasetRole::getDatasetId, id));
    }
}
