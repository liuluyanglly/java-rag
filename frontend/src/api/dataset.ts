import request from './request';

export interface DatasetItem {
  id: number;
  name: string;
  description: string;
  avatar: string;
  embeddingModel: string;
  chunkSize: number;
  chunkOverlap: number;
  docCount: number;
  chunkCount: number;
  isPublic: boolean;
  createTime: string;
  authorizedRoleIds?: number[];
}

export interface DocumentItem {
  id: number;
  datasetId: number;
  name: string;
  fileSize: number;
  fileType: string;
  status: 'PENDING' | 'PARSING' | 'COMPLETED' | 'FAILED';
  chunkCount: number;
  tokenCount: number;
  errorMsg?: string;
  createTime: string;
}

export interface DocumentChunkItem {
  id: number;
  datasetId: number;
  documentId: number;
  chunkIndex: number;
  content: string;
  tokenCount: number;
  metadata: string;
  createTime: string;
}

export const getDatasetListApi = (params: { pageNum?: number; pageSize?: number; name?: string }) => {
  return request.get<{ records: DatasetItem[]; total: number }>('/dataset/list', { params });
};

export const getAccessibleDatasetsApi = () => {
  return request.get<DatasetItem[]>('/dataset/accessible');
};

export const createDatasetApi = (data: Partial<DatasetItem>) => {
  return request.post('/dataset', data);
};

export const updateDatasetApi = (data: Partial<DatasetItem>) => {
  return request.put('/dataset', data);
};

export const deleteDatasetApi = (id: number) => {
  return request.delete(`/dataset/${id}`);
};

export const getDocumentListApi = (params: { datasetId: number; pageNum?: number; pageSize?: number }) => {
  return request.get<{ records: DocumentItem[]; total: number }>('/document/list', { params });
};

export const getDocumentChunksApi = (params: { documentId: number; pageNum?: number; pageSize?: number }) => {
  return request.get<{ records: DocumentChunkItem[]; total: number }>('/document/chunks', { params });
};

export const reindexDocumentApi = (id: number) => {
  return request.post(`/document/${id}/reindex`);
};

export const deleteDocumentApi = (id: number) => {
  return request.delete(`/document/${id}`);
};

export const testDatasetHitApi = (datasetId: number, query: string, topK: number = 5) => {
  return request.get<
    Array<{
      chunkId?: number;
      datasetId: number;
      documentId: number;
      documentName: string;
      content: string;
      score: number;
    }>
  >(`/dataset/${datasetId}/hit-test`, { params: { query, topK } });
};

