package com.supremind.event.business.event;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.supremind.base.utils.DateUtils;
import com.supremind.common.enums.IsDel;
import com.supremind.common.enums.KafkaPlatformTopic;
import com.supremind.common.result.ResultResponse;
import com.supremind.event.business.aiagent.AiAgentAnalysisBs;
import com.supremind.event.business.openapi.OpenDeviceBs;
import com.supremind.event.cache.BoxCache;
import com.supremind.event.config.FilterEventsConfig;
import com.supremind.event.config.SpecialEventConfig;
import com.supremind.event.dto.event.EventDto;
import com.supremind.event.dto.openapi.OpenSpecialLedCmdDto;
import com.supremind.event.entity.box.BoxLedEntity;
import com.supremind.event.entity.event.EventEntity;
import com.supremind.event.enums.AlgorithmOperateEnum;
import com.supremind.event.enums.MarkingType;
import com.supremind.event.enums.TaskType;
import com.supremind.event.helper.BeanHelper;
import com.supremind.event.service.EventAsyncService;
import com.supremind.event.service.box.IBoxLedService;
import com.supremind.event.service.event.EventService;
import com.supremind.event.service.task.SeniorEventService;
import com.supremind.event.vo.box.BoxCacheVo;
import com.supremind.event.vo.configuration.BaseConfigVo;
import com.supremind.event.vo.event.EventVo;
import com.supremind.event.vo.task.TaskCacheVo;
import com.supremind.project.message.center.feign.KafkaProducerFeign;
import com.supremind.redis.common.RedisUtils;
import com.supremind.s3.config.S3Config;
import com.supremind.s3.service.S3Service;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 盒子事件处理
 *
 * @author wuzhuhua
 */
@Service
@Slf4j
public class BoxEventBusinessService extends EventReportBusinessService {

    @Autowired
    private EventCommonBusinessService eventCommonBusinessService;
    @Autowired
    private S3Service s3Service;
    @Autowired
    private S3Config s3Config;
    @Autowired
    private EventService eventService;
    @Autowired
    private EventAsyncService eventAsyncService;
    @Autowired
    private FilterEventsConfig filterEventsConfig;
    private static final String REDIS_FILTER_EVENT_TYPE_KEY = "FILTER_EVENT_TYPE:";
    @Autowired
    private BoxCache boxCache;
    @Autowired
    private RedisUtils redisUtils;

    @Qualifier("nightConstructionEventServiceImpl")
    @Autowired
    private SeniorEventService seniorEventService;


    @Qualifier("shutDownConstructionEventServiceImpl")
    @Autowired
    private SeniorEventService shutDownConstructionSeniorEventService;

    @Autowired
    private SpecialEventConfig specialEventConfig;
    @Autowired
    private OpenDeviceBs openDeviceBs;
    @Autowired
    private IBoxLedService boxLedService;
    @Autowired
    private KafkaProducerFeign kafkaProducerFeign;
    @Autowired
    private AiAgentAnalysisBs aiAgentAnalysisBs;


