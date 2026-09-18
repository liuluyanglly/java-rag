import request from './request';
import { MenuItem } from './auth';

export interface UserItem {
  userId: number;
  username: string;
  nickName: string;
  email?: string;
  phone?: string;
  status: string;
  createTime: string;
  roleIds?: number[];
}

export interface RoleItem {
  roleId: number;
  roleName: string;
  roleKey: string;
  roleSort: number;
  status: string;
  createTime: string;
  menuIds?: number[];
}

export const getUserListApi = (params: { pageNum?: number; pageSize?: number; username?: string }) => {
  return request.get<{ records: UserItem[]; total: number }>('/system/user/list', { params });
};

export const createUserApi = (data: Partial<UserItem>) => {
  return request.post('/system/user', data);
};

export const updateUserApi = (data: Partial<UserItem>) => {
  return request.put('/system/user', data);
};

export const deleteUserApi = (userId: number) => {
  return request.delete(`/system/user/${userId}`);
};

export const getRoleListApi = (params: { pageNum?: number; pageSize?: number; roleName?: string }) => {
  return request.get<{ records: RoleItem[]; total: number }>('/system/role/list', { params });
};

export const getAllRolesApi = () => {
  return request.get<RoleItem[]>('/system/role/all');
};

export const createRoleApi = (data: Partial<RoleItem>) => {
  return request.post('/system/role', data);
};

export const updateRoleApi = (data: Partial<RoleItem>) => {
  return request.put('/system/role', data);
};

export const deleteRoleApi = (roleId: number) => {
  return request.delete(`/system/role/${roleId}`);
};

export const getMenuTreeApi = () => {
  return request.get<MenuItem[]>('/system/menu/tree');
};
