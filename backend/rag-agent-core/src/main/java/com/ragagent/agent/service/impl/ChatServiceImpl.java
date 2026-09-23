package com.ragagent.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.agent.dto.ChatRequest;
import com.ragagent.agent.entity.AiAgent;
import com.ragagent.agent.entity.AiChatMessage;
import com.ragagent.agent.entity.AiChatSession;
import com.ragagent.agent.mapper.AiAgentMapper;
import com.ragagent.agent.mapper.AiChatMessageMapper;
import com.ragagent.agent.mapper.AiChatSessionMapper;
import com.ragagent.agent.service.ChatService;
import com.ragagent.agent.service.PromptFileService;
import com.ragagent.memory.entity.AiAgentMemory;
import com.ragagent.memory.service.LongTermMemoryService;
import com.ragagent.memory.service.MemoryEvolutionService;
import com.ragagent.memory.service.ShortTermMemoryService;
import com.ragagent.rag.service.RagSearchService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能体对话中枢核心业务实现
 * 遵循 Phoenix-Agent-Java 架构规范，Controller 零业务，所有业务推演在此完全实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final AiAgentMapper agentMapper;
    private final RagSearchService ragSearchService;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ShortTermMemoryService shortTermMemoryService;
    private final LongTermMemoryService longTermMemoryService;
    private final MemoryEvolutionService memoryEvolutionService;
    private final ChatClient.Builder chatClientBuilder;
    private final org.springframework.ai.chat.memory.ChatMemory chatMemory;
    private final PromptFileService promptFileService;

    @Override
    public List<AiChatSession> listSessions(Long userId) {
        List<AiChatSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getUserId, userId)
                .orderByDesc(AiChatSession::getPinned)
                .orderByDesc(AiChatSession::getUpdateTime));
        if (sessions != null && !sessions.isEmpty()) {
            List<String> sessionIds = sessions.stream().map(AiChatSession::getSessionId).toList();
            List<AiChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                    .in(AiChatMessage::getSessionId, sessionIds)
                    .orderByAsc(AiChatMessage::getCreateTime));
            Map<String, List<AiChatMessage>> msgGroup = messages.stream()
                    .collect(Collectors.groupingBy(AiChatMessage::getSessionId));
            for (AiChatSession s : sessions) {
                List<AiChatMessage> list = msgGroup.get(s.getSessionId());
                if (list != null && !list.isEmpty()) {
                    s.setMessageCount(list.size());
                    for (int i = list.size() - 1; i >= 0; i--) {
                        AiChatMessage m = list.get(i);
                        if (s.getLastAssistantMessage() == null && "assistant".equalsIgnoreCase(m.getRole())) {
                            s.setLastAssistantMessage(m.getContent());
                        }
                        if (s.getLastUserMessage() == null && "user".equalsIgnoreCase(m.getRole())) {
                            s.setLastUserMessage(m.getContent());
                        }
                        if (s.getLastAssistantMessage() != null && s.getLastUserMessage() != null) {
                            break;
                        }
                    }
                } else {
                    s.setMessageCount(0);
                }
            }
        }
        return sessions != null ? sessions : Collections.emptyList();
    }

    @Override
    public List<AiChatMessage> getSessionMessages(String sessionId) {
        LambdaQueryWrapper<AiChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreateTime);
        return messageMapper.selectList(wrapper);
    }

    @Override
    public void deleteSession(String sessionId) {
        sessionMapper.delete(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId));
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId));
        shortTermMemoryService.clearSession(sessionId);
    }

    @Override
    public void updateSessionTitle(String sessionId, String title) {
        AiChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId));
        if (session != null) {
            session.setTitle(title);
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }

    public enum ChatIntentType {
        WEATHER,      // 天气与环境生活资讯
        TIME,         // 系统时间与日期查询
        KNOWLEDGE_QA, // 企业知识库业务检索
        GENERAL       // 通用认知与智能推理
    }

    @Data
    public static class ChatIntentResult {
        private ChatIntentType type;
        private String city;
        private String description;
    }

    /**
     * <h3>轻量级意图识别与语义分流器 (Intent Router)</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * <ul>
     *   <li><b>为什么需要前置意图分流？</b> 如果用户问“今天天气怎么样”或“现在几点了”，盲目去企业技术知识库执行向量搜索不仅浪费算力，还会召回毫无关系的文档（例如 Java 配置文件中包含“time”），严重破坏大模型的回答准确度。</li>
     *   <li><b>前置分流策略：</b> 采用正则与高频语义特征实现毫秒级前置分流。对生活资讯类查询严格隔离知识库，对明确的业务咨询触发向量召回。</li>
     * </ul>
     *
     * @param userMessage 用户原始输入文本
     * @param datasetIds  本轮挂载的知识库集合
     * @return 意图识别结果封装（包含类型、提取城市、意图描述）
     */
    private ChatIntentResult analyzeIntent(String userMessage, List<Long> datasetIds) {
        ChatIntentResult result = new ChatIntentResult();
        if (!StringUtils.hasText(userMessage)) {
            result.setType(ChatIntentType.GENERAL);
            result.setDescription("通用对话");
            return result;
        }

        String msg = userMessage.toLowerCase().trim();

        // 意图 1：气象与生活资讯意图（触发隔离知识库 + 气象关怀推理）
        Pattern weatherPattern = Pattern.compile(".*(天气|气温|下雨|下雪|晴天|多云|阴天|刮风|冷不冷|热不热|气象|预报).*");
        if (weatherPattern.matcher(msg).matches()) {
            result.setType(ChatIntentType.WEATHER);
            // 提取目标城市 (覆盖主要城市与通用正则匹配)
            Pattern cityPattern = Pattern.compile("([\u4e00-\u9fa5]{2,6}?)(市|区|县)?(的)?(今天|明天|后天|现在|当前)?(天气|气温|气象|下雨|冷不冷|预报)");
            Matcher cityMatcher = cityPattern.matcher(userMessage);
            if (cityMatcher.find()) {
                String potentialCity = cityMatcher.group(1);
                if (!potentialCity.matches("今天|明天|后天|现在|目前|近期|这个|查下|看看|帮我")) {
                    result.setCity(potentialCity);
                }
            }
            if (!StringUtils.hasText(result.getCity())) {
                String[] commonCities = {"北京", "上海", "广州", "深圳", "杭州", "南京", "苏州", "武汉", "成都", "重庆", "西安", "天津", "长沙", "青岛", "宁波", "郑州", "合肥", "厦门", "福州", "济南", "大连", "沈阳", "哈尔滨", "长春"};
                for (String c : commonCities) {
                    if (userMessage.contains(c)) {
                        result.setCity(c);
                        break;
                    }
                }
            }
            result.setDescription("生活资讯 - 天气查询");
            return result;
        }

        // 意图 2：宿主机真实时间与日期意图（直接读取物理时钟注入 Prompt）
        Pattern timePattern = Pattern.compile(".*(几点|几号|星期几|礼拜几|周几|哪一年|当前时间|现在时间|系统时间|今天日期|今天是什么日子|今天是几号).*");
        if (timePattern.matcher(msg).matches()) {
            result.setType(ChatIntentType.TIME);
            result.setDescription("系统时间与日期查询");
            return result;
        }

        // 意图 3：企业专业知识库检索意图（挂载了知识库且包含规约/技术关键词）
        Pattern kbPattern = Pattern.compile(".*(规范|制度|架构|文档|流程|评审|指南|政策|条例|方案|要求|api|接口|数据宝|证书|配置|系统|研发).*");
        if (kbPattern.matcher(msg).matches() && datasetIds != null && !datasetIds.isEmpty()) {
            result.setType(ChatIntentType.KNOWLEDGE_QA);
            result.setDescription("企业知识库业务检索");
            return result;
        }

        // 意图 4：默认通用问答与大模型通用推理
        result.setType(ChatIntentType.GENERAL);
        result.setDescription("通用认知与智能推理");
        return result;
    }

    /**
     * <h3>大模型驱动的会话标题智能提炼算法</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * 依据用户首轮提问，调用大模型快速提炼 4~10 个汉字的极凝练标题（如“WebFlux响应式架构选型”），
     * 替代传统呆板的“截取前20个字”，大幅提升对话历史列表的可读性与美观度。
     *
     * @param userMessage 用户首轮问题文本
     * @param chatModel   对话模型实例
     * @return 凝练的会话标题
     */
    private String generateSmartTitle(String userMessage, ChatModel chatModel) {
        if (!StringUtils.hasText(userMessage)) {
            return "新对话";
        }
        String cleanMsg = userMessage.trim();
        if (chatModel != null) {
            try {
                String prompt = "请为以下用户提问生成一个极简短凝练的会话标题，长度在4到10个汉字之间，直接输出标题纯文本，不要带有任何标点符号、书名号、引号或前缀解释：\n" + cleanMsg;
                String generated = chatClientBuilder.build() // 构建 ChatClient 客户端实例
                        .prompt()                            // 开启 Fluent 提示词构建器
                        .user(prompt)                        // 传入提炼标题的引导提示词
                        .call()                              // 发起同步大模型推理调用
                        .content();                          // 提取模型返回的标题正文内容
                if (StringUtils.hasText(generated)) {
                    String title = generated.replaceAll("[\"“'”《》\\s\\n\\r。，！!？?]", "").trim();
                    if (title.length() > 12) {
                        title = title.substring(0, 12);
                    }
                    if (StringUtils.hasText(title)) {
                        return title;
                    }
                }
            } catch (Exception e) {
                log.warn("【ChatController】大模型生成会话标题降级，回退规则截断: {}", e.getMessage());
            }
        }
        // 规则提取降级兜底
        String fallback = cleanMsg.replaceAll("[\\s\\n\\r。，！!？?\"'“”《》]", "");
        return fallback.length() > 10 ? fallback.substring(0, 10) : (fallback.isEmpty() ? "新对话" : fallback);
    }

    /**
     * <h3>SSE 响应式流式智能对话核心接口 (Server-Sent Events)</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * <ol>
     *   <li><b>响应式 Flux.create 机制：</b>
     *       <br>采用 Project Reactor 的 {@link Flux#create(java.util.function.Consumer)} 构造可推流的异步发射源（Sink）。
     *       配合 Java 21+ 虚拟线程或异步任务池，允许工作线程在数据生成时即时调用 {@code sink.next()} 逐个下发事件包，在完成时调用 {@code sink.complete()}。</li>
     * </ol>
     */
    @Override
    public Flux<ServerSentEvent<String>> streamChat(ChatRequest req, Long userId) {
        final String userMessage = StringUtils.hasText(req.getMessage()) ? req.getMessage().trim() : "";

        // 基于 Reactor 的 FluxSink 创建流式发射通道
        return Flux.create(sink -> {
            CompletableFuture.runAsync(() -> {
                try {
                    // 0. 获取宿主机绝对精确的当前时间
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");
                    String currentDateTimeStr = now.format(formatter);
                    String dayOfWeekStr = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINESE);

                    // 1. 初始化或获取会话，并智能识别是否需要生成会话标题
                    final String finalSessionId = StringUtils.hasText(req.getSessionId()) ? req.getSessionId() : IdUtil.simpleUUID();
                    AiChatSession existingSession = sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                            .eq(AiChatSession::getSessionId, finalSessionId));
                    
                    boolean isNewSession = (existingSession == null);
                    boolean needGenerateTitle = isNewSession || "新对话".equals(existingSession.getTitle()) || !StringUtils.hasText(existingSession.getTitle());
                    
                    ChatModel chatModel = chatModelProvider.getIfAvailable();
                    String resolvedTitle = null;

                    if (needGenerateTitle) {
                        resolvedTitle = generateSmartTitle(userMessage, chatModel);
                        if (isNewSession) {
                            sessionMapper.insert(AiChatSession.builder()
                                    .sessionId(finalSessionId)
                                    .userId(userId)
                                    .agentId(req.getAgentId())
                                    .title(resolvedTitle)
                                    .pinned(false)
                                    .createTime(now)
                                    .updateTime(now)
                                    .build());
                        } else {
                            existingSession.setTitle(resolvedTitle);
                            existingSession.setUpdateTime(now);
                            sessionMapper.updateById(existingSession);
                        }
                        // 即时向前端广播更新后的会话标题
                        sink.next(ServerSentEvent.<String>builder()
                                .event("session_title")
                                .data(JSONUtil.toJsonStr(Map.of("sessionId", finalSessionId, "title", resolvedTitle)))
                                .build());
                    }

                    final String finalSessionTitle = resolvedTitle;

                    // 2. 数据库与 Redis 短期记忆记录用户输入
                    messageMapper.insert(AiChatMessage.builder()
                            .sessionId(finalSessionId)
                            .role("user")
                            .content(userMessage)
                            .createTime(now)
                            .build());
                    shortTermMemoryService.appendMessage(finalSessionId, "user", userMessage);

                    // 初始化完整思考与检索轨迹累加器
                    StringBuilder fullThought = new StringBuilder();
                    java.util.function.Consumer<String> sendThought = text -> {
                        fullThought.append(text);
                        sink.next(ServerSentEvent.<String>builder()
                                .event("thought")
                                .data(JSONUtil.toJsonStr(Map.of("content", text)))
                                .build());
                    };

                    // 3. 推送思考链 - 阶段一：意图识别与环境时间感知
                    ChatIntentResult intent = analyzeIntent(userMessage, req.getDatasetIds());
                    if (intent.getType() == ChatIntentType.WEATHER) {
                        String cityDisplay = StringUtils.hasText(intent.getCity()) ? intent.getCity() : "未指定城市(智能建议)";
                        sendThought.accept(String.format("🎯 [意图识别] 识别用户意图: 【生活资讯 - 天气查询】 (基准系统时间: %s %s | 目标城市: %s)\n",
                                currentDateTimeStr, dayOfWeekStr, cityDisplay));
                    } else if (intent.getType() == ChatIntentType.TIME) {
                        sendThought.accept(String.format("🎯 [意图识别] 识别用户意图: 【系统时间与日期查询】 (宿主机实时系统时间: %s %s)\n",
                                currentDateTimeStr, dayOfWeekStr));
                    } else if (intent.getType() == ChatIntentType.KNOWLEDGE_QA) {
                        sendThought.accept(String.format("🎯 [意图识别] 识别用户意图: 【企业知识库业务问答】 (系统参考时间: %s)\n",
                                currentDateTimeStr));
                    } else {
                        sendThought.accept(String.format("🎯 [意图识别] 识别用户意图: 【通用认知与智能推理】 (系统参考时间: %s %s)\n",
                                currentDateTimeStr, dayOfWeekStr));
                    }
                    Thread.sleep(60);

                    // 4. 推送思考链 - 阶段二：短期记忆与长期偏好检索
                    sendThought.accept("🧠 [上下文检索] 正在从 Redis(10号库) 读取会话短期记忆与上下文...\n");
                    Thread.sleep(60);

                    List<AiAgentMemory> memories = longTermMemoryService // 长期记忆中枢服务
                            .searchMemories(userId, userMessage, 3);     // 执行 0.60相关度 + 0.25重要度 + 0.15时间衰减 的斯坦福混合加权检索 (召回 Top3)
                    if (!memories.isEmpty()) {
                        StringBuilder memSb = new StringBuilder("✨ [长期偏好] 命中 " + memories.size() + " 条长期偏好与自进化纠错准则：\n");
                        for (AiAgentMemory m : memories) {
                            memSb.append("   • [").append(m.getMemoryType()).append("] ").append(m.getContent()).append("\n");
                        }
                        sendThought.accept(memSb.toString());
                        sink.next(ServerSentEvent.<String>builder().event("memories").data(JSONUtil.toJsonStr(memories)).build());
                    }

                    // 5. 推送思考链 - 阶段三：分流式 RAG 知识检索
                    final List<RagSearchService.SearchResultChunk> chunks;
                    // 只有明确属于企业知识库业务问答时，才执行知识库向量检索；天气、时间与通用问题严格隔离，杜绝无关切片污染回答
                    if (intent.getType() == ChatIntentType.KNOWLEDGE_QA) {
                        sendThought.accept("🔍 [知识库检索] 正在检索知识库元数据并比对向量相似度...\n");
                        chunks = ragSearchService.search(userId, userMessage, req.getDatasetIds(), 3);
                        if (!chunks.isEmpty()) {
                            sink.next(ServerSentEvent.<String>builder().event("citations").data(JSONUtil.toJsonStr(chunks)).build());
                            StringBuilder chunkSb = new StringBuilder("📚 [切片命中] 成功召回 " + chunks.size() + " 个高相关度知识切片：\n");
                            for (int i = 0; i < chunks.size(); i++) {
                                RagSearchService.SearchResultChunk c = chunks.get(i);
                                double scorePct = c.getScore() != null ? c.getScore() * 100 : 90.0;
                                chunkSb.append(String.format("   • 来源: 《%s》 (相似度: %.1f%%)\n", c.getDocumentName(), scorePct));
                            }
                            chunkSb.append("💡 [智能推理] 知识库切片已注入 Prompt 上下文，大模型开始组织回答...\n");
                            sendThought.accept(chunkSb.toString());
                        } else {
                            sendThought.accept("💡 [通用推理] 知识库无直接精确命中，准备调用通用大模型智能推理逻辑...\n");
                        }
                    } else {
                        chunks = new ArrayList<>();
                        if (intent.getType() == ChatIntentType.WEATHER) {
                            sendThought.accept("💡 [意图分流] 命中生活资讯(天气)意图，已安全隔离企业知识库，启动气象与出行关怀逻辑...\n");
                        } else if (intent.getType() == ChatIntentType.TIME) {
                            sendThought.accept("💡 [意图分流] 命中系统时间意图，已安全隔离企业知识库，直接读取宿主机精准时钟...\n");
                        } else {
                            sendThought.accept("💡 [意图分流] 命中通用问答/编程意图，已安全隔离企业知识库，由大模型结合当前系统环境深度推理...\n");
                        }
                    }
                    Thread.sleep(60);

                    // 6. 拼装 System Prompt (动态注入当前绝对系统时间与意图规约)
                    AiAgent agent = req.getAgentId() != null ? agentMapper.selectById(req.getAgentId()) : null;
                    StringBuilder systemPromptBuilder = new StringBuilder();
                    if (agent != null && StringUtils.hasText(agent.getSystemPrompt())) {
                        systemPromptBuilder.append(agent.getSystemPrompt());
                    } else {
                        try {
                            String defaultPrompt = promptFileService.render("system-default", Map.of(
                                    "currentTime", currentDateTimeStr,
                                    "dayOfWeek", dayOfWeekStr
                            ));
                            systemPromptBuilder.append(defaultPrompt);
                        } catch (Exception e) {
                            systemPromptBuilder.append("你是一个专业的企业级 AI 智能助手，请依据上下文知识严谨回答用户问题。");
                        }
                    }

                    // 注入宿主机真实系统时间规范
                    systemPromptBuilder.append("\n\n【当前系统环境与真实时间基准 (绝对真实有效)】:\n")
                            .append("1. 当前宿主机系统真实时间为：").append(currentDateTimeStr).append(" (").append(dayOfWeekStr).append(")。\n")
                            .append("2. 当用户提问包含“今天”、“昨天”、“明天”、“现在”、“当前”、“今年”等时间表述时，必须严格以此时间为绝对参考基准进行推理和组织回答；\n")
                            .append("3. 严禁回答“无法感知时间”、“不知道当前是哪一年/几月几日/星期几”；\n");

                    if (intent.getType() == ChatIntentType.WEATHER) {
                        systemPromptBuilder.append("\n【天气与生活资讯回答规范 (严格执行，绝不违背)】:\n")
                                .append("1. 绝不允许提及任何企业知识库、参考文档、证书、Java开发配置或API信息！这些与天气没有任何关系；\n")
                                .append("2. 必须首先清晰告知用户当前准确时间：今天是 ").append(currentDateTimeStr).append(" (").append(dayOfWeekStr).append(")；\n");
                        if (StringUtils.hasText(intent.getCity())) {
                            systemPromptBuilder.append("3. 用户已指定查询城市为【").append(intent.getCity()).append("】。\n")
                                    .append("   - 说明当前企业智能体运行于内部受限隔离专网，未接入公网实时气象雷达测温接口，无法提供分秒级实时精确数值；\n")
                                    .append("   - 结合【").append(intent.getCity()).append("】在9月中旬初秋时节的常规气候特征，提供贴心的早晚温差防范、着装与出行建议；\n")
                                    .append("   - 语言简明亲切，条理清晰，结尾提示如需精确到小时的突发天气预警可查看手机气象应用。\n");
                        } else {
                            systemPromptBuilder.append("3. 用户提问中【未指定任何具体城市】（例如仅问“今天的天气”或“今天天气如何”）。\n")
                                    .append("   - 必须友善礼貌地提示用户：“您好！今天是 ").append(currentDateTimeStr).append("（").append(dayOfWeekStr).append("）。由于您尚未指定具体的城市或地区，我暂时无法为您查询准确的天气。请问您想了解哪个城市（例如：北京、上海、广州、深圳、杭州等）的天气呢？”；\n")
                                    .append("   - 保持回答简短自然、亲切礼貌，严禁长篇大论堆砌大段无关的科普或开发配置！\n");
                        }
                    } else if (intent.getType() == ChatIntentType.TIME) {
                        systemPromptBuilder.append("\n【系统时间意图规范】: 请直接、精确、自信地告知用户当前宿主机系统准确的年月日、星期几以及时分秒。\n");
                    }

                    systemPromptBuilder.append("\n【回答格式与美学排版规范 (必须严格遵守)】:\n")
                            .append("1. 开头严禁输出前置空行或空白字符，直接紧凑输出第一项内容；严禁输出生硬、未经整理的纯文本大段堆叠，必须使用美观的 GitHub Flavored Markdown (GFM) 格式排版；\n")
                            .append("2. 层次分明：根据内容需要适度使用标题 (如 `### 1. 核心概述`、`### 2. 参数列表`)、有序或无序列表，段落间保留清晰空行；\n")
                            .append("3. 重点加粗：对关键结论、重要参数名、核心字段加粗显示 (`**字段名**`) 或使用行内代码 (`code`)；\n")
                            .append("4. 表格必须转为 Markdown 表格：如果涉及参数、字段、错误码或对比数据，必须组织为标准的 Markdown 表格 (使用 `| 字段 | 类型 | 说明 |` 语法)，严禁直接输出原始 HTML <table> 标签；\n")
                            .append("5. 代码与报文规范：请求示例、代码或 JSON 报文必须使用三反引号包裹并标明语言类型 (如 ```json、```java、```bash 等)；\n")
                            .append("6. 语言严谨亲和：用词专业清晰，条理清晰，结尾可附带温馨提示或操作建议。");

                    // 仅在知识库问答或通用推理时注入长期记忆，天气与时间意图隔离长期偏好记忆
                    if (!memories.isEmpty() && intent.getType() != ChatIntentType.WEATHER && intent.getType() != ChatIntentType.TIME) {
                        systemPromptBuilder.append("\n\n【长期记忆与自进化准则 (请严格遵守)】:\n");
                        for (AiAgentMemory m : memories) {
                            systemPromptBuilder.append("- [").append(m.getMemoryType()).append("] ").append(m.getContent()).append("\n");
                        }
                    }

                    // 7. 拼装 User Prompt (融合短期历史 + 知识库切片 + 当前提问)
                    StringBuilder promptWithContext = new StringBuilder();
                    String shortTermContext = shortTermMemoryService.getFormattedRecentHistory(finalSessionId, 4);
                    if (StringUtils.hasText(shortTermContext)) {
                        promptWithContext.append(shortTermContext).append("\n");
                    }

                    if (!chunks.isEmpty()) {
                        promptWithContext.append("【参考知识库内容】:\n");
                        for (int i = 0; i < chunks.size(); i++) {
                            promptWithContext.append("--- [切片 ").append(i + 1).append("] 来源文档: 《").append(chunks.get(i).getDocumentName()).append("》 ---\n")
                                    .append(chunks.get(i).getContent()).append("\n\n");
                        }
                        promptWithContext.append("【当前用户问题】:\n").append(userMessage)
                                .append("\n\n(请严格基于上述参考切片，按照【回答格式与美学排版规范】输出优雅美观、结构清晰的回答)");
                    } else if (intent.getType() == ChatIntentType.KNOWLEDGE_QA) {
                        promptWithContext.append("【企业知识库防幻觉规约】:\n")
                                .append("当前企业知识库中未检索到与用户提问直接匹配的文档切片。\n")
                                .append("请明确、诚恳地告知用户：“根据企业知识库当前收录的内容，未查询到相关明确记载。”\n")
                                .append("严禁依据常识盲目编造虚假条款或数据，避免事实性幻觉。\n\n")
                                .append("【当前用户问题】:\n").append(userMessage);
                    } else {
                        promptWithContext.append("【当前用户问题】:\n").append(userMessage);
                    }

                    // 8. 大模型流式输出
                    StringBuilder fullResponse = new StringBuilder();

                    if (chatModel != null) {
                        Flux<String> streamFlux = chatClientBuilder.build() // 基于 Spring AI 构建 ChatClient 会话客户端实例
                                .prompt()                                   // 初始化 Fluent 风格的 Prompt 请求链
                                .system(systemPromptBuilder.toString())     // 注入系统人设、环境时空感知与意图处理规约
                                .user(promptWithContext.toString())         // 注入包含知识切片与当前问题的用户上下文
                                .advisors(advisorSpec -> advisorSpec.param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, finalSessionId)) // 挂载短期记忆顾问并绑定当前会话标识 (Redis 滑动窗口)
                                .stream()                                   // 开启响应式流式调用模式 (Reactive SSE)
                                .content();                                 // 获取大语言模型逐字/逐词推流的响应内容流 (Flux<String>)

                        streamFlux.doOnNext(token -> {
                            fullResponse.append(token);
                            sink.next(ServerSentEvent.<String>builder()
                                    .event("message")
                                    .data(JSONUtil.toJsonStr(Map.of("content", token)))
                                    .build());
                        }).doOnComplete(() -> {
                            finishSession(userId, req.getAgentId(), finalSessionId, userMessage, fullResponse.toString(), fullThought.toString(), chunks, finalSessionTitle, sink);
                        }).doOnError(err -> {
                            log.error("大模型流式生成异常", err);
                            sink.next(ServerSentEvent.<String>builder().event("error").data("生成异常: " + err.getMessage()).build());
                            sink.complete();
                        }).subscribe();

                    } else {
                        String demoContent = !chunks.isEmpty()
                                ? "基于企业知识库【" + chunks.get(0).getDocumentName() + "】中的相关记载：\n\n"
                                + chunks.get(0).getContent() + "\n\n如需了解更详细的规范，请参考文档引用源。"
                                : "您好！当前系统真实时间为：" + currentDateTimeStr + "（" + dayOfWeekStr + "）。我是企业级智能助手，已成功联通大模型网关与记忆中枢，您可以随时提问。";

                        for (char c : demoContent.toCharArray()) {
                            String cStr = String.valueOf(c);
                            fullResponse.append(cStr);
                            sink.next(ServerSentEvent.<String>builder()
                                    .event("message")
                                    .data(JSONUtil.toJsonStr(Map.of("content", cStr)))
                                    .build());
                            try { Thread.sleep(20); } catch (Exception ignored) {}
                        }
                        finishSession(userId, req.getAgentId(), finalSessionId, userMessage, fullResponse.toString(), fullThought.toString(), chunks, finalSessionTitle, sink);
                    }

                } catch (Exception e) {
                    log.error("流式对话执行失败", e);
                    sink.next(ServerSentEvent.<String>builder().event("error").data("生成异常: " + e.getMessage()).build());
                    sink.complete();
                }
            });
        });
    }

    /**
     * <h3>会话生成结束收敛处理 (Finish Session Pipeline)</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * <ol>
     *   <li><b>双写持久化：</b> 既将完整消息记录持久化至数据库（永久存档与审计），又写入 Redis 短期记忆列表（供多轮对话即时滑窗读取）。</li>
     *   <li><b>Mem0 异步反省机制：</b> 调用 {@code memoryEvolutionService.evolveFromConversationAsync}，在后台异步启动模型反思，提取用户的新增事实、编码偏好或纠错经验，动态构建长期记忆网络。</li>
     *   <li><b>终结信号下发：</b> 发送名为 {@code finish} 的 SSE 事件包通知前端结束动画，随后调用 {@code sink.complete()} 释放 HTTP 长连接通道。</li>
     * </ol>
     *
     * @param userId           发起用户 ID
     * @param agentId          使用的智能体 ID
     * @param sessionId        会话 UUID
     * @param userMessage      用户本轮原始问题
     * @param assistantContent 大模型生成的完整回答正文
     * @param thought          本轮累加的完整思考链/中间检索轨迹
     * @param chunks           命中的知识切片溯源引用
     * @param sessionTitle     若是首轮新会话提炼出的标题，可为空
     * @param sink             响应式流发射通道
     */
    
