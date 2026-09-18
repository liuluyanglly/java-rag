import React, { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Tag,
  Modal,
  Form,
  Input,
  InputNumber,
  Tree,
  Popconfirm,
  message,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, KeyOutlined } from '@ant-design/icons';
import {
  getRoleListApi,
  createRoleApi,
  updateRoleApi,
  deleteRoleApi,
  getMenuTreeApi,
  RoleItem,
} from '../../../api/system';
import { MenuItem } from '../../../api/auth';

export const RoleManagePage: React.FC = () => {
  const [roles, setRoles] = useState<RoleItem[]>([]);
  const [menuTree, setMenuTree] = useState<MenuItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingRole, setEditingRole] = useState<RoleItem | null>(null);
  const [checkedMenuKeys, setCheckedMenuKeys] = useState<React.Key[]>([]);
  const [form] = Form.useForm();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [roleRes, menuRes] = await Promise.all([
        getRoleListApi({ pageNum: 1, pageSize: 50 }),
        getMenuTreeApi(),
      ]);
      setRoles(roleRes.records);
      setMenuTree(menuRes);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenModal = (role?: RoleItem) => {
    if (role) {
      setEditingRole(role);
      form.setFieldsValue(role);
      setCheckedMenuKeys(role.menuIds ? role.menuIds.map(String) : []);
    } else {
      setEditingRole(null);
      form.resetFields();
      form.setFieldsValue({ roleSort: 1, status: '0' });
      setCheckedMenuKeys([]);
    }
    setModalVisible(true);
  };

  const handleSubmit = async (values: any) => {
    try {
      const payload = {
        ...values,
        menuIds: checkedMenuKeys.map((k) => Number(k)),
      };

      if (editingRole) {
        await updateRoleApi({ ...payload, roleId: editingRole.roleId });
        message.success('角色修改成功');
      } else {
        await createRoleApi(payload);
        message.success('角色创建成功');
      }
      setModalVisible(false);
      loadData();
    } catch (e) {}
  };

  const handleDelete = async (roleId: number) => {
    try {
      await deleteRoleApi(roleId);
      message.success('删除成功');
      loadData();
    } catch (e) {}
  };

  // 递归格式化 Menu 树为 AntD Tree 格式
  const formatTreeData = (menus: MenuItem[]): any[] => {
    return menus.map((m) => ({
      key: String(m.menuId),
      title: `${m.menuName} ${m.perms ? `(${m.perms})` : ''}`,
      children: m.children ? formatTreeData(m.children) : undefined,
    }));
  };

  return (
    <Card className="border border-slate-200 dark:border-slate-800 rounded-xl">
      <div className="flex justify-between items-center mb-5">
        <div>
          <h2 className="text-lg font-bold text-slate-800 dark:text-slate-100">
            角色与权限管理
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            配置角色标识符（如 admin, kb_admin）并挂载菜单与按钮权限树。
          </p>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => handleOpenModal()}
          className="bg-indigo-600 hover:bg-indigo-500 rounded-lg"
        >
          新增角色
        </Button>
      </div>

      <Table
        dataSource={roles}
        rowKey="roleId"
        loading={loading}
        columns={[
          { title: '角色ID', dataIndex: 'roleId', width: 90 },
          { title: '角色名称', dataIndex: 'roleName', key: 'roleName' },
          {
            title: '权限标识 (roleKey)',
            dataIndex: 'roleKey',
            render: (key) => <Tag color="geekblue">{key}</Tag>,
          },
          { title: '排序', dataIndex: 'roleSort', width: 80 },
          {
            title: '状态',
            dataIndex: 'status',
            render: (s) => (
              <Tag color={s === '0' ? 'success' : 'error'}>
                {s === '0' ? '正常' : '停用'}
              </Tag>
            ),
          },
          { title: '创建时间', dataIndex: 'createTime' },
          {
            title: '操作',
            key: 'action',
            render: (_, record) => (
              <div className="flex gap-2">
                <Button
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => handleOpenModal(record)}
                >
                  配置
                </Button>
                <Popconfirm
                  title="确认删除该角色吗？"
                  onConfirm={() => handleDelete(record.roleId)}
                  disabled={record.roleId === 1}
                >
                  <Button
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    disabled={record.roleId === 1}
                  >
                    删除
                  </Button>
                </Popconfirm>
              </div>
            ),
          },
        ]}
      />

      <Modal
        title={editingRole ? '配置角色与权限' : '新增角色'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        onOk={() => form.submit()}
        width={560}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item
            name="roleName"
            label="角色名称"
            rules={[{ required: true, message: '请输入角色名称' }]}
          >
            <Input placeholder="例如: 知识库审核员" />
          </Form.Item>
          <Form.Item
            name="roleKey"
            label="角色字符 (用于 Sa-Token @SaCheckRole)"
            rules={[{ required: true, message: '请输入权限字符' }]}
          >
            <Input disabled={editingRole?.roleId === 1} placeholder="例如: kb_auditor" />
          </Form.Item>
          <Form.Item name="roleSort" label="显示顺序">
            <InputNumber className="w-full" min={1} />
          </Form.Item>

          <div className="border border-slate-200 dark:border-slate-800 rounded-lg p-3 bg-slate-50 dark:bg-slate-900/60 max-h-60 overflow-y-auto">
            <div className="text-xs font-semibold text-slate-500 mb-2 flex items-center gap-1">
              <KeyOutlined className="text-indigo-500" /> 菜单与操作权限勾选:
            </div>
            <Tree
              checkable
              checkedKeys={checkedMenuKeys}
              onCheck={(keys: any) => setCheckedMenuKeys(keys)}
              treeData={formatTreeData(menuTree)}
            />
          </div>
        </Form>
      </Modal>
    </Card>
  );
};
