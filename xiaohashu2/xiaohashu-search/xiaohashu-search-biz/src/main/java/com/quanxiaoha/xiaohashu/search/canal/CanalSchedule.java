package com.quanxiaoha.xiaohashu.search.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.google.common.collect.Maps;
import com.quanxiaoha.framework.common.enums.StatusEnum;
import com.quanxiaoha.xiaohashu.search.domain.mapper.SelectMapper;
import com.quanxiaoha.xiaohashu.search.enums.NoteStatusEnum;
import com.quanxiaoha.xiaohashu.search.enums.NoteVisibleEnum;
import com.quanxiaoha.xiaohashu.search.index.NoteIndex;
import com.quanxiaoha.xiaohashu.search.index.UserIndex;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/*
* 数据消费
* */
@Component
@Slf4j
public class CanalSchedule implements Runnable {

    @Resource
    private CanalProperties canalProperties;
    @Resource
    private CanalConnector canalConnector;
    @Resource
    private SelectMapper selectMapper;
    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Override
    @Scheduled(fixedDelay = 100)
    public void run() {
        // 初始化批次 ID，-1 表示未开始或未获取到数据
        long batchId = -1;
        try {
            // 从 canalConnector 获取批量消息，返回的数据量由 batchSize 控制，若不足，则拉取已有的
            Message message = canalConnector.getWithoutAck(canalProperties.getBatchSize());

            // 获取当前拉取消息的批次 ID
            batchId = message.getId();

            // 获取当前批次中的数据条数
            long size = message.getEntries().size();
            if (batchId == -1 || size == 0) {
                try {
                    // 拉取数据为空，休眠 1s, 防止频繁拉取
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {}
            } else {
                // 如果当前批次有数据，打印这批次中的数据条目
                processEntry(message.getEntries());
            }

            // 对当前批次的消息进行 ack 确认，表示该批次的数据已经被成功消费
            canalConnector.ack(batchId);
        } catch (Exception e) {
            log.error("消费 Canal 批次数据异常", e);
            // 如果出现异常，需要进行数据回滚，以便重新消费这批次的数据
            canalConnector.rollback(batchId);
        }
    }

    /**
     * 处理这一批次数据
     */
    public void processEntry(List<CanalEntry.Entry> entries) throws Exception {
        //循环处理
        for (CanalEntry.Entry entry : entries) {
            //只处理rowdata行数据类型的entry，忽略事物等其他类型
            if(entry.getEntryType() == CanalEntry.EntryType.ROWDATA){
                //获取事件类型
                CanalEntry.EventType eventType = entry.getHeader().getEventType();
                //获取数据库名称
                String database = entry.getHeader().getSchemaName();
                //获取表名称
                String table = entry.getHeader().getTableName();

                //解析出rowChange对象，包含rowData和事件相关信息
                CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());

                //变量所有行数据（RowData）
                for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                    // 删除事件没有 after 列，必须使用 before 列获取主键；更新事件优先使用 after 列。
                    List<CanalEntry.Column> columns = eventType == CanalEntry.EventType.DELETE
                            ? rowData.getBeforeColumnsList() : rowData.getAfterColumnsList();

                    //将列数据解析为Map，方便后续处理
                    Map<String, Object> columnMap = parseColumns2Map(columns);

                    // TODO: 自定义处理
                    log.info("EventType: {}, Database: {}, Table: {}, Columns: {}", eventType, database, table, columnMap);

                    // 处理事件
                    processEvent(columnMap, table, eventType);
                }
            }
        }
    }

    /*
    * 处理事件
    * */
    private void processEvent(Map<String, Object> columnMap, String table, CanalEntry.EventType eventType) throws IOException {
        switch (table){
            case "t_note" -> handleNoteEvent(columnMap,eventType);//笔记表
            case "t_user" -> handleUserEvent(columnMap,eventType);//用户表
            case "t_note_count" -> handleNoteCountEvent(columnMap);//笔记计数表
            default -> log.warn("Table: {} not support", table);
        }


    }

    /*
    *  处理用户表事件
    * */
    private void handleUserEvent(Map<String, Object> columnMap, CanalEntry.EventType eventType) throws IOException {
        //获取用户id
        long userId = Long.parseLong(columnMap.get("id").toString());
        //不同的事件，处理逻辑不同
        switch (eventType){
            case INSERT -> syncUserIndex(userId);//记录新增事件
            case UPDATE -> {//记录更新事件
                // 用户变更后的状态
                Integer status = parseInteger(columnMap.get("status"));
                // 逻辑删除
                Integer isDeleted = parseInteger(columnMap.get("is_deleted"));

                if (status == null || isDeleted == null) {
                    syncNotesIndexAndUserIndex(userId);
                } else if (Objects.equals(status, StatusEnum.ENABLE.getValue())
                        && Objects.equals(isDeleted, 0)) { // 用户状态为已启用，并且未被逻辑删除
                    // 更新用户索引、笔记索引
                    syncNotesIndexAndUserIndex(userId);
                } else if (Objects.equals(status, StatusEnum.DISABLED.getValue())//用户状态为禁用
                         || Objects.equals(isDeleted,1)) {//被逻辑删除
                    //删除用户文档
                    deleteUserDocument(String.valueOf(userId));
                }
            }
            case DELETE -> deleteUserDocument(String.valueOf(userId));
            default -> log.warn("Unhandled event type for t_user: {}", eventType);
        }
    }

    /*
    * 处理笔记表事件
    * */
    private void handleNoteEvent(Map<String, Object> columnMap, CanalEntry.EventType eventType) throws IOException {
        Object noteIdValue = columnMap.get("id");
        if (noteIdValue == null) {
            log.warn("Canal 笔记事件缺少 id，忽略事件: {}, {}", eventType, columnMap);
            return;
        }
        long noteId = Long.parseLong(noteIdValue.toString());

        //不同的事件，处理逻辑不同
        switch (eventType){
            case INSERT -> syncNoteIndex(noteId);//记录新增事件
            case DELETE -> deleteNoteDocument(String.valueOf(noteId));
            case UPDATE -> {//记录更新事件
                //获取笔记变更后的状态
                Integer status = parseInteger(columnMap.get("status"));
                //获取可见范围
                Integer visible = parseInteger(columnMap.get("visible"));

                //判断
                if (status == null || visible == null) {
                    syncNoteIndex(noteId);
                } else if (Objects.equals(visible, NoteVisibleEnum.PUBLIC.getCode()) &&
                        Objects.equals(status, NoteStatusEnum.NORMAL.getCode())){ //正常展示，并且可见性为公开
                    syncNoteIndex(noteId); //对索引进行覆盖更新
                } else if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode()) //仅对自己可见
                        || Objects.equals(status,NoteStatusEnum.DELETED.getCode()) //被逻辑删除
                        || Objects.equals(status,NoteStatusEnum.DOWNED.getCode())) { //被下架
                    //删除笔记文档
                    deleteNoteDocument(String.valueOf(noteId));
                }
            }
            default -> log.warn("Unhandled event type for t_note: {}", eventType);
        }

    }

    /*
    * 删除指定id的笔记文档
    * */
    private void deleteNoteDocument(String documentId) throws IOException {
        // 创建删除请求对象，指定索引名称和文档 ID
        DeleteRequest deleteRequest = new DeleteRequest(NoteIndex.NAME, documentId);
        // 执行删除操作，将指定文档从 Elasticsearch 索引中删除
        restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    /*
     *  删除用户文档
     * */
    private void deleteUserDocument(String documentId) throws IOException {
        // 创建删除请求对象，指定索引名称和文档 ID
        DeleteRequest deleteRequest = new DeleteRequest(UserIndex.NAME, documentId);
        // 执行删除操作，将指定文档从 Elasticsearch 索引中删除
        restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    /*
    * 更新用户索引、笔记索引
    * */
    private void syncNotesIndexAndUserIndex(long userId) throws IOException {
        //创建一个 BulkRequest --------- BulkRequest 用于批量处理对es发起的请求
        BulkRequest bulkRequest = new BulkRequest();

        //1.用户索引
        List<Map<String, Object>> userResult = selectMapper.selectEsUserIndexData(userId);

        //变量查询结果，将每条记录同步到 Elasticsearch
        for (Map<String, Object> recordMap : userResult) {
            //创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
            //设置文档的id，使用记录中的主键 id 字段值
            indexRequest.id(String.valueOf(recordMap.get(UserIndex.FIELD_USER_ID)));
            //设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recordMap);
            //将每一个 indexRequest 加入到 bulkRequest
            bulkRequest.add(indexRequest);
        }

        //2.笔记索引
        List<Map<String, Object>> noteResult = selectMapper.selectEsNoteIndexData(null, userId);

        //变量查询结果，将每条记录同步到 Elasticsearch
        for (Map<String, Object> recordMap : noteResult) {
            //创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
            //设置文档的id，使用记录中的主键 id 字段值
            indexRequest.id(String.valueOf(recordMap.get(NoteIndex.FIELD_NOTE_ID)));
            //设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recordMap);
            //将每一个 indexRequest 加入到 bulkRequest
            bulkRequest.add(indexRequest);
        }
        //执行批量请求
        restHighLevelClient.bulk(bulkRequest,RequestOptions.DEFAULT);
    }

    /*
    * 同步笔记索引
    * */
    private void syncNoteIndex(long noteId) throws IOException {
        //从数据库中查询 Elasticsearch 索引数据
        List<Map<String, Object>> result = selectMapper.selectEsNoteIndexData(noteId,null);

        if (result == null || result.isEmpty()) {
            deleteNoteDocument(String.valueOf(noteId));
            return;
        }

        //变量查询结果，将每条记录同步到 Elasticsearch
        for (Map<String, Object> recordMap : result) {
            //创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
            //设置文档的id，使用记录中的主键 id 字段值
            indexRequest.id(String.valueOf(recordMap.get(NoteIndex.FIELD_NOTE_ID)));
            //设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recordMap);
            //将数据写入 Elasticsearch 索引
            restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        }
    }


    /*
    * 同步用户索引
    * */
    private void syncUserIndex(long userId) throws IOException {
        //1.同步用户索引
        List<Map<String, Object>> userResult = selectMapper.selectEsUserIndexData(userId);

        //变量查询结果，将每条记录同步到 elasticsearch
        for (Map<String, Object> recorMap : userResult) {
            //创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
            //设置文档的id，使用记录中的主键“id” 字段值
            indexRequest.id(String.valueOf(recorMap.get(UserIndex.FIELD_USER_ID)));
            //设置文档的内容，使用查询结果的记录数据
            indexRequest.source(recorMap);
            //将数据写入 elasticsearch 索引
            restHighLevelClient.index(indexRequest,RequestOptions.DEFAULT);
        }
        

        
    }

    /**
     * 将列数据解析为 Map
     * @param columns
     * @return
     */
    private Map<String, Object> parseColumns2Map(List<CanalEntry.Column> columns) {
        Map<String, Object> map = Maps.newHashMap();
        columns.forEach(column -> {
            if (Objects.isNull(column)) return;
            map.put(column.getName(), column.getValue());
        });
        return map;
    }

    /**
     * t_note_count 由点赞、收藏、评论等异步消费者更新。计数表变更时重新查询完整笔记数据，
     * 覆盖 ES 文档，避免搜索排序和展示计数长期使用旧值。
     */
    private void handleNoteCountEvent(Map<String, Object> columnMap) throws IOException {
        Object noteIdValue = columnMap.get("note_id");
        if (noteIdValue == null) {
            log.warn("Canal 笔记计数事件缺少 note_id，忽略事件: {}", columnMap);
            return;
        }
        syncNoteIndex(Long.parseLong(noteIdValue.toString()));
    }

    private Integer parseInteger(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Integer.valueOf(value.toString());
    }
}