    @Override
    public ResultResponse handle(EventDto data) {
        log.info("盒子侧事件接受,{}", JSONObject.toJSONString(data));
        long start = System.currentTimeMillis();
        String source = data.getSource();
        if (StringUtils.isEmpty(source)) {
            return ResultResponse.ok();
        }
        //处理事件布控相关信息
        TaskCacheVo taskCacheVo = eventCommonBusinessService.deposeBaseEventTaskData(data);
        if (taskCacheVo == null) {
            return ResultResponse.fail("task not find");
        }
        //截帧相关引擎在任务重试再运行后必触发，上报数据频率与设置时间间隔不一致
        //需求见 https://cf.supremind.info/pages/viewpage.action?pageId=102032366
        try {
            if (1 == filterEventsConfig.getIsOpen()) {
                String redis_key = REDIS_FILTER_EVENT_TYPE_KEY + "_" + data.getTaskCode() + "_" + data.getEventType();
                String[] split1 = filterEventsConfig.getEventType().split(",");
                if (null != split1 && split1.length > 0) {
                    //截帧间隔
                    List<String> evenTypes = Arrays.asList(split1);
                    //同一布控且需要过滤的算法匹配才开始判断是否需要过滤
                    if (evenTypes.contains(data.getEventType())) {
                        Long cooling_second = data.getExtraData().getJSONObject("originalConfig").getJSONArray("algList").getJSONObject(data.getOriginalViolationIndex()).getJSONObject("algParam").getLong("cooling_second");
                        log.info("filterEvent===start=={}", filterEventsConfig.getEventType());
                        //获取老的截帧间隔
                        String oldCoolingSecond = redisUtils.get(redis_key);
                        if (redisUtils.hasKey(redis_key)) {
                            String[] split = oldCoolingSecond.split("@");
                            if (Long.valueOf(split[0]).equals(cooling_second)) {
                                log.info("截帧事件未到冷却时间===丢弃==={}", data.getEngineEventId());
                                return null;
                            } else {
                                Date d = new Date(Long.parseLong(split[1]));
                                log.info("oldtime:{}", d);
                                long compareTime = com.supremind.common.utils.DateUtils.compareTime(data.getEventTime(), d, (cooling_second * 1000));
                                if (compareTime <= 0) {
                                    log.info("截帧事件未到更新后的冷却时间===丢弃==={}", data.getEngineEventId());
                                    return null;
                                }
                            }
                        }
                        long time = data.getEventTime().getTime() + cooling_second * 1000;
                        redisUtils.set(redis_key, cooling_second + "@" + data.getEventTime().getTime(), (time - System.currentTimeMillis()) / 1000);
                    }
                }
            }
        } catch (Exception e) {
            log.error("filter event fail:{}", e);
        }

        BaseConfigVo baseConfig = eventCommonBusinessService.deposeBaseEventBaseConfigData(data);
        //事件过滤
        if (eventCommonBusinessService.filterEvent(data)) {
            return saveFilteredEventAndReturn(baseConfig, data);
        }

        //无效数据直接落库
        if (MarkingType.unknown.code().equals(data.getMarking())) {
            EventEntity eventEntity = JSONObject.parseObject(String.valueOf(data), EventEntity.class);
            eventEntity.setCreateTime(new Date());
            eventEntity.setIsDel(IsDel.NORMAL.code());
            eventService.saveEvent(eventEntity);
            return ResultResponse.ok();
        }

        Integer isOpenDq = eventCommonBusinessService.personnelCheck(data, true);
        EventEntity entity = eventCommonBusinessService.pushDQOrSave(data, isOpenDq);
        
        // 是否开启人审
        log.info("整体耗时:{} ms", System.currentTimeMillis() - start);
        // 推送过滤事件到Mq通用的过滤列表
        pushFilteredEventToMq(data);
        //盒子事件上报成功，增加临时缓存，防止网络异常情况，盒子会补偿推送事件重复
        redisUtils.set(data.getEngineEventId(), data.getEngineEventId(), 600L);
        return ResultResponse.ok();
    }

    private void buildMarkingObject(EventDto dto) {
        JSONObject dataExtra = dto.getExtra();
        // 机审设置标记时间，标记人
        JSONObject marking = new JSONObject();
        marking.put("MarkingTime", new Date());
        marking.put("MarkingBy", 0);
        marking.put("MarkEventCount", 1);
        dataExtra.put("marking", marking);
        dto.setExtra(dataExtra);
    }

