package com.example.aftersales.identity.mapper;

import com.example.aftersales.identity.domain.po.UserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 仅用于身份模块；调用者必须检查 enabled，不能直接将 PO 返回前端。 */
@Mapper
public interface UserMapper {
    UserPO findById(@Param("id") long id);
    UserPO findByUsername(@Param("username") String username);
}
