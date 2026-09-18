import React, { useState, useEffect } from 'react';
import { Card, Table, Tag } from 'antd';
import { getMenuTreeApi } from '../../../api/system';
import { MenuItem } from '../../../api/auth';

export const MenuManagePage: React.FC = () => {
  const [menus, setMenus] = useState<MenuItem[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const res = await getMenuTreeApi();
      setMenus(res);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card className="border border-slate-200 dark:border-slate-800 rounded-xl">
      <div className="mb-5">
        <h2 className="text-lg font-bold text-slate-800 dark:text-slate-100">
          菜单与权限标识列表
        </h2>
        <p className="text-xs text-slate-400 mt-0.5">
          系统路由架构定义，包括目录 (M)、页面菜单 (C) 与按钮权限标识 (F)。
        </p>
      </div>

      <Table
        dataSource={menus}
        rowKey="menuId"
        loading={loading}
        pagination={false}
        columns={[
          { title: '菜单名称', dataIndex: 'menuName', key: 'menuName' },
          {
            title: '类型',
            dataIndex: 'menuType',
            width: 90,
            render: (type) => {
              if (type === 'M') return <Tag color="orange">目录</Tag>;
              if (type === 'C') return <Tag color="cyan">菜单</Tag>;
              return <Tag color="default">按钮</Tag>;
            },
          },
          { title: '路由路径', dataIndex: 'path', key: 'path' },
          { title: '前端组件', dataIndex: 'component', key: 'component' },
          {
            title: '权限标识符 (perms)',
            dataIndex: 'perms',
            render: (p) => (p ? <Tag color="purple">{p}</Tag> : '-'),
          },
          { title: '排序', dataIndex: 'orderNum', width: 80 },
        ]}
      />
    </Card>
  );
};
