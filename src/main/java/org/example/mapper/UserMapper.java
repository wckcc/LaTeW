package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.dto.UserDTO;
import java.util.List;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper {
    /**
     * 插入用户
     */
    int insert(UserDTO userDTO);

    /**
     * 根据ID更新用户
     */
    int updateById(UserDTO userDTO);

    /**
     * 根据ID删除用户
     */
    int deleteById(Long id);

    /**
     * 根据ID查询用户
     */
    UserDTO selectById(Long id);

    /**
     * 根据用户名查询用户
     */
    UserDTO selectByUsername(String username);

    /**
     * 根据邮箱查询用户
     */
    UserDTO selectByEmail(String email);

    /**
     * 查询所有用户
     */
    List<UserDTO> selectAll();

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);
}

