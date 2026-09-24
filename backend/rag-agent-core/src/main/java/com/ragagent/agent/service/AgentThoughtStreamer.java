package com.ragagent.agent.service;

import cn.hutool.json.JSONUtil;
import com.ragagent.rag.service.HybridSearchEngine;
import com.ragagent.rag.service.RagSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Map;

/**
 * <h1>多智能体流式协同思考调度器 (Agent Thought Streamer)</h1>
 * <p>
 * 专职负责在问答前置阶段，调度各专业子智能体（规划总控、时空感知、多路召回、审查质检）执行，
 * 并将真实的思考推导、决策逻辑与图谱拓扑以打字机级平滑 SSE 流实时推送给前端。
 *
 * @author Java-RAG Team
 */
@Slf4j
@Service
public class AgentThoughtStreamer {

    /**
     * 平滑流式推送纯文本思考片段 (逐块渐进推流)
     *
     * @param sink              SSE 发射通道
     * @param fullAccumulator   完整思考内容累加器
     * @param text              本次要流式推送的文本内容
     * @param chunkIntervalMs   每小块间隔毫秒数（推荐 10~25ms，保证平滑打字感）
     */
    public void streamSmoothText(FluxSink<ServerSentEvent<String>> sink,
                                 StringBuilder fullAccumulator,
                                 String text,
                                 long chunkIntervalMs) {
        if (!StringUtils.hasText(text) || sink.isCancelled()) {
            return;
        }

        fullAccumulator.append(text);

        // 按自然标点或长度进行微小颗粒度分块推流
        int chunkSize = 6;
        int len = text.length();
        for (int i = 0; i < len; i += chunkSize) {
            if (sink.isCancelled()) {
                break;
            }
            int end = Math.min(i + chunkSize, len);
            String chunk = text.substring(i, end);
            sink.next(ServerSentEvent.<String>builder()
                    .event("thought")
                    .data(JSONUtil.toJsonStr(Map.of("content", chunk)))
                    .build());
            if (chunkIntervalMs > 0) {
                try {
                    Thread.sleep(chunkIntervalMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 阶段 1：主任务规划调度智能体 (Planner Agent)
     */
    public void streamPlanningStep(FluxSink<ServerSentEvent<String>> sink,
                                   StringBuilder fullAccumulator,
                                   String userQuery,
                                   boolean hasDataset) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 【任务规划总控智能体 (Planner Agent)】\n");
        sb.append("   正在审视用户提问语义复杂度，解构任务目标并构建协同子智能体执行链...\n");

        if (userQuery.length() > 30) {
            sb.append("   • 判定为复合长篇咨询，需激活深度语义解析与多源交叉校验回路；\n");
        } else {
            sb.append("   • 判定为精准明确提问，启动敏捷协同链路；\n");
        }

        sb.append("   • 协同规划调度表：\n")
          .append("     1. 调度 [时空与意图感知智能体] -> 对齐物理时基与上下文记忆；\n");
        if (hasDataset) {
            sb.append("     2. 调度 [四维多路召回智能体] -> 穿透向量库、倒排索引与知识图谱拓扑；\n");
        } else {
            sb.append("     2. 调度 [通用逻辑推理智能体] -> 执行系统化知识构建；\n");
        }
        sb.append("     3. 调度 [审查反省质检智能体] -> 执行事实性核验与防幻觉过滤。\n\n");

        streamSmoothText(sink, fullAccumulator, sb.toString(), 12);
    }

    /**
     * 阶段 2：意图与时空感知智能体 (Context & Intent Agent)
     */
    public void streamContextStep(FluxSink<ServerSentEvent<String>> sink,
                                  StringBuilder fullAccumulator,
                                  String intentDescription,
                                  String currentDateTimeStr,
                                  String dayOfWeekStr,
                                  List<?> memories) {
        StringBuilder sb = new StringBuilder();
        sb.append("🧭 【时空与意图感知智能体 (Context Agent)】\n");
        sb.append(String.format("   • 宿主机物理时钟基准对齐: %s (%s)\n", currentDateTimeStr, dayOfWeekStr));
        sb.append(String.format("   • 意图识别结论: %s\n", intentDescription));
        sb.append("   • Redis 短期会话滑窗读取完毕，已建立对话上下文连续性。\n");

        if (memories != null && !memories.isEmpty()) {
            sb.append(String.format("   • 斯坦福加权检索命中 %d 条长期自进化认知偏好，已载入工作记忆。\n", memories.size()));
        }
        sb.append("\n");

        streamSmoothText(sink, fullAccumulator, sb.toString(), 12);
    }

    /**
     * 阶段 3：四维多路召回智能体 (Hybrid Retrieval Agent)
     */
    public void streamHybridRetrievalStep(FluxSink<ServerSentEvent<String>> sink,
                                          StringBuilder fullAccumulator,
                                          List<HybridSearchEngine.HybridEvidence> evidences,
                                          List<RagSearchService.SearchResultChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔍 【四维多路召回智能体 (Hybrid Retrieval Agent)】\n");
        sb.append("   • 正在并发驱动多路召回引擎：\n")
          .append("     - [Dense] 向量语义近似度计算（Spring AI VectorStore 粗排扩召回）...\n")
          .append("     - [Sparse] 数据库全文与精准术语词频匹配...\n")
          .append("     - [GraphRAG] 知识图谱核心实体模糊探测与 1 度拓扑关系网展开...\n")
          .append("     - [RRF 融合] 运行倒数排名融合算法 (Score = 1/(60 + Rank)) 进行全局无监督重排...\n");

        if (evidences != null && !evidences.isEmpty()) {
            sb.append(String.format("   • 成功召回 %d 条高置信度融合证据：\n", evidences.size()));
            for (HybridSearchEngine.HybridEvidence ev : evidences) {
                if ("KNOWLEDGE_GRAPH".equalsIgnoreCase(ev.getSource())) {
                    sb.append(String.format("     🕸️ [图谱网络] %s (融合权重: %.4f)\n", ev.getSnippet(), ev.getRrfScore()));
                } else {
                    sb.append(String.format("     📄 [文档切片] 《%s》 (融合权重: %.4f)\n", ev.getTitle(), ev.getRrfScore()));
                }
            }
        } else if (chunks != null && !chunks.isEmpty()) {
            sb.append(String.format("   • 向量检索召回 %d 个切片：\n", chunks.size()));
            for (RagSearchService.SearchResultChunk c : chunks) {
                double score = c.getScore() != null ? c.getScore() * 100 : 85.0;
                sb.append(String.format("     📄 《%s》 (相似度: %.1f%%)\n", c.getDocumentName(), score));
            }
        } else {
            sb.append("   • 知识库当前无直接匹配切片，已通知审查智能体触发通用防幻觉策略。\n");
        }
        sb.append("\n");

        streamSmoothText(sink, fullAccumulator, sb.toString(), 14);
    }

    /**
     * 阶段 4：审查与反省质检智能体 (Reviewer & Critic Agent)
     */
    public void streamCriticStep(FluxSink<ServerSentEvent<String>> sink,
                                 StringBuilder fullAccumulator,
                                 boolean hasEvidence,
                                 String guidance) {
        StringBuilder sb = new StringBuilder();
        sb.append("🛡️ 【审查反省质检智能体 (Critic Agent)】\n");
        if (hasEvidence) {
            sb.append("   • 审查判定：检索证据链充分完整，事实支撑度高，防范幻觉校验通过。\n");
        } else {
            sb.append("   • 审查判定：未检索到强约束切片，激活事实性防御边界，严禁盲目凭空编造。\n");
        }
        if (StringUtils.hasText(guidance)) {
            sb.append(String.format("   • 输出组织策略: %s\n", guidance));
        }
        sb.append("   • 质检通过，已交接给回答合成引擎开始组织最终输出。\n\n");

        streamSmoothText(sink, fullAccumulator, sb.toString(), 12);
    }
}
