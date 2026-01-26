package com.supremind.event.controller.event;


import cn.hutool.core.date.DatePattern;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.supremind.common.result.ResultResponse;
import com.supremind.event.business.event.BoxEventBusinessService;
import com.supremind.event.business.event.HeadFlowEventBusinessService;
import com.supremind.event.business.event.PlateformEventBusinessService;
import com.supremind.event.constant.HeadFlowCountConstants;
import com.supremind.event.constant.TerminalRedisKey;
import com.supremind.event.dto.event.AnalyzerUploadDto;
import com.supremind.event.dto.event.EventDto;
import com.supremind.event.dto.event.ReviewDto;
import com.supremind.event.enums.EventSource;
import com.supremind.redis.common.RedisUtils;
import com.supremind.s3.config.S3Config;
import com.supremind.s3.service.S3Service;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 事件上报
 */
@Slf4j
@RestController
@RequestMapping("/report")
public class EventReportedController {

    @Autowired
    private PlateformEventBusinessService plateformEventBusinessService;
    @Autowired
    private BoxEventBusinessService boxEventBusinessService;
    @Autowired
    private HeadFlowEventBusinessService headFlowEventBusinessService;
    @Autowired
    private S3Service s3Service;
    @Autowired
    private S3Config s3Config;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private RedisTemplate redisTemplate;

    @ApiOperation("事件数据上报")
    @PostMapping("/event")
    public ResultResponse listEvent(@RequestBody EventDto report) {
        if (null == report){
            log.info("report event is null");
            return ResultResponse.fail("event data must be not null!");
        }
        if (StringUtils.isEmpty(report.getEventType())){
            log.info("report event not have eventType");
            return ResultResponse.fail("report data eventType must be not null!");
        }
        if (StringUtils.isNotEmpty(report.getEngineEventId()) && null != redisUtils.get(report.getEngineEventId())){
            log.info("event report duplication======{}",report.getEngineEventId());
            return ResultResponse.fail("event duplication!");
        }
        ResultResponse response = boxEventBusinessService.handle(report);
        return response;
    }

    /**
     * @param dto
     * @param supplier
     * @param consumer
     */
    private void afterReportEventSuccess(EventDto dto, Supplier<Boolean> supplier, Consumer<EventDto> consumer) {
        boolean reportOk = supplier.get();
        if (reportOk) {
            consumer.accept(dto);
        }
    }

    /**
     * 引擎产生事件，原图上传地址
     * @param dto
     * @return
     */
    @ApiOperation("引擎图片上传")
    @PostMapping("/analyzer/upload/stream")
    public ResultResponse<String> uploadStream(@RequestBody AnalyzerUploadDto dto) {

        log.info("analyzer upload start========{}",dto.getFileName());
        String url = "";
        try {
            String base64 = dto.getBase64();
            if (StringUtils.isEmpty(base64)){
                return ResultResponse.fail("base64 is not null");
            }
            String objectName = s3Service.createObjectName(dto.getFileName(), "", null, false);
            url = s3Service.base64Upload(objectName, base64, s3Config.getDefaultBucket(),null);
//            StringBuffer sbr = new StringBuffer(url);
//            url =  sbr.append("/").append(objectName).toString();
            log.info("analyzer upload end========{}",url);
        }catch (Exception e){
            log.error("analyzer upload Exception fail===={}",e.getMessage());
        }
        return ResultResponse.okWithData(url);
    }

    @ApiOperation("dq事件回传")
    @PostMapping("/event/review")
    public  ResultResponse reviewMessage(@RequestBody ReviewDto dto){
        plateformEventBusinessService.receiveReviewMessage(dto);
        return ResultResponse.ok();
    }
}
