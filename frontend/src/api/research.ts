import request from './request';

export interface ResearchTaskItem {
  id: number;
  taskId: string;
  userId: number;
  topic: string;
  status: 'PLANNING' | 'RESEARCHING' | 'VERIFYING' | 'COMPLETED' | 'FAILED';
  planSteps: string; // JSON string
  currentStep: number;
  reportMarkdown: string;
  citations: string; // JSON string
  createTime: string;
}

export const submitResearchApi = (data: { topic: string }) => {
  return request.post<ResearchTaskItem>('/research/submit', data);
};

export const getResearchTasksApi = () => {
  return request.get<ResearchTaskItem[]>('/research/tasks');
};

export const getResearchTaskDetailApi = (taskId: string) => {
  return request.get<ResearchTaskItem>(`/research/task/${taskId}`);
};

export const deleteResearchTaskApi = (taskId: string) => {
  return request.delete<boolean>(`/research/task/${taskId}`);
};
