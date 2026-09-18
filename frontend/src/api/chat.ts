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
 * 启动 SSE 流式对话请求
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
