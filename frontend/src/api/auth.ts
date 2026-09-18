import request from './request';

export interface LoginParams {
  username: string;
  password: string;
}

export interface UserInfo {
  userId: number;
  username: string;
  nickName: string;
  avatar: string;
  status: string;
}

export interface AuthInfoResponse {
  user: UserInfo;
  roles: any[];
  permissions: string[];
}

export interface MenuItem {
  menuId: number;
  menuName: string;
  parentId: number;
  orderNum: number;
  path: string;
  component: string;
  menuType: string;
  icon: string;
  perms: string;
  children?: MenuItem[];
}

export const loginApi = (data: LoginParams) => {
  return request.post<{ token: string }>('/auth/login', data);
};

export const getInfoApi = () => {
  return request.get<AuthInfoResponse>('/auth/info');
};

export const getRoutersApi = () => {
  return request.get<MenuItem[]>('/auth/routers');
};

export const logoutApi = () => {
  return request.post('/auth/logout');
};
