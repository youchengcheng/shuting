package com.quanxiaoha.xiaohashu.note.api;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.note.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@FeignClient(name = "xiaohashu-note", contextId = "noteInternalApi")
public interface NoteFeignApi {
    @PostMapping("/internal/note/metadata")
    Response<List<NoteMetadataDTO>> metadata(@RequestBody NoteBatchQueryReqDTO request);

    @PostMapping("/internal/note/previews")
    Response<List<NotePreviewDTO>> previews(@RequestBody NoteBatchQueryReqDTO request);
}
