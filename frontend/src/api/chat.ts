import request, { TOKEN_HEADER } from './request';

export interface ChatSessionItem {
  id: number;
  sessionId: string;
  userId: number;
  agentId?: number;
  title: string;
  pinned: boolean;
  createTime: string;
  updateTime: string;
  messageCount?: number;
  lastUserMessage?: string;
  lastAssistantMessage?: string;
}

export interface CitationItem {
  chunkId?: number;
  datasetId?: number;
  documentName: string;
  content: string;
  score?: number;
}

export interface ChatMessageItem {
  id?: number;
  sessionId: string;
  role: 'user' | 'assistant' | 'system' | 'tool';
  content: string;
  thought?: string;
  tokens?: number;
  citations?: string; // JSON string of CitationItem[]
  memories?: string;  // JSON string of AgentMemoryItem[]
  createTime?: string;
  sessionTitle?: string;
}

export interface AgentMemoryItem {
  id: string;
  userId: number;
  agentId?: number;
  sessionId?: string;
  memoryType: 'SEMANTIC' | 'PREFERENCE' | 'EPISODIC' | 'CORRECTION';
  content: string;
  importance: number;
  accessCount: number;
  score?: number;
  metadata?: string;
  createTime: string;
  updateTime: string;
  lastAccessedTime: string;
}

export interface FeedbackRequest {
  agentId?: number;
  sessionId?: string;
  query: string;
  answer: string;
  rating: number; // 1: 赞, -1: 踩
  comment?: string;
}

export interface ReflexionResponse {
  query: string;
  originalAnswer: string;
  userFeedback: string;
  errorDiagnosis: string;
  improvementRule: string;
  importance: number;
}

export const getChatSessionsApi = () => {
  return request.get<ChatSessionItem[]>('/chat/sessions');
};

export const createChatSessionApi = (agentId?: number) => {
  return request.post<ChatSessionItem>('/chat/session', null, { params: { agentId } });
};

export const getChatHistoryApi = (sessionId: string) => {
  return request.get<ChatMessageItem[]>(`/chat/history/${sessionId}`);
};

export const deleteChatSessionApi = (sessionId: string) => {
  return request.delete<boolean>(`/chat/session/${sessionId}`);
};


/**
 * 长期记忆与自进化管理 API
 */
export const getMemoriesApi = (memoryType?: string) => {
  return request.get<AgentMemoryItem[]>('/memory/list', { params: { memoryType } });
};

export const searchMemoriesApi = (query: string, topK: number = 5) => {
  return request.get<AgentMemoryItem[]>('/memory/search', { params: { query, topK } });
};

export const deleteMemoryApi = (id: string) => {
  return request.delete<boolean>(`/memory/${id}`);
};

export const clearSessionMemoryApi = (sessionId: string) => {
  return request.delete<boolean>(`/memory/session/${sessionId}`);
};

export const submitFeedbackApi = (data: FeedbackRequest) => {
  return request.post<ReflexionResponse>('/memory/feedback', data);
};

/**
 * <h3>启动 SSE (Server-Sent Events) 流式对话请求客户端</h3>
 * <p>
 * <b>学习核心知识点：</b>
 * <ol>
 *   <li><b>为什么不用浏览器原生的 EventSource？</b>
 *       <br>标准浏览器的 {@code EventSource} API 仅支持 HTTP GET 请求且无法在 Header 中直接附加自定义认证 Token。
 *       本实现采用现代标准的 {@code fetch()} 配合 {@code ReadableStream}，支持 POST 请求体与 Sa-Token 鉴权 Header。</li>
 *   <li><b>SSE 协议规范解析：</b>
 *       <br>SSE 消息块以双换行符 ({@code \n\n}) 分割。每个事件可包含 {@code event: <名称>} 与多行 {@code data: <数据>}。
 *       解码器必须妥善处理 TCP 分包导致的半截数据暂存缓存（Buffer）。</li>
 *   <li><b>主动取消机制：</b>
 *       <br>返回一个标准的 {@link AbortController}，当用户在前端点击“停止生成”按钮时，调用 {@code controller.abort()} 即可瞬间切断 HTTP 连接，停止服务端算力消耗。</li>
 * </ol>
 *
 * @param data      请求载荷（会话 ID、指定智能体、提问文本、挂载知识库 ID 列表）
 * @param callbacks 各通道流式事件回调字典
 * @return 控制器句柄 (可随时调用 .abort() 终止请求)
 */
