import { create } from 'zustand';
import { getInfoApi, getRoutersApi, logoutApi, MenuItem, UserInfo } from '../api/auth';

interface AuthState {
  token: string | null;
  userInfo: UserInfo | null;
  roles: any[];
  permissions: string[];
  routers: MenuItem[];
  setToken: (token: string) => void;
  fetchUserInfo: () => Promise<void>;
  logout: () => Promise<void>;
  hasPermission: (perm: string) => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  token: localStorage.getItem('token'),
  userInfo: null,
  roles: [],
  permissions: [],
  routers: [],

  setToken: (token: string) => {
    localStorage.setItem('token', token);
    set({ token });
  },

  fetchUserInfo: async () => {
    try {
      const [infoRes, routersRes] = await Promise.all([getInfoApi(), getRoutersApi()]);
      set({
        userInfo: infoRes.user,
        roles: infoRes.roles,
        permissions: infoRes.permissions,
        routers: routersRes,
      });
    } catch (e) {
      console.error('获取用户信息失败', e);
    }
  },

  logout: async () => {
    try {
      await logoutApi();
    } finally {
      localStorage.removeItem('token');
      set({ token: null, userInfo: null, permissions: [], routers: [] });
      window.location.href = '/login';
    }
  },

  hasPermission: (perm: string) => {
    const { permissions } = get();
    if (permissions.includes('*:*:*')) return true;
    return permissions.includes(perm);
  },
}));
