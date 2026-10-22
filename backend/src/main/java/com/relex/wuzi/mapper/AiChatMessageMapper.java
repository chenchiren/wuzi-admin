package com.relex.wuzi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.relex.wuzi.ai.domain.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
