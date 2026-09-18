package com.ragagent.agent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义重读 (Re2 - Re-Reading) 增强拦截器
 * 遵循 Spring AI 2.0 CallAdvisor 规范，对复杂推理问题通过让大模型再次审视输入问题强化思维链与准确度
 */
@Slf4j
public class ReReadingAdvisor implements CallAdvisor {

    private static final String DEFAULT_USER_TEXT_ADVISE = """
            {re2_input_query}

            【请再次仔细审视并确认上述核心问题】: {re2_input_query}
            """;

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 50; // 高优先级执行
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        log.debug("触发 ReReadingAdvisor 自定义重读前置拦截...");
        if (chatClientRequest == null || chatClientRequest.prompt() == null) {
            return chain.nextCall(chatClientRequest);
        }

        Prompt originalPrompt = chatClientRequest.prompt();
        List<Message> messages = new ArrayList<>(originalPrompt.getInstructions());
        
        // 查找最后一条用户提问消息并进行 Re2 增强
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof UserMessage && StringUtils.hasText(msg.getText())) {
                String inputQuery = msg.getText();
                String augmentedText = PromptTemplate.builder()
                        .template(DEFAULT_USER_TEXT_ADVISE)
                        .build()
                        .render(Map.of("re2_input_query", inputQuery));
                messages.set(i, new UserMessage(augmentedText));
                break;
            }
        }

        ChatClientRequest processedRequest = chatClientRequest.mutate()
                .prompt(new Prompt(messages, originalPrompt.getOptions()))
                .build();

        return chain.copy(this).nextCall(processedRequest);
    }
}