    /**
     * 保存事件过滤和返回
     *
     * @param baseConfig 基础配置
     * @param data       数据
     * @return {@link ResultResponse}
     */
    private ResultResponse saveFilteredEventAndReturn(BaseConfigVo baseConfig, EventDto data) {
        data.setMarking(MarkingType.filtered.code());
        log.info("filter insert :{}", JSON.toJSONString(data));
        EventEntity eventEntity = JSONObject.parseObject(JSON.toJSONString(data), EventEntity.class);
        eventEntity.setCreateTime(new Date());
        eventEntity.setIsDel(IsDel.NORMAL.code());
        eventService.saveEvent(eventEntity);
        data.setId(eventEntity.getId());
        eventAsyncService.snapshotAsync(baseConfig, data, s3Service, s3Config);
        return ResultResponse.ok();
    }

    /**
     * 特殊事件处理流程
     *
     * @param eventDto 事件签证官
     */
    private void specialEventProcess(EventDto eventDto) {
        try {
            Map<String, SpecialEventConfig.LedEventTypeConf> eventTypeConf = specialEventConfig.getEventType();
            if (ObjectUtil.isNull(eventTypeConf)) {
                return;
            }
            if (eventTypeConf.containsKey(eventDto.getEventType()) &&
                eventTypeConf.get(eventDto.getEventType()).getIsOpen()) {
                List<BoxLedEntity> leds = boxLedService.list(new LambdaQueryWrapper<BoxLedEntity>()
                        .eq(BoxLedEntity::getBoxSn, eventDto.getSource())
                        .eq(BoxLedEntity::getStatus, 1));
                if (CollUtil.isEmpty(leds)) {
                    log.error("特殊事件处理，盒子:{} 下未找到可用Led", eventDto.getSource());
                    return;
                }
                // 执行LED 显示逻辑
                OpenSpecialLedCmdDto ledCmdDto = new OpenSpecialLedCmdDto();
                ledCmdDto.setBoxSn(eventDto.getSource());
                ledCmdDto.setDevList(leds.get(0).getCode());
                ledCmdDto.setKeep(eventTypeConf.get(eventDto.getEventType()).getKeep());
                ledCmdDto.setContent(eventTypeConf.get(eventDto.getEventType()).getText());
                openDeviceBs.sendLedCustomizeShowCmd(ledCmdDto);
            }
        } catch (Exception e) {
            log.error("特殊事件处理异常：{}", e);
        }
    }

    /**
     * @param eventDto
     */
    private void specialEventImageFlowProcess(EventDto eventDto) {
        try {
            // 取出 图片分析事件中 原始 pictureId 更新到 原事件中
            log.info("开始处理图片分析任务事件更新-> {}", JSON.toJSONString(eventDto));
            JSONObject originalConfig = eventDto.getExtraData().getJSONObject("originalConfig");
            if (ObjectUtil.isNull(originalConfig)) {
                return;
            }
            if (TaskType.imageFlow.code().equals(originalConfig.getString("type"))) {
                JSONObject snapshot = eventDto.getSnapshot().getJSONObject(0);
                if (ObjectUtil.isNull(snapshot) || !snapshot.containsKey("pictureId")) {
                    return;
                }
                EventEntity orginalEvent = eventService.getById(snapshot.getString("pictureId"));
                if (ObjectUtil.isEmpty(orginalEvent)) {
                    return;
                }
                EventEntity eventEntity = new EventEntity();
                eventEntity.setId(orginalEvent.getId());
                eventEntity.setExtraData(eventDto.getExtraData());
                eventService.updateById(eventEntity);
            }
            log.info("结束 处理图片分析任务事件更新, engine_event_id -> {}", eventDto.getEngineEventId());
        } catch (Exception e) {
            log.error("处理图片分析任务事件更新异常：{}", e);
        }
    }

    @Async("taskExecutor")
    public void pushFilteredEventToMq(EventDto eventDto) {
        if (MarkingType.event.code().equals(eventDto.getMarking())) {
            return;
        }
        log.info("push filtered event to kafka start ======");
        kafkaProducerFeign.produceCustomTopicMsg(
                KafkaPlatformTopic.PLATFORM_CUSTOMER_META_EVENTS_FILTERED.code(),
                eventDto);
    }


}
