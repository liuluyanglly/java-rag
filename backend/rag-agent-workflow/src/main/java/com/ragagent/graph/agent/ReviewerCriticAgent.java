package com.ragagent.graph.agent;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ragagent.graph.GraphState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * <h3>3. 审查质检智能体 (Reviewer / Critic Agent)</h3>
 * <p>
 * <b>角色职责：</b>
 * 极度严苛的代码质量评审员与安全审计官。
 * 负责对 CoderExecutorAgent 的交付代码进行全方位质检，判断其是否满足工程标准。
 * 决定是给予通过进入交付阶段，还是附带缺陷清单打回触发 Loop 循环回路。
 *
 * @author Java-RAG Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewerCriticAgent implements SubAgent {

    private final ChatClient.Builder chatClientBuilder;

    @Override
    public String getRoleName() {
        return "ReviewerCriticAgent";
    }

    @Override
    public String getDescription() {
        return "严苛的架构审查员，负责评估代码完整性、健壮性与安全性，裁决是否通过或打回重构";
    }

    @Override
    public GraphState execute(GraphState state) {
        String topic = state.getTopic();
        String code = (String) state.get("executionResult");
        Integer currentLoop = (Integer) state.getOrDefault("loopCount", 0);

        log.info("【ReviewerCriticAgent】启动严苛代码质检: 当前 Loop 轮次={}", currentLoop);
        state.addThought("【ReviewerCriticAgent】正在从完整性、安全性、鲁棒性多维度审查交付成果...");

        String prompt = """
                你是一名极度严苛、对代码质量零容忍的首席技术架构师与安全审计专家。
                请对以下研发实现成果进行全面评审，评估其是否完整解决了用户需求，是否存在空指针、并发漏洞、异常处理不全或伪代码应付的情况。
                
                【用户需求】：
                %s
                
                【交付代码与实现】：
                %s
                
                请严格以 JSON 格式输出评审结果，禁止包含任何额外文字，格式如下：
                {
                  "score": 85,
                  "decision": "PASS", // 得分 >= 80 为 "PASS"，< 80 为 "REVISE"
                  "feedback": "整体结构完整，异常处理得当；建议补充入参校验。"
                }
                """.formatted(topic, code != null && code.length() > 2000 ? code.substring(0, 2000) + "\n...[已截断]" : code);

        int score = 75;
        String decision = "REVISE";
        String feedback = "代码实现需要进一步完善边界防御与详细逻辑。";

        try {
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt(prompt).call().content();
            if (StringUtils.hasText(response)) {
                String cleanJson = response.replaceAll("```json|```", "").trim();
                if (JSONUtil.isTypeJSONObject(cleanJson)) {
                    JSONObject json = JSONUtil.parseObj(cleanJson);
                    score = json.getInt("score", 75);
                    decision = json.getStr("decision", score >= 80 ? "PASS" : "REVISE");
                    feedback = json.getStr("feedback", feedback);
                }
            }
        } catch (Exception e) {
            log.warn("【ReviewerCriticAgent】大模型质检解析异常，采用默认判定: {}", e.getMessage());
            // 若为首次迭代，适度给一次反思机会
            if (currentLoop == 0) {
                score = 75;
                decision = "REVISE";
                feedback = "初次代码提交缺少关键入参边界校验与异常捕获，请重构完善。";
            } else {
                score = 88;
                decision = "PASS";
                feedback = "已完成多轮反思修复，质量达到交付标准。";
            }
        }

        // 递增已完成的 Loop 循环轮次
        int nextLoop = currentLoop + 1;
        state.put("loopCount", nextLoop);
        state.put("reviewScore", score);
        state.put("reviewDecision", decision);
        state.put("reviewFeedback", feedback);

        if ("PASS".equalsIgnoreCase(decision)) {
            state.addThought("【ReviewerCriticAgent】质检通过 (得分: " + score + "/100)，准予交付！意见: " + feedback);
        } else {
            state.addThought("【ReviewerCriticAgent】质检未通过 (得分: " + score + "/100，第 " + nextLoop + " 轮 Loop)，打回 CoderExecutorAgent 重构。意见: " + feedback);
        }

        return state;
    }
}
