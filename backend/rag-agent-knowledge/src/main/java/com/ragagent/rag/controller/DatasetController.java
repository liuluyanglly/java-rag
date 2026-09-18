package com.ragagent.rag.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragagent.common.result.Result;
import com.ragagent.common.security.SecurityUtils;
import com.ragagent.rag.entity.AiDataset;
import com.ragagent.rag.service.AiDatasetService;
import com.ragagent.rag.entity.AiDatasetRole;
import com.ragagent.rag.mapper.AiDatasetMapper;
import com.ragagent.rag.mapper.AiDatasetRoleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/dataset")
@RequiredArgsConstructor
public class DatasetController {

    private final AiDatasetService datasetService;
    private final com.ragagent.rag.service.RagSearchService ragSearchService;

    @Operation(summary = "知识库召回测试沙箱",
            description = "知识库召回测试器。输入测试 Query，实时查看本知识库的向量与关键词召回片段、相似度评分")
    @GetMapping("/{id}/hit-test")
    public Result<List<com.ragagent.rag.service.RagSearchService.SearchResultChunk>> hitTest(
            @PathVariable Long id,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            ServerWebExchange exchange) {
        Long userId = SecurityUtils.getLoginUserId(exchange);
        List<com.ragagent.rag.service.RagSearchService.SearchResultChunk> results = 
                ragSearchService.search(userId, query, List.of(id), topK);
        return Result.success(results);
    }

    @Operation(summary = "分页查询知识库列表",
            description = "管理端全量分页列表，按创建时间倒序。需要 ai:dataset:list 权限")
    @SaCheckPermission("ai:dataset:list")
    @GetMapping("/list")
    public Result<Page<AiDataset>> list(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "知识库名称，模糊匹配，为空则不过滤", example = "研发规范")
            @RequestParam(required = false) String name,
            ServerWebExchange exchange) {
        return Result.success(datasetService.pageDatasets(pageNum, pageSize, name));
    }

    @Operation(summary = "获取当前用户可访问的知识库列表",
            description = "用户端使用。按当前登录用户的角色白名单与安全密级过滤，仅返回有权检索的知识库")
    @GetMapping("/accessible")
    public Result<List<AiDataset>> getAccessibleDatasets(ServerWebExchange exchange) {
        Long userId = SecurityUtils.getLoginUserId(exchange);
        return Result.success(datasetService.listAccessibleDatasets(userId));
    }

    @Operation(summary = "新增知识库",
            description = "入参 id / createdBy / createTime / updateTime 由服务端生成，无需传入；"
                    + "authorizedRoleIds 非空时同步写入角色授权关系")
    @SaCheckPermission("ai:dataset:add")
    @PostMapping
    public Result<Void> add(@RequestBody AiDataset dataset, ServerWebExchange exchange) {
        Long userId = SecurityUtils.getLoginUserId(exchange);
        datasetService.createDataset(dataset, userId);
        return Result.success("创建成功", null);
    }

    @Operation(summary = "更新知识库",
            description = "按 id 更新。authorizedRoleIds 非 null 时先清空原授权再全量重建；传 null 表示不调整授权")
    @SaCheckPermission("ai:dataset:edit")
    @PutMapping
    public Result<Void> edit(@RequestBody AiDataset dataset, ServerWebExchange exchange) {
        datasetService.updateDataset(dataset);
        return Result.success("更新成功", null);
    }

    @Operation(summary = "删除知识库",
            description = "逻辑删除知识库并清理其角色授权关系")
    @SaCheckPermission("ai:dataset:remove")
    @DeleteMapping("/{id}")
    public Result<Void> remove(
            @Parameter(description = "知识库ID", example = "1", required = true)
            @PathVariable Long id,
            ServerWebExchange exchange) {
        datasetService.deleteDataset(id);
        return Result.success("删除成功", null);
    }
}
