package com.quanxiaoha.ai.robot.model.vo.chat;

import com.quanxiaoha.ai.robot.model.commen.BasePageQuery;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 查询对话历史消息
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindChatHistoryMessagePageListReqVO extends BasePageQuery {

    @NotBlank(message = "对话 ID 不能为空")
    private String chatId;
}

