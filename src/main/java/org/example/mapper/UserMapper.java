package org.example.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import org.example.dto.UserDTO;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper {
    /**
     * 插入用户
     */
    @Insert("insert into user (username, email, password_hash) values (#{username}, #{email}, #{password})")
    int insert(UserDTO userDTO);

    /**
     * 检查用户名是否存在
     */
    @Select("select COUNT(*) from user where username = #{username}")
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    @Select("select COUNT(*) from user where email = #{email}")
    boolean existsByEmail(String email);

    /**
     * 根据用户名查询用户
     */
    @Select("select user_id as id, username, email, password_hash as password, avatar_url as avatar, role from user where username = #{username}")
    UserDTO selectByUsername(String username);

    /**
     * 根据邮箱查询用户
     */
    @Select("select user_id as id, username, email, password_hash as password, avatar_url as avatar, role from user where email = #{email}")
    UserDTO selectByEmail(String email);

    /**
     * 更新用户头像
     */
    @Update("update user set avatar_url = #{avatar} where user_id = #{userId}")
    int updateAvatar(Long userId, String avatar);

    /**
     * 更新用户名
     */
    @Update("update user set username = #{username} where user_id = #{userId}")
    int updateUsername(@Param("userId") Long userId, @Param("username") String username);

    /**
     * 根据ID查询用户
     */
    @Select("select user_id as id, username, email, avatar_url as avatar, role from user where user_id = #{id}")
    UserDTO selectById(Long id);

    /**
     * 根据ID查询用户（含密码哈希，仅服务层校验用）
     */
    @Select("select user_id as id, username, email, password_hash as password, avatar_url as avatar, role from user where user_id = #{id}")
    UserDTO selectByIdWithPassword(Long id);

    /**
     * 更新密码
     */
    @Update("update user set password_hash = #{passwordHash} where user_id = #{userId}")
    int updatePasswordHash(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);
}

