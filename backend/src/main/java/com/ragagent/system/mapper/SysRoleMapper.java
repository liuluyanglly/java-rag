package com.ragagent.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragagent.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.* FROM sys_role r INNER JOIN sys_user_role ur ON r.role_id = ur.role_id WHERE ur.user_id = #{userId} AND r.del_flag = '0'")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);
}
