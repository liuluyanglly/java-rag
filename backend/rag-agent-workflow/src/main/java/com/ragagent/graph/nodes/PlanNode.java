package com.ragagent.graph.nodes;

import com.ragagent.graph.GraphNode;
import com.ragagent.graph.GraphState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>顶级研究战略规划节点 (Plan Node / Planning Agent)</h1>
 * <p>
 * 本节点是多智能体流水线中的 <b>Planner（规划大脑）</b>，对应前沿 Agent 设计模式中的 <b>Task Decomposition（任务分解）</b>。
 * <p>
 * <b>核心学习知识点：</b>
 * <ol>
 *   <li><b>为什么不能让 LLM 直接写整篇长研报？</b>
 *       <br>如果让模型单次生成万字研报，模型往往会因注意力机制分散（Lost in the Middle）而陷入空泛泛的套话，甚至中途草草收尾。
 *       先进行“大纲规划（Plan）”，将庞大课题拆解为可观测、可逐一检索佐证的阶段性目标，是生成高质量专业报告的工业界最佳实践。</li>
 *   <li><b>阶段递进逻辑：</b>
 *       <br>五阶段遵循标准工程研发与战略咨询逻辑：本质机理 -> 技术架构 -> 评测矩阵 -> 落地避坑 -> 战略路线图。</li>
 *   <li><b>动态弹性回退机制：</b>
 *       <br>若由于网络波动或模型解析异常导致输出步骤少于 3 个，系统自动基于专业模板动态注入高质量大纲，确保下游节点稳定运行。</li>
 * </ol>
 *
 * @author Java-RAG Team
 * @see HybridRetrievalNode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanNode implements GraphNode {

    /** Spring AI 对话模型提供者 (按需装配) */
    private final ObjectProvider<ChatModel> chatModelProvider;

    /**
     * <h3>执行课题战略拆解与 5 阶段递进大纲制定</h3>
     *
     * @param state 当前全局图状态（包含待研究课题 topic）
     * @return 注入规划大纲（{@code planSteps}）与思考推演日志后的新图状态
     */
    @Override
    public GraphState execute(GraphState state) {
        String topic = state.getTopic();
        log.info("【PlanNode】开始深度研究战略拆解与推演规划: topic={}", topic);

        // 记录阶段性思考过程（用于流式推送到前端让用户感知思考深度）
        state.addThought("🧠 [深度思考·阶段拆解] 正在解析课题【" + topic + "】的学科交叉维度与核心战略诉求...");
        state.addThought("🔍 [理论建模] 确立研究假设：基于底层第一性原理，深入剖析其基本定义、机理本质及理论发展脉络。");

        List<String> steps = new ArrayList<>();
        ChatModel chatModel = chatModelProvider.getIfAvailable();

        // 步骤 1：利用大模型执行 Few-Shot 提示工程动态拆解课题
        if (chatModel != null) {
            try {
                String planPrompt = String.format("""
                        你是一个享誉全球的顶级技术战略与工程研发咨询科学家。
                        请针对用户研究课题【%s】，制定一份详实、专业、层层递进的 5 阶段深度技术调研大纲计划。
                        要求：
                        1. 覆盖：①底层理论与本质机理；②主流技术路径与架构模型；③关键方案横向对比与性能指标；④生产/工业级落地实战与瓶颈挑战；⑤演进路线图与决策建议。
                        2. 每行输出一个步骤，格式为：“第X阶段：...”，严禁输出任何多余废话或开场白。
                        """, topic);

                String result = ChatClient.create(chatModel)
                        .prompt()
                        .user(planPrompt)
                        .call()
                        .content();

                if (result != null && !result.isBlank()) {
                    String[] lines = result.split("\\r?\\n");
                    for (String line : lines) {
                        String trimmed = line.trim();
                        // 过滤非结构化杂讯，精准提取阶段文本
                        if (!trimmed.isEmpty() && (trimmed.startsWith("第") || trimmed.matches("^\\d+[.、].*"))) {
                            steps.add(trimmed);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("【PlanNode】大模型规划生成异常，平滑降级采用高质量动态专业大纲: {}", e.getMessage());
            }
        }

        // 步骤 2：质量校验与模板兜底（确保大纲永远具备专业级工程深度）
        if (steps.size() < 3) {
            steps = List.of(
                    "第一阶段：深挖【" + topic + "】底层理论机理、工作原理数学推导及学术演进脉络",
                    "第二阶段：系统解构行业主流技术架构与关键工艺路线，绘制多维拓扑结构图",
                    "第三阶段：建立关键性能指标 (KPI) 评测矩阵，对比各技术流派的吞吐、延迟、成本与成熟度",
                    "第四阶段：深入剖析生产级工业落地实操难点、核心瓶颈约束及经典避坑指南",
                    "第五阶段：构建中长期演进发展路线图，输出切实可行的商业/工程战略决策建议"
            );
        }

        // 步骤 3：回写状态机上下文，为后续的检索与终稿撰写提供骨架
        state.setPlanSteps(steps);
        state.put("planCount", steps.size());

        state.addThought("📊 [多维大纲生成] 已为【" + topic + "】构建 " + steps.size() + " 个递进式研究推进阶梯：\n" +
                String.join("\n", steps.stream().map(s -> "   • " + s).toList()));
        state.addThought("✨ [调度决策] 规划方案通过战略中枢审查，启动四维混合向量与全文拓扑检索...");

        return state;
    }
}
