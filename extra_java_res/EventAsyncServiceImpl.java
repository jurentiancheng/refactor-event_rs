package com.supremind.event.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.supremind.event.business.event.EventCommonBusinessService;
import com.supremind.event.cache.TaskCache;
import com.supremind.event.condition.camera.CameraCondition;
import com.supremind.event.config.PocConfig;
import com.supremind.event.dto.event.EventDto;
import com.supremind.event.entity.aiagent.AiAnalysisLogEntity;
import com.supremind.event.entity.camera.CameraEntity;
import com.supremind.event.entity.event.EventEntity;
import com.supremind.event.enums.EventSource;
import com.supremind.event.enums.MarkingType;
import com.supremind.event.enums.TaskType;
import com.supremind.event.helper.BeanHelper;
import com.supremind.event.job.CameraStreamStatusUpdateXxlJob;
import com.supremind.event.service.EventAsyncService;
import com.supremind.event.service.ICameraService;
import com.supremind.event.service.event.EventService;
import com.supremind.event.service.event.IAiAnalysisLogService;
import com.supremind.event.service.portal.EventGroupService;
import com.supremind.event.service.portal.EventLevelGroupService;
import com.supremind.event.utils.PictureCompressUtil;
import com.supremind.event.vo.algorithm.EventMapping;
import com.supremind.event.vo.configuration.BaseConfigVo;
import com.supremind.event.vo.portal.EventGroupDetailedAllVo;
import com.supremind.event.vo.portal.EventLevelGroupVo;
import com.supremind.event.vo.portal.UserEventGroupVo;
import com.supremind.project.message.center.feign.KafkaProducerFeign;
import com.supremind.s3.config.S3Config;
import com.supremind.s3.service.S3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @Author baiyonggang
 * @Date 2023/2/10 1:40 下午
 */
@Service
@Slf4j
public class EventAsyncServiceImpl implements EventAsyncService {
    @Autowired
    private EventService eventService;
    @Autowired
    private CameraStreamStatusUpdateXxlJob cameraStreamStatusUpdateXxlJob;
    @Autowired
    private EventLevelGroupService eventLevelGroupService;
    @Autowired
    private ICameraService cameraService;
    @Autowired
    private EventGroupService eventGroupService;
    @Autowired
    private KafkaProducerFeign kafkaProducerFeign;
    @Autowired
    private PocConfig pocConfig;
    @Autowired
    private S3Service s3Service;
    @Autowired
    private S3Config s3Config;
    @Autowired
    private IAiAnalysisLogService analysisLogService;
    @Autowired
    private TaskCache taskCache;

    @Override
    @Async("taskExecutor")
    public void snapshotUriAsync(BaseConfigVo baseConfigVo, EventDto dto, S3Service s3Service, S3Config s3Config) {
        PictureCompressUtil.compressSnapshotUri(baseConfigVo, dto, s3Service, s3Config);
        EventEntity entity = BeanHelper.convertObj(dto, EventEntity.class);
        eventService.updateById(entity);
    }

    @Override
    @Async("taskExecutor")
    public void snapshotUriRawAsync(BaseConfigVo baseConfigVo, EventDto dto, S3Service s3Service, S3Config s3Config) {
        PictureCompressUtil.compressSnapshotUriRaw(baseConfigVo, dto, s3Service, s3Config);
        EventEntity entity = BeanHelper.convertObj(dto, EventEntity.class);
        eventService.updateById(entity);
    }

