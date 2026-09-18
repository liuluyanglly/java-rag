package com.ragagent.agent;

import com.ragagent.agent.advisor.ReReadingAdvisor;
import com.ragagent.agent.service.PromptFileService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SpringAi2IntegrationTest {

    @Test
    public void testPromptFileServiceLoadingAndRendering() {
        PromptFileService service = new PromptFileService();
        service.init();

        List<PromptFileService.PromptTemplateInfo> list = service.listTemplates();
        assertNotNull(list);
        assertFalse(list.isEmpty(), "提示词模板列表不应为空");
        System.out.println("成功加载模板数量: " + list.size());
        for (PromptFileService.PromptTemplateInfo info : list) {
            System.out.println(" - 模板: " + info.getTemplateId() + " (" + info.getName() + "), 变量: " + info.getVariables());
        }

        // 测试 system-default 渲染
        String renderedDefault = service.render("system-default", Map.of(
                "currentTime", "2026-09-18 10:30:00",
                "dayOfWeek", "星期五"
        ));
        assertNotNull(renderedDefault);
        assertTrue(renderedDefault.contains("2026-09-18 10:30:00"), "渲染后应包含当前时间");
        assertTrue(renderedDefault.contains("星期五"), "渲染后应包含星期");
        System.out.println("\n【system-default 渲染结果预览】:\n" + renderedDefault);

        // 测试 customer-service 渲染
        String renderedCustomer = service.render("customer-service", Map.of(
                "companyName", "阿尔法智联科技",
                "serviceScope", "智能RAG检索与知识中枢",
                "hotline", "400-888-9999"
        ));
        assertTrue(renderedCustomer.contains("阿尔法智联科技"));
        assertTrue(renderedCustomer.contains("400-888-9999"));
        System.out.println("\n【customer-service 渲染结果预览】:\n" + renderedCustomer);
    }

    @Test
    public void testReReadingAdvisorNameAndOrder() {
        ReReadingAdvisor advisor = new ReReadingAdvisor();
        assertEquals("ReReadingAdvisor", advisor.getName());
        assertTrue(advisor.getOrder() < 1000);
    }

    @Test
    public void testChineseTokenTextSplitter() {
        com.ragagent.rag.service.ChineseTokenTextSplitter splitter = new com.ragagent.rag.service.ChineseTokenTextSplitter(200);
        org.springframework.ai.document.Document doc = new org.springframework.ai.document.Document(
                "Spring AI 2.0 是一个开创性的企业级 AI 框架！它全面拥抱了模块化 RAG 架构。\n\n" +
                "在中文语料场景下，传统的 TokenTextSplitter 无法有效切分中文句号与标点。通过 ChineseTokenTextSplitter，" +
                "系统可以智能结合段落、标点符号与滑动窗口，避免语义被打碎！退费政策需要遵循企业知识库规约。"
        );
        List<org.springframework.ai.document.Document> chunks = splitter.apply(List.of(doc));
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        System.out.println("中文分词器生成切片数: " + chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("  切片[" + (i + 1) + "]: " + chunks.get(i).getText());
        }
    }

    @Test
    public void testCustomerServiceTool() {
        com.ragagent.agent.tools.CustomerServiceTool tool = new com.ragagent.agent.tools.CustomerServiceTool();
        
        // 1. 查询有效企业工单
        String detail = tool.getBookingDetails(new com.ragagent.agent.tools.CustomerServiceTool.QueryBookingRequest("BK20260901", "张伟"));
        assertNotNull(detail);
        assertTrue(detail.contains("张伟"));
        assertTrue(detail.contains("SRV-2026-X1"));
        System.out.println("工单查询结果: " + detail);

        // 2. 身份不符校验
        String mismatch = tool.getBookingDetails(new com.ragagent.agent.tools.CustomerServiceTool.QueryBookingRequest("BK20260901", "李雷"));
        assertTrue(mismatch.contains("不匹配"));

        // 3. 工单撤销办理
        String cancel = tool.cancelBooking(new com.ragagent.agent.tools.CustomerServiceTool.CancelBookingRequest("BK20260901", "张伟", "业务调整"));
        assertTrue(cancel.contains("成功"));
        System.out.println("工单撤销结果: " + cancel);
    }

    @Test
    public void testRagEvaluationServiceRuleFallback() {
        com.ragagent.rag.service.RagEvaluationService service = new com.ragagent.rag.service.RagEvaluationService(null);
        String query = "退票费用是多少？";
        String context = "取消预订：经济舱取消费用为75美元。";
        String response = "根据规定，经济舱退票需要支付75美元的手续费。";

        com.ragagent.rag.service.RagEvaluationService.RagEvalResult result = service.evaluate(query, context, response);
        assertNotNull(result);
        assertTrue(result.isPass());
        assertTrue(result.getTotalScore() >= 0.7);
        System.out.println("RAG 评测结果: " + result);
    }

    @Test
    public void testAgentEnvironmentAndTodoTracker() {
        // 1. 测试环境自动感知
        com.ragagent.agent.tools.AgentEnvironment env = new com.ragagent.agent.tools.AgentEnvironment();
        String envCtx = env.getEnvironmentContext();
        assertNotNull(envCtx);
        assertTrue(envCtx.contains("系统运行时环境"));
        assertTrue(envCtx.contains("操作系统"));
        System.out.println("环境感知输出:\n" + envCtx);

        // 2. 测试任务清单追踪器 (借鉴 Spring-AI-Agent-Utils)
        com.ragagent.agent.tools.TodoTrackerTool tracker = new com.ragagent.agent.tools.TodoTrackerTool();
        var task1 = tracker.addTask("session-101", "设计API", "设计用户认证接口");
        assertNotNull(task1);
        assertEquals(1, task1.getId());
        assertEquals(com.ragagent.agent.tools.TodoTrackerTool.TaskStatus.PENDING, task1.getStatus());

        String updateRes = tracker.updateTaskStatus("session-101", 1, "COMPLETED");
        assertTrue(updateRes.contains("COMPLETED"));

        var list = tracker.listTasks("session-101");
        assertEquals(1, list.size());
        assertEquals(com.ragagent.agent.tools.TodoTrackerTool.TaskStatus.COMPLETED, list.get(0).getStatus());
        System.out.println("TodoTracker 追踪任务清单: " + list);
    }

    @Test
    public void testAiObservabilityService() {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        // 模拟记录一次 ChatClient 操作
        registry.timer("spring.ai.chat.client.operation").record(java.time.Duration.ofMillis(850));
        // 模拟记录 Token 消耗
        registry.counter("gen_ai.client.token.usage", "gen_ai.token.type", "input").increment(350);
        registry.counter("gen_ai.client.token.usage", "gen_ai.token.type", "output").increment(150);

        com.ragagent.common.observability.AiObservabilityService obs = new com.ragagent.common.observability.AiObservabilityService(registry);
        var summary = obs.getMetricsSummary();

        assertNotNull(summary);
        assertEquals(1, summary.getChatClientCalls());
        assertEquals(350, summary.getInputTokens());
        assertEquals(150, summary.getOutputTokens());
        assertEquals(500, summary.getTotalTokens());
        assertTrue(summary.getEstimatedCostRmb() > 0);
        System.out.println("AI 可观测性度量汇总: " + summary);
    }
}

