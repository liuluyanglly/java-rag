import React, { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  Popconfirm,
  message,
  Switch,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, UserOutlined } from '@ant-design/icons';
import {
  getUserListApi,
  createUserApi,
  updateUserApi,
  deleteUserApi,
  getAllRolesApi,
  UserItem,
  RoleItem,
} from '../../../api/system';

export const UserManagePage: React.FC = () => {
  const [users, setUsers] = useState<UserItem[]>([]);
  const [roles, setRoles] = useState<RoleItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<UserItem | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [userRes, roleRes] = await Promise.all([
        getUserListApi({ pageNum: 1, pageSize: 50 }),
        getAllRolesApi(),
      ]);
      setUsers(userRes.records);
      setRoles(roleRes);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenModal = (user?: UserItem) => {
    if (user) {
      setEditingUser(user);
      form.setFieldsValue({
        ...user,
        password: '', // 修改时不回填密码
      });
    } else {
      setEditingUser(null);
      form.resetFields();
      form.setFieldsValue({ status: '0' });
    }
    setModalVisible(true);
  };

  const handleSubmit = async (values: any) => {
    try {
      if (editingUser) {
        await updateUserApi({ ...values, userId: editingUser.userId });
        message.success('用户修改成功');
      } else {
        await createUserApi(values);
        message.success('用户创建成功');
      }
      setModalVisible(false);
      loadData();
    } catch (e) {}
  };

  const handleDelete = async (userId: number) => {
    try {
      await deleteUserApi(userId);
      message.success('删除成功');
      loadData();
    } catch (e) {}
  };

  return (
    <Card className="border border-slate-200 dark:border-slate-800 rounded-xl">
      <div className="flex justify-between items-center mb-5">
        <div>
          <h2 className="text-lg font-bold text-slate-800 dark:text-slate-100">
            用户管理
          </h2>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 max-w-xl leading-relaxed">
            管理系统用户账号、分配角色与访问状态。
          </p>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => handleOpenModal()}
          className="bg-indigo-600 hover:bg-indigo-500 rounded-lg"
        >
          新增用户
        </Button>
      </div>

      <Table
        dataSource={users}
        rowKey="userId"
        loading={loading}
        columns={[
          { title: '用户ID', dataIndex: 'userId', width: 90 },
          { title: '登录账号', dataIndex: 'username', key: 'username' },
          { title: '用户昵称', dataIndex: 'nickName', key: 'nickName' },
          { title: '邮箱', dataIndex: 'email', key: 'email', render: (v) => v || '-' },
          {
            title: '状态',
            dataIndex: 'status',
            render: (status) => (
              <Tag color={status === '0' ? 'success' : 'error'}>
                {status === '0' ? '正常' : '停用'}
              </Tag>
            ),
          },
          { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
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
                  编辑
                </Button>
                <Popconfirm
                  title="确认删除该用户吗？"
                  onConfirm={() => handleDelete(record.userId)}
                  disabled={record.userId === 1}
                >
                  <Button
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    disabled={record.userId === 1}
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
        title={editingUser ? '编辑用户' : '新增用户'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item
            name="username"
            label="登录账号"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input disabled={!!editingUser} placeholder="例如: zhangsan" />
          </Form.Item>
          <Form.Item
            name="nickName"
            label="用户昵称"
            rules={[{ required: true, message: '请输入昵称' }]}
          >
            <Input placeholder="例如: 张三" />
          </Form.Item>
          <Form.Item
            name="password"
            label={editingUser ? '密码 (留空则不修改)' : '登录密码'}
            rules={editingUser ? [] : [{ required: true, message: '请输入密码' }]}
          >
            <Input.Password placeholder="密码" />
          </Form.Item>
          <Form.Item name="roleIds" label="分配角色">
            <Select
              mode="multiple"
              placeholder="请选择赋予的角色"
              options={roles.map((r) => ({ label: r.roleName, value: r.roleId }))}
            />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input placeholder="user@example.com" />
          </Form.Item>
          <Form.Item name="status" label="账号状态">
            <Select
              options={[
                { label: '正常启用', value: '0' },
                { label: '停用封禁', value: '1' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};