    @Override
    @Async("taskExecutor")
    public void snapshotAsync(BaseConfigVo baseConfigVo, EventDto dto, S3Service s3Service, S3Config s3Config) {
        PictureCompressUtil.compressAndUploadToS3(baseConfigVo, dto, s3Service, s3Config);
        EventEntity entity = BeanHelper.convertObj(dto, EventEntity.class);
        eventService.updateById(entity);
        //只有违法事件推送至kafka违法topic
        if (MarkingType.event.code().equals(entity.getMarking())) {
            CameraCondition condition = new CameraCondition();
            condition.setCode(entity.getCameraCode());
            //根据小事件获取事件组名称
            EventGroupDetailedAllVo groupDetail = eventGroupService.getEventGroupDetailForGroup(entity.getEventType());
            log.info("groupDetail====={}", JSON.toJSONString(groupDetail));
            if (null != groupDetail) {
                entity.setEventTypeName(groupDetail.getEventTypeGroupName());
            }


            Long projectId = entity.getProjectId();
            if (pocConfig != null) {
                List<EventMapping> eventMappingList = pocConfig.getEventMappingList();
                if (eventMappingList != null) {
                    for (EventMapping eventMapping : eventMappingList) {
                        if (eventMapping.getProjectId().equals(projectId)&&eventMapping.getReplaceFromDetailCode().equals(entity.getEventType())) {
                            entity.setEventTypeName(eventMapping.getReplaceToGroupName());
                        }
                    }
                }
            }

            //JSONObject object = JSONObject.parseObject(JSON.toJSONString(redisTemplate.opsForValue().get(RedisKeyConstant.GLOBAL_EVENT_GROUP)));
            //entity.setEventTypeName(object.getString(entity.getEventType()));
            JSONObject jsonObject = JSONObject.parseObject(JSON.toJSONString(entity));
            //查询事件组的等级和颜色设置
            List<EventLevelGroupVo> levelGroupVos = eventLevelGroupService.listEventLevelGroup(entity.getProjectId());
            if (CollectionUtil.isNotEmpty(levelGroupVos)) {
                levelGroupVos.stream().forEach(eventLevelGroupVo -> {
                    List<UserEventGroupVo> eventGroupVoList = eventLevelGroupVo.getEventGroupVoList();
                    if (CollectionUtil.isNotEmpty(eventGroupVoList)) {
                        eventGroupVoList.stream().forEach(UserEventGroupVo -> {
                            if (UserEventGroupVo.getEventTypeCodes().contains(entity.getEventType())) {
                                jsonObject.put("colour", eventLevelGroupVo.getColour());
                                return;
                            }
                        });
                    }
                });
            }
            if (!EventSource.manual_report.code().equals(entity.getSource())) {
                Optional.ofNullable(taskCache.get(entity.getTaskCode())).ifPresent(task -> {
                    if (TaskType.airBase.code().equals(task.getType())) {
                        String airTaskName = (String) JSONPath.eval(entity.getExtraData(), "$.eventResult.res_720000.dj_task_name");
                        log.info("无人机布控任务， 设置默认摄像头为无任务任务：{} ", airTaskName);
                        jsonObject.put("cameraCodeName", airTaskName);
                    } else {
                        String cName = Optional.ofNullable(cameraService.getDetail(condition))
                                .map(CameraEntity::getName)
                                .orElse("默认相机");
                        jsonObject.put("cameraCodeName", cName);
                    }
                });
            }
            // 未开启DQ时，数据直接推送至违法topic
            log.info("push kafka start ======");
            kafkaProducerFeign.produceEventMsg(entity.getProjectId(), jsonObject);
        }
    }

    /**
     * 在线状态工作
     */
    @Override
    @Async("taskExecutor")
    public void checkStreamStatus(int workerCount, List<CameraEntity> cameraEntities) {
        cameraStreamStatusUpdateXxlJob.checkStreamStatus(workerCount, cameraEntities);
    }

    @Override
    @Async("taskExecutor")
    public void push(Integer isOpenDq, BaseConfigVo baseConfig, EventDto data, S3Service s3Service, S3Config s3Config, EventCommonBusinessService commonBusinessService) {
        PictureCompressUtil.compressSnapshotUriRaw(baseConfig, data, s3Service, s3Config);
        EventEntity entity = commonBusinessService.pushDQOrSave(data, isOpenDq);
        data.setId(entity.getId());
        this.snapshotUriAsync(baseConfig, data, s3Service, s3Config);
    }

    @Async("taskExecutor")
    public void drawRangSnapshotUri(AiAnalysisLogEntity entity, Long eventId) {
        PictureCompressUtil.drawLargeModelDetectRangToSnapshotUrl(entity, s3Service, s3Config).ifPresent(snapshotUri -> {
            log.info("完成大模型检测区绘制后，上传成功到S3地址：{}", snapshotUri);
            AiAnalysisLogEntity logEntity = new AiAnalysisLogEntity();
            logEntity.setId(entity.getId());
            logEntity.setAiQuestionImage(snapshotUri);
            analysisLogService.updateById(logEntity);
        });
    }


}
