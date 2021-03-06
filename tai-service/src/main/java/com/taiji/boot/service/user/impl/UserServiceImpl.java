package com.taiji.boot.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.taiji.boot.dal.base.user.entity.UserEntity;
import com.taiji.boot.dal.base.user.mapper.UserMapper;
import com.taiji.boot.service.user.UserService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public int insertUser(UserEntity userEntity) {
        return userMapper.insert(userEntity);
    }

    @Override
    public UserEntity getUserById(Integer userId) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("id", userId);
        return userMapper.selectList(wrapper).get(0);
    }

    @Override
    public UserEntity getUserByLoginName(String loginName) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("login_name", loginName);
        return userMapper.selectOne(wrapper);
    }

    @Override
    public List<UserEntity> getUserByWrapper(QueryWrapper<UserEntity> wrapper) {
        return userMapper.selectList(wrapper);
    }

    @Override
    public long countUserByWrapper(QueryWrapper<UserEntity> wrapper) {
        return userMapper.selectCount(wrapper);
    }

}
