package com.quanxiaoha.xiaohashu.comment.biz.consumer;

import com.quanxiaoha.xiaohashu.comment.biz.domain.mapper.CommentLikeDOMapper;
import com.quanxiaoha.xiaohashu.comment.biz.enums.LikeUnlikeCommentTypeEnum;
import com.quanxiaoha.xiaohashu.comment.biz.model.dto.LikeUnlikeCommentMqDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

class LikeUnlikeComment2DBConsumerTest {
    private static final long USER_ID = 9L;
    private static final long COMMENT_ID = 100L;
    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 11, 17, 12, 0);
    private final CommentLikeDOMapper mapper = mock(CommentLikeDOMapper.class);
    private final LikeUnlikeComment2DBConsumer consumer = new LikeUnlikeComment2DBConsumer();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(consumer, "commentLikeDOMapper", mapper);
    }

    @Test void orderedLikeThenUnlikeDeletesTheFinalOperationDespiteAnEarlierTimestamp() {
        LikeUnlikeCommentMqDTO like = operation(LikeUnlikeCommentTypeEnum.LIKE, CREATE_TIME);
        LikeUnlikeCommentMqDTO unlike = operation(LikeUnlikeCommentTypeEnum.UNLIKE, CREATE_TIME.minusSeconds(1));

        consumer.processBatch(List.of(like, unlike));

        verify(mapper).batchDelete(List.of(unlike));
        verifyNoMoreInteractions(mapper);
    }

    @Test void orderedLikeUnlikeLikeInsertsTheFinalOperationDespiteAnEarlierTimestamp() {
        LikeUnlikeCommentMqDTO firstLike = operation(LikeUnlikeCommentTypeEnum.LIKE, CREATE_TIME);
        LikeUnlikeCommentMqDTO unlike = operation(LikeUnlikeCommentTypeEnum.UNLIKE, CREATE_TIME.plusSeconds(2));
        LikeUnlikeCommentMqDTO lastLike = operation(LikeUnlikeCommentTypeEnum.LIKE, CREATE_TIME.plusSeconds(1));

        consumer.processBatch(List.of(firstLike, unlike, lastLike));

        verify(mapper).batchInsert(List.of(lastLike));
        verifyNoMoreInteractions(mapper);
    }

    private LikeUnlikeCommentMqDTO operation(LikeUnlikeCommentTypeEnum type, LocalDateTime createTime) {
        return LikeUnlikeCommentMqDTO.builder()
                .userId(USER_ID)
                .commentId(COMMENT_ID)
                .type(type.getCode())
                .createTime(createTime)
                .build();
    }
}
