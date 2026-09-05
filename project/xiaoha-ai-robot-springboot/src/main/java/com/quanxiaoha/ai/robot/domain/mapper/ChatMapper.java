package com.quanxiaoha.ai.robot.domain.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quanxiaoha.ai.robot.domain.dos.ChatDO;

import java.sql.Wrapper;

/*
* mapper接口
* */
public interface ChatMapper extends BaseMapper<ChatDO> {

    /*
    * 分页查询
    * */
    default Page<ChatDO> selectPageList(Long current, Long size) {
        //分页对象（查询第几页，每页查询多少数据）
        Page<ChatDO> page = new Page<>(current, size);

        //构建查询条件
        LambdaQueryWrapper<ChatDO> wrapper = Wrappers.<ChatDO>lambdaQuery()
                .orderByDesc(ChatDO::getUpdateTime);//按更新时间倒叙

        return selectPage(page,wrapper);
    }

}