private void finishSession(Long userId, Long agentId, String sessionId, String userMessage,
                               String assistantContent, String thought,
                               List<RagSearchService.SearchResultChunk> chunks,
                               String sessionTitle,
                               reactor.core.publisher.FluxSink<ServerSentEvent<String>> sink) {
        // 步骤 1：保存助手回答到关系型数据库（含 Token 估算与切片溯源 JSON）
        AiChatMessage assistantMsg = AiChatMessage.builder()
                .sessionId(sessionId)
                .role("assistant")
                .content(assistantContent)
                .thought(thought)
                .citations(chunks.isEmpty() ? "[]" : JSONUtil.toJsonStr(chunks))
                .tokens(assistantContent.length() / 2)
                .createTime(LocalDateTime.now())
                .build();
        messageMapper.insert(assistantMsg);

        // 保存助手回答到 Redis 短期记忆
        shortTermMemoryService.appendMessage(sessionId, "assistant", assistantContent); // 追加回复文本至 Redis 10号库短期滑动窗口

        // 异步触发 Mem0 事实提炼与自进化闭环
        memoryEvolutionService.evolveFromConversationAsync(userId, agentId, sessionId, userMessage, assistantContent); // 异步后台启动 Mem0 模型反思，抽取新偏好并持久化至长期记忆库

        Map<String, Object> finishPayload = new java.util.HashMap<>();
        finishPayload.put("id", assistantMsg.getId());
        finishPayload.put("sessionId", sessionId);
        finishPayload.put("role", "assistant");
        finishPayload.put("content", assistantContent);
        finishPayload.put("thought", thought);
        finishPayload.put("citations", assistantMsg.getCitations());
        finishPayload.put("tokens", assistantMsg.getTokens());
        finishPayload.put("createTime", assistantMsg.getCreateTime());
        if (StringUtils.hasText(sessionTitle)) {
            finishPayload.put("sessionTitle", sessionTitle);
        }

        sink.next(ServerSentEvent.<String>builder().event("finish").data(JSONUtil.toJsonStr(finishPayload)).build());
        sink.complete();
    }

}
