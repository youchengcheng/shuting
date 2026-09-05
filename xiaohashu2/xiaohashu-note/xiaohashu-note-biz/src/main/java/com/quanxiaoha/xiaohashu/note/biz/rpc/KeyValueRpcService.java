package com.quanxiaoha.xiaohashu.note.biz.rpc;


import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.kv.api.KeyValueFeignApi;
import com.quanxiaoha.xiaohashu.kv.dto.rep.AddNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rep.DeleteNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.rep.FindNoteContentReqDTO;
import com.quanxiaoha.xiaohashu.kv.dto.resp.FindNoteContentRspDTO;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class KeyValueRpcService {

    @Resource
    private KeyValueFeignApi keyValueFeignApi;

    /*
    * 保存笔记
    * */
    public boolean saveNoteContent(String uuid,String content){

        AddNoteContentReqDTO addNoteContentReqDTO = new AddNoteContentReqDTO();

        addNoteContentReqDTO.setUuid(uuid);
        addNoteContentReqDTO.setContent(content);

        Response<?> response = keyValueFeignApi.addNoteContent(addNoteContentReqDTO);

        if(Objects.isNull(response) || !response.isSuccess()){
            return false;
        }
        return true;
    }

    /*
    * 删除笔记
    * */
    public boolean deleteNoteContent(String uuid){
        DeleteNoteContentReqDTO deleteNoteContentReqDTO = new DeleteNoteContentReqDTO();

        deleteNoteContentReqDTO.setUuid(uuid);

        Response<?> response = keyValueFeignApi.deleteNoteContent(deleteNoteContentReqDTO);

        if(Objects.isNull(response) || !response.isSuccess()){
            return false;
        }
        return true;
    }

    /*
    * 根据笔记id查询笔记内容
    * */
    public String findNoteContent(String uuid){
        FindNoteContentReqDTO findNoteContentReqDTO = new FindNoteContentReqDTO();
        findNoteContentReqDTO.setUuid(uuid);

        Response<FindNoteContentRspDTO> response = keyValueFeignApi.findNoteContent(findNoteContentReqDTO);

        //Objects.isNull(response.getData()) 因为response.getData()获取到的是对象，也就
        //是FindNoteContentRspDTO对象，所以这里使用Objects进行判断是否为空
        if(Objects.isNull(response) || !response.isSuccess() || Objects.isNull(response.getData())){
            return null;
        }
        //返回笔记内容
        return response.getData().getContent();
    }
}
