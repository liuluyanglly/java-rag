package com.ragagent.graph.nodes;

import com.ragagent.graph.GraphNode;
import com.ragagent.graph.GraphState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class PlanNode implements GraphNode {

    @Override
    public GraphState execute(GraphState state) {
        log.info("[PlanNode] 开始深度研究规划: topic={}", state.getTopic());
        state.addThought("📋 正在对深度研究课题进行系统拆解，制定多维多阶段调研计划...");

        // 规划拆解为 3 个递进式的深度探究步骤
        List<String> steps = List.of(
                "第一阶段：深入调研【" + state.getTopic() + "】的核心机理、理论定义与基础特性",
                "第二阶段：系统梳理关键工艺/方案路径与行业最佳实践，对比关键性能与指标参数",
                "第三阶段：深入剖析落地关键瓶颈、产业化制约因素及演进实施路线图"
        );
        state.setPlanSteps(steps);
        state.put("planCount", steps.size());

        state.addThought("✅ 已完成课题规划，生成 " + steps.size() + " 个调研大纲分支，准备启动多路并行检索。");
        return state;
    }
}