export const streamChat = (
  data: {
    sessionId?: string;
    agentId?: number;
    message: string;
    datasetIds?: number[];
  },
  callbacks: {
    onThought?: (thought: string) => void;
    onSessionTitle?: (title: string, sessionId?: string) => void;
    onCitations?: (citations: CitationItem[]) => void;
    onMemories?: (memories: AgentMemoryItem[]) => void;
    onMessage?: (token: string) => void;
    onFinish?: (finalMsg: ChatMessageItem) => void;
    onError?: (err: any) => void;
  }
) => {
  const token = localStorage.getItem('token');
  // 创建可取消控制器
  const controller = new AbortController();

  fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [TOKEN_HEADER]: token ?? '',
    },
    body: JSON.stringify(data),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const reader = response.body?.getReader();
      const decoder = new TextDecoder('utf-8');

      let buffer = '';
      while (reader) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const blocks = buffer.split(/\r?\n\r?\n/);
        buffer = blocks.pop() || '';

        for (const block of blocks) {
          if (!block.trim()) continue;
          const blockLines = block.split(/\r?\n/);
          let eventName = 'message';
          const dataParts: string[] = [];

          for (const line of blockLines) {
            if (line.startsWith('event:')) {
              eventName = line.replace(/^event:\s*/, '').trim();
            } else if (line.startsWith('data:')) {
              // 按照 SSE 标准仅移除 data: 后的前导单个空格，完整保留换行与后续格式
              dataParts.push(line.replace(/^data:\s?/, ''));
            }
          }

          const rawData = dataParts.join('\n');
          let eventData = rawData;

          // 兼容解包结构化 JSON (保证 Markdown 换行符与格式 100% 无损还原)
          if (eventName === 'message' || eventName === 'thought') {
            try {
              const parsed = JSON.parse(rawData);
              if (parsed && typeof parsed === 'object') {
                if (typeof parsed.content === 'string') {
                  eventData = parsed.content;
                } else if (typeof parsed.delta === 'string') {
                  eventData = parsed.delta;
                }
              }
            } catch {
              // 保持纯文本 rawData 原样
            }
          }

          if (eventName === 'thought') {
            callbacks.onThought?.(eventData);
          } else if (eventName === 'session_title') {
            try {
              const parsed = JSON.parse(rawData);
              callbacks.onSessionTitle?.(parsed.title, parsed.sessionId);
            } catch (e) {
              console.error(e);
            }
          } else if (eventName === 'citations') {
            try {
              callbacks.onCitations?.(JSON.parse(rawData));
            } catch (e) {
              console.error(e);
            }
          } else if (eventName === 'memories') {
            try {
              callbacks.onMemories?.(JSON.parse(rawData));
            } catch (e) {
              console.error(e);
            }
          } else if (eventName === 'message') {
            callbacks.onMessage?.(eventData);
          } else if (eventName === 'finish') {
            try {
              const parsed = JSON.parse(rawData);
              if (parsed?.sessionTitle) {
                callbacks.onSessionTitle?.(parsed.sessionTitle, parsed.sessionId);
              }
              callbacks.onFinish?.(parsed);
            } catch (e) {
              console.error(e);
            }
          } else if (eventName === 'error') {
            callbacks.onError?.(eventData);
          }
        }
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        callbacks.onError?.(err);
      }
    });

  return controller;
};
