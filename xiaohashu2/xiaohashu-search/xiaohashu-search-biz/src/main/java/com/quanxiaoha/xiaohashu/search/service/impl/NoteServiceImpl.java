package com.quanxiaoha.xiaohashu.search.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.quanxiaoha.framework.common.constant.DateConstants;
import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.framework.common.util.DateUtils;
import com.quanxiaoha.framework.common.util.NumberUtils;
import com.quanxiaoha.xiaohashu.search.domain.mapper.SelectMapper;
import com.quanxiaoha.xiaohashu.search.dto.RebuildNoteDocumentReqDTO;
import com.quanxiaoha.xiaohashu.search.enums.NotePublishTimeRangeEnum;
import com.quanxiaoha.xiaohashu.search.enums.NoteSortTypeEnum;
import com.quanxiaoha.xiaohashu.search.index.NoteIndex;
import com.quanxiaoha.xiaohashu.search.model.vo.SearchNoteReqVO;
import com.quanxiaoha.xiaohashu.search.model.vo.SearchNoteRspVO;
import com.quanxiaoha.xiaohashu.search.service.NoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Resource
    private RestHighLevelClient restHighLevelClient;
    @Resource
    private SelectMapper selectMapper;

    /*
    * 搜索笔记
    * */
    @Override
    public PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO) {
        //查询关键字
        String keyword = searchNoteReqVO.getKeyword();
        //当前页码
        Integer pageNo = Objects.requireNonNullElse(searchNoteReqVO.getPageNo(), 1);
        //笔记类型
        Integer type = searchNoteReqVO.getType();
        // 排序类型
        Integer sort = searchNoteReqVO.getSort();
        //发布时间范围
        Integer publishTimeRange = searchNoteReqVO.getPublishTimeRange();

        //构建 SearchRequest，指定要查询的索引
        SearchRequest searchRequest = new SearchRequest(NoteIndex.NAME);

        //创建查询构造器
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        //构建查询条件
//        "query": {
//            "function_score": {
//                "query": {
//                    "multi_match": {
//                        "query": "壁纸",
//                        "fields": ["title^2", "topic"]
//                    }
//                },
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().must(
                QueryBuilders.multiMatchQuery(keyword)
                        .field(NoteIndex.FIELD_NOTE_TITLE, 2.0f)//设置权重为2.0
                        .field(NoteIndex.FIELD_NOTE_TOPIC)//不设置默认为1.0
        );

        //若勾选了笔记类型，添加过滤条件
        if(Objects.nonNull(type)){
            boolQueryBuilder.filter(QueryBuilders.termQuery(NoteIndex.FIELD_NOTE_TYPE, type));
        }

        // 按发布时间范围过滤
        NotePublishTimeRangeEnum notePublishTimeRangeEnum = NotePublishTimeRangeEnum.valueOf(publishTimeRange);

        if(Objects.nonNull(notePublishTimeRangeEnum)){
            // 结束时间
            String endTime = LocalDateTime.now().format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
            // 开始时间
            String startTime = null;

            switch (notePublishTimeRangeEnum) {
                case DAY ->
                        startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusDays(1)); // 一天之前的时间
                case WEEK ->
                        startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusWeeks(1)); // 一周之前的时间
                case HALF_YEAR ->
                        startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusMonths(6)); // 半年之前的时间
            }
            // 设置时间范围
            if (StringUtils.isNoneBlank(startTime)) {
                boolQueryBuilder.filter(QueryBuilders.rangeQuery(NoteIndex.FIELD_NOTE_CREATE_TIME)
                        .gte(startTime) // 大于等于
                        .lte(endTime) // 小于等于
                );
            }
        }

        //排序
        NoteSortTypeEnum noteSortTypeEnum = NoteSortTypeEnum.valueOf(sort);

        // 设置排序
        // "sort": [
        //     {
        //       "_score": {
        //         "order": "desc"
        //       }
        //     }
        //   ]

        if (Objects.nonNull(noteSortTypeEnum)){
            switch (noteSortTypeEnum){
                // 按笔记发布时间降序
                case LATEST -> sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_CREATE_TIME).order(SortOrder.DESC));
                // 按笔记点赞量降序
                case MOST_LIKE -> sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL).order(SortOrder.DESC));
                // 按评论量降序
                case MOST_COMMENT -> sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL).order(SortOrder.DESC));
                // 按收藏量降序
                case MOST_COLLECT -> sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL).order(SortOrder.DESC));
            }
            //设置查询
            sourceBuilder.query(boolQueryBuilder);
        }else {
            // 综合排序，自定义评分，并按 _score 评分降序
            sourceBuilder.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));

             /*创建 FilterFunctionBuilder 数组
             "functions": [
                     {
                       "field_value_factor": {
                         "field": "like_total",
                         "factor": 0.5,
                         "modifier": "sqrt",
                         "missing": 0
                       }
                     },
                     {
                       "field_value_factor": {
                         "field": "collect_total",
                         "factor": 0.3,
                         "modifier": "sqrt",
                         "missing": 0
                       }
                     },
                     {
                       "field_value_factor": {
                         "field": "comment_total",
                         "factor": 0.2,
                         "modifier": "sqrt",
                         "missing": 0
                       }
                     }
                   ],*/
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionBuilders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                    //function 1
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL)
                                    .factor(0.5f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    ),
                    //function 2
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL)
                                    .factor(0.3f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    ),
                    //function 3
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL)
                                    .factor(0.2f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    ),
            };

            // 构建 function_score 查询
            // "score_mode": "sum",
            // "boost_mode": "sum"
            FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(boolQueryBuilder, filterFunctionBuilders)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                    .boostMode(CombineFunction.SUM);

            //设置查询
            sourceBuilder.query(functionScoreQueryBuilder);
        }


        //设置分页
        int pageSize = 10;
        int from = (pageNo - 1) * pageSize;
        sourceBuilder.from(from);
        sourceBuilder.size(pageSize);

        //设置高亮字段
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(NoteIndex.FIELD_NOTE_TITLE)
                .preTags("<strong>")
                .postTags("</strong>");
        sourceBuilder.highlighter(highlightBuilder);

        //将构建的查询条件设置到 SearchRequest 中
        searchRequest.source(sourceBuilder);

        //反参 VO 集合
        List<SearchNoteRspVO> searchNoteRspVOS = Lists.newArrayList();
        //总文档数，默认为0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest.source().toString());

            //执行搜索
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            //处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);

            //获取搜素命中的文档列表
            SearchHits hits = searchResponse.getHits();

            for (SearchHit hit : hits) {
                log.info("==> 文档数据: {}", hit.getSourceAsString());
                //所有字段以map进行返回
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();

                // 提取特定字段值
                Long noteId = getLong(sourceAsMap, NoteIndex.FIELD_NOTE_ID, "note_id");
                String cover = getString(sourceAsMap, NoteIndex.FIELD_NOTE_COVER);
                String title = getString(sourceAsMap, NoteIndex.FIELD_NOTE_TITLE);
                String avatar = getString(sourceAsMap, NoteIndex.FIELD_NOTE_AVATAR, "creator_avatar");
                String nickname = getString(sourceAsMap, NoteIndex.FIELD_NOTE_NICKNAME, "creator_nickname");
                String topicName = getString(sourceAsMap, "topic_name", NoteIndex.FIELD_NOTE_TOPIC);
                String imgUris = getString(sourceAsMap, "img_uris");
                String videoUri = getString(sourceAsMap, "video_uri");
                Long creatorId = getLong(sourceAsMap, "creator_id", "creatorId");
                Integer noteType = getInteger(sourceAsMap, NoteIndex.FIELD_NOTE_TYPE);
                // 获取更新时间
                String updateTimeStr = getString(sourceAsMap, NoteIndex.FIELD_NOTE_UPDATE_TIME,
                        NoteIndex.FIELD_NOTE_CREATE_TIME);
                LocalDateTime updateTime = parseDateTime(updateTimeStr);
                Integer likeTotal = getInteger(sourceAsMap, NoteIndex.FIELD_NOTE_LIKE_TOTAL);
                Integer commentTotal = getInteger(sourceAsMap, NoteIndex.FIELD_NOTE_COMMENT_TOTAL);
                Integer collectTotal = getInteger(sourceAsMap, NoteIndex.FIELD_NOTE_COLLECT_TOTAL);

                //获取高亮字段
                String highlightedTitle = null;
                //containsKey() 判断 title 这个字段有没有被高亮
                if(CollUtil.isNotEmpty(hit.getHighlightFields())
                        && hit.getHighlightFields().containsKey(NoteIndex.FIELD_NOTE_TITLE)){
                    highlightedTitle = hit.getHighlightFields().get(NoteIndex.FIELD_NOTE_TITLE).fragments()[0].string();
                }


                // 构建 VO 实体类
                SearchNoteRspVO searchNoteRspVO = SearchNoteRspVO.builder()
                        .noteId(noteId)
                        .id(noteId)
                        .creatorId(creatorId)
                        .type(noteType)
                        .imgUris(imgUris)
                        .videoUri(videoUri)
                        .topicName(topicName)
                        .cover(cover)
                        .title(title)
                        .highlightTitle(highlightedTitle)
                        .avatar(avatar)
                        .nickname(nickname)
                        .updateTime(DateUtils.formatRelativeTime(updateTime))
                        .likeTotal(NumberUtils.formatNumberString(likeTotal))
                        .collectTotal(NumberUtils.formatNumberString(collectTotal))
                        .commentTotal(NumberUtils.formatNumberString(commentTotal))
                        .build();
                searchNoteRspVOS.add(searchNoteRspVO);
            }

        }catch (Exception e){
            log.error("==> 查询 Elasticserach 异常: ", e);
        }
        return PageResponse.success(searchNoteRspVOS, pageNo, total);
    }

    private String getString(Map<String, Object> source, String key, String... fallbackKeys) {
        Object value = source.get(key);
        if (value != null && StringUtils.isNotBlank(value.toString())) {
            return value.toString();
        }
        for (String fallbackKey : fallbackKeys) {
            value = source.get(fallbackKey);
            if (value != null && StringUtils.isNotBlank(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private Long getLong(Map<String, Object> source, String key, String... fallbackKeys) {
        Object value = source.get(key);
        if (value == null) {
            for (String fallbackKey : fallbackKeys) {
                value = source.get(fallbackKey);
                if (value != null) {
                    break;
                }
            }
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    private Integer getInteger(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Number ? ((Number) value).intValue()
                : (value == null ? 0 : Integer.valueOf(value.toString()));
    }

    private LocalDateTime parseDateTime(String value) {
        if (StringUtils.isBlank(value)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value, DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignoredAgain) {
                return LocalDateTime.now();
            }
        }
    }

    /*
    * 重建笔记文档
    * */
    @Override
    public Response<Long> rebuildDocument(RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO) {
        //获取笔记id
        Long noteId = rebuildNoteDocumentReqDTO.getId();

        //从数据库中查询数据，同步到 ES 中
        List<Map<String, Object>> result = selectMapper.selectEsNoteIndexData(noteId, null);
        
        //遍历查询结果，将每条记录同步到 ES 中
        for (Map<String, Object> recordMap : result) {
            //创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
            //设置文档的id，使用记录中的主键 id 字段值
            indexRequest.id(String.valueOf(recordMap.get(NoteIndex.FIELD_NOTE_ID)));
            //设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recordMap);
            //将数据写入 ES 中
            try {
                restHighLevelClient.index(indexRequest,RequestOptions.DEFAULT);
            }catch (Exception e){
                log.error("==> 重建笔记文档失败: ", e);
            }
        }
        return Response.success();
    }
}
