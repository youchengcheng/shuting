package com.quanxiaoha.xiaohashu.search.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.framework.common.util.NumberUtils;
import com.quanxiaoha.xiaohashu.search.domain.mapper.SelectMapper;
import com.quanxiaoha.xiaohashu.search.dto.RebuildUserDocumentReqDTO;
import com.quanxiaoha.xiaohashu.search.index.NoteIndex;
import com.quanxiaoha.xiaohashu.search.index.UserIndex;
import com.quanxiaoha.xiaohashu.search.model.vo.SearchUserReqVO;
import com.quanxiaoha.xiaohashu.search.model.vo.SearchUserRspVO;
import com.quanxiaoha.xiaohashu.search.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/*
* 用户搜索业务
* */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Resource
    private RestHighLevelClient restHighLevelClient;
    @Resource
    private SelectMapper selectMapper;

    /*
     * 搜索用户
     * */
    @Override
    public PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO) {
        //查询关键字
        String keyword = searchUserReqVO.getKeyword();
        //当前页码
        Integer pageNo = Objects.requireNonNullElse(searchUserReqVO.getPageNo(), 1);

        //构建 SearchRequest，指定索引
        SearchRequest searchRequest = new SearchRequest(UserIndex.NAME);

        //构建查询内容
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        //构建 multi_match 查询，查询nickname 和xiaohashu_id 字段
        sourceBuilder.query(QueryBuilders.multiMatchQuery(
                keyword,UserIndex.FIELD_USER_NICKNAME,UserIndex.FIELD_USER_XIAOHASHU_ID
        ));

        //排序，按fans_total降序
        FieldSortBuilder sortBuilder = new FieldSortBuilder(UserIndex.FIELD_USER_FANS_TOTAL)
                .order(SortOrder.DESC);
        sourceBuilder.sort(sortBuilder);

        //设置分页，from 和size
        int pageSize = 10;
        int from = (pageNo - 1) * pageSize;

        sourceBuilder.from(from);
        sourceBuilder.size(pageSize);

        //设置高亮字段
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(UserIndex.FIELD_USER_NICKNAME)
                .preTags("<strong>")
                .postTags("</strong>");
        sourceBuilder.highlighter(highlightBuilder);

        //将构建的查询条件设置到 SearchRequest 中
        searchRequest.source(sourceBuilder);

        //构建 VO 集合
        List<SearchUserRspVO> searchUserRspVOS = Lists.newArrayList();
        //总文档数，默认为0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest);

            //执行查询请求
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            //处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);

            //获取搜索命中的文档列表
            SearchHits hits = searchResponse.getHits();

            for (SearchHit hit : hits) {
                //获取文档的所有字段（以map的形式返回）
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                //提取特定字段
                Long userId = getLong(sourceAsMap, UserIndex.FIELD_USER_ID);
                String nickname = getString(sourceAsMap, UserIndex.FIELD_USER_NICKNAME);
                String avatar = getString(sourceAsMap, UserIndex.FIELD_USER_AVATAR);
                String xiaohashuId = getString(sourceAsMap, UserIndex.FIELD_USER_XIAOHASHU_ID);
                Integer noteTotal = getInteger(sourceAsMap, UserIndex.FIELD_USER_NOTE_TOTAL);
//                Integer fansTotal = (Integer) sourceAsMap.get(UserIndex.FIELD_USER_FANS_TOTAL);

                // 只修复粉丝数：为空则赋 0，避免空指针
                Integer fansTotal = 0;
                fansTotal = getInteger(sourceAsMap, UserIndex.FIELD_USER_FANS_TOTAL);

                //获取高亮字段
                String highlightNickname = null;
                //containsKey() 判断 nickname 这个字段有没有被高亮
                if(CollUtil.isNotEmpty(hit.getHighlightFields())
                        && hit.getHighlightFields().containsKey(UserIndex.FIELD_USER_NICKNAME)){
                    highlightNickname = hit.getHighlightFields().get(UserIndex.FIELD_USER_NICKNAME).fragments()[0].string();
                }
                
                //构建 VO
                SearchUserRspVO searchUserRspVO = SearchUserRspVO.builder()
                        .userId(userId)
                        .nickname(nickname)
                        .avatar(avatar)
                        .xiaohashuId(xiaohashuId)
                        .noteTotal(noteTotal)
                        .fansTotal(NumberUtils.formatNumberString(fansTotal))
                        .highlightNickname(highlightNickname)
                        .build();
                searchUserRspVOS.add(searchUserRspVO);
            }
        }catch (Exception e){
            log.error("==> 查询 Elasticserach 异常: ", e);
        }
        return PageResponse.success(searchUserRspVOS,pageNo,total);
    }

    private String getString(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? null : value.toString();
    }

    private Long getLong(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    private Integer getInteger(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.valueOf(value.toString());
    }

    /*
    * 重建用户文档
    * */
    @Override
    public Response<Long> rebuildDocument(RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO) {
        //获取用户id
        Long userId = rebuildUserDocumentReqDTO.getId();

        //从数据库中查询数据，同步到 ES 中
        List<Map<String, Object>> result = selectMapper.selectEsUserIndexData(userId);

        //遍历查询结果，将每条记录同步到 ES 中
        for (Map<String, Object> recordMap : result) {
            //创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
            //设置文档的id，使用记录中的主键 id 字段值
            indexRequest.id(String.valueOf(recordMap.get(UserIndex.FIELD_USER_ID)));
            //设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recordMap);
            //将数据写入 ES 中
            try {
                restHighLevelClient.index(indexRequest,RequestOptions.DEFAULT);
            }catch (Exception e){
                log.error("==> 重建用户文档失败: ", e);
            }
        }
        return Response.success();
    }
}
