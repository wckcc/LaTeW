package org.example.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.dto.UserDTO;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper {
    /**
     * 插入用户
     */
    @Insert("insert into users (username, phone, password) values (#{username}, #{email}, #{password})")
    int insert(UserDTO userDTO);

    /**
     * 检查用户名是否存在
     */
    @Select("select COUNT(*) from users where username = #{username}")
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在（当前项目数据库列仍使用 phone 存储邮箱）
     */
    @Select("select COUNT(*) from users where phone = #{email}")
    boolean existsByEmail(String email);

    /**
     * 根据用户名查询用户
     */
    @Select("select id, username, phone as email, password, avatar from users where username = #{username}")
    UserDTO selectByUsername(String username);

    /**
     * 根据邮箱查询用户（当前项目数据库列仍使用 phone 存储邮箱）
     */
    @Select("select id, username, phone as email, password, avatar from users where phone = #{email}")
    UserDTO selectByEmail(String email);

    /**
     * 更新用户头像
     */
    @Update("update users set avatar = #{avatar} where id = #{userId}")
    int updateAvatar(Long userId, String avatar);

    /**
     * 根据ID查询用户
     */
    @Select("select id, username, phone as email, avatar from users where id = #{id}")
    UserDTO selectById(Long id);
}

