package org.example.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
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
    @Insert("insert into users (username, phone, password) values (#{username}, #{phone}, #{password})")
    int insert(UserDTO userDTO);

    /**
     * 检查用户名是否存在
     */
    @Select("select COUNT(*) from users where username = #{username}")
    boolean existsByUsername(String username);

    /**
     * 检查手机号是否存在
     */
    @Select("select COUNT(*) from users where phone = #{phone}")
    boolean existsByPhone(String phone);

    /**
     * 根据用户名查询用户
     */
    @Select("select id, username, phone, password from users where username = #{username}")
    UserDTO selectByUsername(String username);
}

