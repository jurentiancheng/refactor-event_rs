package com.supremind.event.business.event;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.supremind.base.utils.DateUtils;
import com.supremind.common.enums.IsDel;
import com.supremind.common.result.ResultResponse;
import com.supremind.event.cache.*;
import com.supremind.event.condition.camera.CameraCondition;
import com.supremind.event.constant.EventConstants;
import com.supremind.event.context.EventServiceContext;
import com.supremind.event.dto.event.EventDto;
import com.supremind.event.dto.event.ReviewDto;
import com.supremind.event.dto.event.ReviewUpdateFieldDto;
import com.supremind.event.entity.camera.CameraEntity;
import com.supremind.event.entity.event.EventEntity;
import com.supremind.event.entity.task.TaskAlgParamEntity;
import com.supremind.event.enums.*;
import com.supremind.event.filter.OtherEventsFilter;
import com.supremind.event.filter.PlateEventsFilter;
import com.supremind.event.helper.BeanHelper;
import com.supremind.event.queue.EventEvidenceDelayQueue;
import com.supremind.event.service.ICameraService;
import com.supremind.event.service.event.EventService;
import com.supremind.event.service.portal.CacheStatisticsEventService;
import com.supremind.event.service.task.ITaskAlgParamService;
import com.supremind.event.utils.EvidenceVideoUtil;
import com.supremind.event.utils.PictureCompressUtil;
import com.supremind.event.utils.SnapShotUtil;
import com.supremind.event.vo.algorithm.AlgorithmCacheVo;
import com.supremind.event.vo.config.EventFilterConfigCacheVo;
import com.supremind.event.vo.configuration.BaseConfigVo;
import com.supremind.event.vo.configuration.EventFilterConfigVo;
import com.supremind.event.vo.event.DelayQueueEventVo;
import com.supremind.event.vo.event.EventVo;
import com.supremind.event.vo.event.ReviewDataPushVo;
import com.supremind.event.vo.portal.StatisticsEventVo;
import com.supremind.event.vo.task.TaskCacheVo;
import com.supremind.feign.review.EventsFeign;
import com.supremind.redis.common.RedisUtils;
import com.supremind.s3.config.S3Config;
import com.supremind.s3.service.S3Service;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 事件公共处理类
 */
@Slf4j
@Service
public class EventCommonBusinessService implements Cache {

    @Autowired
    private PlateEventsFilter plateEventsFilter;
    @Autowired
    private OtherEventsFilter otherEventsFilter;
    @Autowired
    private EvidenceVideoUtil evidenceVideoUtil;
    @Autowired
    private EventEvidenceDelayQueue eventEvidenceDelayQueue;
    @Autowired
    private S3Config s3Config;
    @Autowired
    private S3Service s3Service;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private EventService eventService;
    @Autowired
    private CacheStatisticsEventService cacheStatisticsEventService;
    @Autowired
    private ITaskAlgParamService taskAlgParamService;
    @Autowired
    private TaskCache taskCache;
    @Autowired
    private EventAlgorithmCache algorithmCache;
    @Autowired
    private BaseConfigCache baseConfigCache;
    @Autowired
    private EventFilterConfigCache eventFilterConfigCache;
    @Autowired
    private ICameraService cameraService;

    @Autowired
    private EventsFeign eventsFeign;

    private static final List<String> POSITION_EVENT_TYPE_LIST = Arrays.asList("7021", "7022", "7023", "7024");

    private static final String default_base_config = "0";
    @Autowired
    private RedisTemplate redisTemplate;


    public boolean filterEvent(EventDto data) {

        // 事件冷却时间平台侧过滤
//        JSONObject originalConfig = data.getExtraData().getJSONObject("originalConfig");
//        JSONObject algParam = originalConfig.getJSONArray("algList").getJSONObject(data.getOriginalViolationIndex()).getJSONObject("algParam");
//        Integer coolingSecond = algParam.getInteger("cooling_second");
//        if (ObjectUtil.isNotEmpty(coolingSecond)) {
//            long millis = data.getEventTime().getTime();
//            long oldMillis = millis - coolingSecond * 1000;
//            Date date = new Date(oldMillis);
//            List<EventVo> eventVos = queryEvent(data.getTaskCode(), data.getEventType(), date, data.getEventTime());
//            if (CollUtil.isNotEmpty(eventVos)) {
//                return true;
//            }
//        }

        //todo 缓存
        EventFilterConfigCacheVo cacheVo = eventFilterConfigCache.get(String.valueOf(data.getProjectId()));
        if (null == cacheVo) {
            log.info("EventFilterConfig is empty");
            return false;
        }
        // 车牌事件过滤
        JSONObject plateJsonObject = getConfig(cacheVo, SettingGroupType.plate);
        if (plateJsonObject != null) {
            boolean filter = plateEventsFilter.filter(data, plateJsonObject);
            if (filter) {
                return true;
            }
        }
        // 其他事件过滤
        JSONObject otherJsonObject = this.getConfig(cacheVo, SettingGroupType.other);
        if (otherJsonObject != null) {
            boolean filter = otherEventsFilter.filter(data, otherJsonObject);
            if (filter) {
                return true;
            }
        }
        return false;
    }

    //获取过滤类型
    private JSONObject getConfig(EventFilterConfigCacheVo configVoList, SettingGroupType settingGroupType) {
        for (EventFilterConfigVo vo : configVoList.getConfigVoList()) {
            if (settingGroupType.code().equals(vo.getSettingGroup())) {
                return vo.getConfig();
            }
        }
        return null;
    }

    private List<EventVo> queryEvent(String taskNo, String eventType, Date startTime, Date endTime) {
        List<EventEntity> eventList = eventService.list(new LambdaQueryWrapper<EventEntity>()
                .eq(EventEntity::getTaskCode, taskNo)
                .eq(EventEntity::getEventType, eventType)
                .ne(EventEntity::getMarking, MarkingType.filtered.code())
                .between(EventEntity::getEventTime, startTime, endTime));
        List<EventVo> eventVos = BeanHelper.convertList(eventList, EventVo.class);
        return eventVos;
    }

    public void getEventEvidenceUrl(EventDto data, Integer evidenceOn) {
        StringBuilder eventEvidenceUrl = new StringBuilder();
        if (EventConstants.defaultevidenceOn == evidenceOn) {
            log.info("getEventEvidenceUrl start====== ");
            //获取该项目下算法取证视频配置
            AlgorithmCacheVo algorithmCacheVo = algorithmCache.get(String.valueOf(data.getProjectId()).concat("_").concat(data.getEventType()));
            JSONObject jsonObject = evidenceVideoUtil.calculateEvidencetime(data.getEventTime(), algorithmCacheVo);
            String preStartTime = String.valueOf(jsonObject.getDate("startTime").getTime());
            String preEndTime = String.valueOf(jsonObject.getDate("endTime").getTime());
            //预生成地址
            String date = DateUtils.dateToStr(DateUtils.getNowTime(), "yyyy/MMdd/HH");
            String urlSuffix = "/event/" + date + "/" + data.getCameraCode() + "_" + StringUtils.substring(preStartTime, 0, preStartTime.length() - 3)
                    + "_" + StringUtils.substring(preEndTime, 0, preEndTime.length() - 3) + "_.mp4";
            eventEvidenceUrl.append(s3Config.getOriginalEndpoint()).append(urlSuffix);
            data.setEvidenceStatus(EvidenceVideoStatus.loading.code());
            data.setEvidenceUrl(eventEvidenceUrl.toString());
            log.info("outter url==={}", s3Config.getOutterEndpoint());
            DelayQueueEventVo delayQueueEventVo = new DelayQueueEventVo();
            delayQueueEventVo.setEventId(data.getId());
            delayQueueEventVo.setBucketName(s3Config.getDefaultBucket());
            delayQueueEventVo.setCameraCode(data.getCameraCode());
            delayQueueEventVo.setStartTime(jsonObject.getDate("startTime").getTime());
            delayQueueEventVo.setEndTime(jsonObject.getDate("endTime").getTime());
            delayQueueEventVo.setTagetUrl(urlSuffix);
            long endTime = (System.currentTimeMillis() - jsonObject.getDate("endTime").getTime()) / 1000;
            eventEvidenceDelayQueue.produce(JSONObject.toJSONString(delayQueueEventVo), jsonObject.getDate("endTime").getTime());
            log.info("getEventEvidenceUrl to delayQueue end  =======");
        } else {
            data.setEvidenceStatus(EvidenceVideoStatus.blank.code());
        }
    }

    /**
     * 判断人审开关
     *
     * @param data  数据
     * @param isBox 是盒子
     * @return int
     */
    public Integer personnelCheck(EventDto data, boolean isBox) {
        // 判断布控任务中有没有开启dq配置
        // 配置为空 校验全局人审配置： 开-》 校验对应项目算法dq人审配置开关
        // 配置不为空 则校验开关
        AlgorithmOperateEnum algorithmOperateEnum = checkDqSwitch(data, isBox);
        if (AlgorithmOperateEnum.enable.equals(algorithmOperateEnum)) {
            data.setMarking(MarkingType.init.code());
        } else {
            data.setMarking(MarkingType.event.code());
            JSONObject dataExtra = data.getExtra();
            // 机审设置标记时间，标记人
            JSONObject marking = new JSONObject();
            marking.put("MarkingTime", new Date());
            marking.put("MarkingBy", 0);
            marking.put("MarkEventCount", 1);
            dataExtra.put("marking", marking);
            data.setExtra(dataExtra);
        }
        return algorithmOperateEnum.code();
    }

    /**
     * 检查人审开关
     * @param data
     * @param isBox
     * @return
     */
    private AlgorithmOperateEnum checkDqSwitch(EventDto data, boolean isBox) {
        // 判断布控任务中有没有开启dq配置
        // 配置为空 校验全局人审配置： 开-》 校验对应项目算法dq人审配置开关
        // 配置不为空 则校验开关
        Integer debugFlg = 1;
        JSONObject algParam = data.getExtraData().getJSONObject("originalConfig").getJSONArray("algList").getJSONObject(data.getOriginalViolationIndex()).getJSONObject("algParam");
        Integer isOpenDQ = algParam.getInteger("isOpenDQ");
        // DQ 没有配置，检查全局配置
        if (ObjectUtil.isEmpty(isOpenDQ)) {
            AlgorithmCacheVo algorithmCacheVo = algorithmCache.get(String.valueOf(data.getProjectId()).concat("_").concat(data.getEventType()));
            log.info("人审配置：{}  {}", String.valueOf(data.getProjectId()).concat("_").concat(data.getEventType()), JSON.toJSONString(algorithmCacheVo));
            debugFlg = redisTemplate.opsForValue().get(EventConstants.globalReview) == null ? AlgorithmOperateEnum.disable.code() : Integer.valueOf(String.valueOf(redisTemplate.opsForValue().get(EventConstants.globalReview)));
            log.info("人审全局配置:{}", debugFlg);
            //如果人审启用，才去判断对应来源的配置
            //此处直接判断布控内的算法开启DQ配置，如果开启，则推送,详见需求地址：https://cf.supremind.info/pages/viewpage.action?pageId=105396143
            if (AlgorithmOperateEnum.enable.code().equals(debugFlg)) {
                if (!isBox) {
                    debugFlg = algorithmCacheVo.getDebugSwitch();
                } else {
                    debugFlg = algorithmCacheVo.getBoxDebugSwitch();
                }
            }
            return AlgorithmOperateEnum.ofValue(debugFlg);
        }

        if (AlgorithmOperateEnum.disable.code().equals(isOpenDQ)) {
            return AlgorithmOperateEnum.disable;
        }
        // 判断Dq 配置若有需校验是否在时间段内
        Date eventTime = data.getEventTime();
        JSONObject openDqTime = algParam.getJSONObject("openDqTime");
        if (ObjectUtil.isEmpty(openDqTime)) {
            return AlgorithmOperateEnum.disable;
        }
        // 判断日期
        String openDqStartDate = openDqTime.getString("openDqStartDate");
        Date startDate = DateUtils.parseDate(openDqStartDate, "yyyy-MM-dd");
        String openDqEndDate = openDqTime.getString("openDqEndDate");
        Date endDate = DateUtils.parseDate(openDqEndDate, "yyyy-MM-dd");
        String toStr = DateUtils.dateToStr(eventTime, "yyyy-MM-dd");
        Date eventDate = DateUtils.parseDate(toStr);
        if (ObjectUtil.isNotEmpty(openDqStartDate) && ObjectUtil.isNotEmpty(openDqEndDate)) {
            if ((eventDate.compareTo(startDate) < 0 || eventDate.compareTo(endDate) > 0)) {
                return AlgorithmOperateEnum.disable;
            }
        }
        //判断时间
        String openDqStartTime = Optional.ofNullable(openDqTime.getString("openDqStartTime")).orElse("00:00");
        String openDqEndTime = Optional.ofNullable(openDqTime.getString("openDqEndTime")).orElse("23:59");
        String dateToStr = DateUtils.dateToStr(eventTime, "yyyy-MM-dd ");
        Date startTime = DateUtils.parseDate(dateToStr.concat(openDqStartTime), "yyyy-MM-dd HH:mm");
        Date endTime = DateUtils.parseDate(dateToStr.concat(openDqEndTime), "yyyy-MM-dd HH:mm");
        if ((eventTime.compareTo(startTime) < 0 || eventTime.compareTo(endTime) > 0)) {
            return AlgorithmOperateEnum.disable;
        }
        return AlgorithmOperateEnum.enable;
    }


    public void pushDQ(EventDto alarmVo) {
        try {
            ReviewDataPushVo reviewDataVo = new ReviewDataPushVo();
            BeanUtils.copyProperties(alarmVo, reviewDataVo);
            reviewDataVo.setEventId(alarmVo.getId());
            JSONObject originData = alarmVo.getExtraData();
            if (originData != null) {
                JSONArray position = originData.getJSONArray("position");
                if (CollectionUtils.isNotEmpty(position)) {
                    reviewDataVo.setPosition(position);
                }
                reviewDataVo.setSnapshot(alarmVo.getSnapshot());
                String taskSnapshot = originData.getString("taskSnapshot");
                if (null != taskSnapshot) {
                    // 布控原图
                    reviewDataVo.setTaskSnapshot(taskSnapshot);
                }
                // roi坐标信息
                String eventType = alarmVo.getEventType();
                JSONArray algList = originData.getJSONObject("originalConfig").getJSONArray("algList");
                List<Object> eventAlgs = algList.stream().filter(item -> JSONObject.parseObject(JSON.toJSONString(item)).getString("eventType").equals(eventType)).collect(Collectors.toList());
                JSONObject originalConfig = new JSONObject();
                JSONArray violations = new JSONArray();
                if (eventAlgs.size() > 0) {
                    JSONObject eventAlg = JSONObject.parseObject(JSON.toJSONString(eventAlgs.get(0)));
                    JSONObject algParam = eventAlg.getJSONObject("algParam");
                    violations.add(algParam);
                    originalConfig.put("violations", violations);
                }
                AlgorithmCacheVo algorithmCacheVo = algorithmCache.get(String.valueOf(alarmVo.getProjectId()).concat("_").concat(alarmVo.getEventType()));
                originalConfig.put("drawType", algorithmCacheVo.getDrawType());
                reviewDataVo.setOriginalConfig(originalConfig);
            }
            //特种车辆类型
            String specialCarType = alarmVo.getSpecialCarType();
            reviewDataVo.setSpecialCarType(specialCarType);
            // 公司信息
            Long companyId = alarmVo.getCompanyId();
            reviewDataVo.setCompanyId(companyId);
            reviewDataVo.setCompanyName(alarmVo.getCompanyName());

            // 摄像头信息
            reviewDataVo.setCameraCode(alarmVo.getCameraCode());
            // 可编辑字段
            AlgorithmCacheVo cacheVo = algorithmCache.get(String.valueOf(alarmVo.getProjectId()).concat("_").concat(alarmVo.getEventType()));
            if (null != cacheVo) {
                JSONObject editableConfig = cacheVo.getEditableConfig();
                if (editableConfig != null) {
                    JSONArray config = editableConfig.getJSONArray("config");
                    reviewDataVo.setEditable(config);
                }
            }
            log.info("review data pre push: {}", JSONObject.toJSONString(reviewDataVo));
            ResultResponse response = eventsFeign.add(JSON.parseObject(JSON.toJSONString(reviewDataVo)));
            log.info("push DQ result:{}", response);
        } catch (Exception e) {
            log.error("push DQ fail ===={}", e);
        }

    }

    public TaskCacheVo deposeBaseEventTaskData(EventDto data) {
        data.setId(EventServiceContext.getId());
        //处理事件相关联的项目，组织等信息
        TaskCacheVo taskCacheVo = taskCache.get(data.getTaskCode());
        if (taskCacheVo == null) {
            return null;
        }
        // 布控任务如果是多路流布控情况下，不能取Task中的camreaCode
        // 暂时仅处理 地磅布控任务，后面优化（上线前临时方案 后续优化 TODO...）
        if (TaskType.weighbridge.code().equals(taskCacheVo.getType())) {
            CameraCondition conditon = new CameraCondition();
            conditon.setBoxSn(data.getSource());
            List<CameraEntity> cameraEntities = cameraService.list(conditon);
            cameraEntities.forEach(cameraEntity -> {
               if (cameraEntity.getCode().equals(data.getCameraCode())) {
                   data.setCameraCodeName(cameraEntity.getName());
               }
            });
        }
        data.setProjectId(taskCacheVo.getProjectId());
        data.setProjectName(taskCacheVo.getProjectName());
        data.setCompanyId(taskCacheVo.getOrgId());
        data.setCompanyName(taskCacheVo.getOrgName());
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("task_version", taskCacheVo.getVersion());
        data.setExtra(jsonObject);
        return taskCacheVo;
    }

    public BaseConfigVo deposeBaseEventBaseConfigData(EventDto data) {
        //处理事件上报的图片信息
        BaseConfigVo configVo = baseConfigCache.get(String.valueOf(data.getProjectId()));
        if (null == configVo) {
            //如果项目没有压缩配置，则获取默认的压缩配置
            configVo = baseConfigCache.get(default_base_config);
        }
        return configVo;
    }


    public EventEntity pushDQOrSave(EventDto data, Integer debugFlg) {
        EventEntity eventEntity = BeanHelper.convertObj(data, EventEntity.class);
        eventEntity.setCreateTime(new Date());
        eventEntity.setIsDel(IsDel.NORMAL.code());
        eventEntity.setMarkingTime(new Date());

        if (AlgorithmOperateEnum.enable.code().equals(debugFlg)) {
            this.pushDQ(data);
        } 
        eventService.saveEvent(eventEntity);
        return eventEntity;
    }

    public void deposeUpdateFields(EventDto eventDto, ReviewUpdateFieldDto updateFields) {
        String plateNumber = updateFields.getPlateNumber();
        if (ObjectUtil.isNotEmpty(plateNumber)) {
            eventDto.setPlateNumber(plateNumber);
        }
        String plateColor = updateFields.getPlateColor();
        if (ObjectUtil.isNotEmpty(plateColor)) {
            eventDto.setPlateColor(plateColor);
        }
        String vehicleType = updateFields.getVehicleType();
        if (ObjectUtil.isNotEmpty(vehicleType)) {
            eventDto.setVehicleType(vehicleType);
        }
        String specialCarType = updateFields.getSpecialCarType();
        if (ObjectUtil.isNotEmpty(specialCarType)) {
            eventDto.setSpecialCarType(specialCarType);
        }

        List<String> bedList = updateFields.getBedList();
        if (CollectionUtils.isNotEmpty(bedList)) {
            eventDto.getExtra().put("bedList", bedList);
        }
    }

    public EventDto deposeSaveChangedSnapshot(EventEntity eventEntity, EventDto eventDto, ReviewDto dto) {
        Long eventId = dto.getEventId();
        // 获取该事件的原始信息

        String eventType = eventEntity.getEventType();
        Long projectId = eventEntity.getProjectId();
        // 获取算法标签
        AlgorithmCacheVo algorithmCacheVo = algorithmCache.get(projectId + "_" + eventType);
        String label = algorithmCacheVo.getLabel();
        Integer index = dto.getIndex() == null ? 0 : dto.getIndex();
        //获取画框坐标
        List<Integer> updatePoints = dto.getUpdatePoints();
        // 判断是否是岗位算法，如果是岗位算法 需要获取布控线 画线
        if (POSITION_EVENT_TYPE_LIST.contains(eventType)) {
            TaskCacheVo taskCacheVo = taskCache.get(eventEntity.getTaskCode());
            LambdaQueryWrapper<TaskAlgParamEntity> wrapper = Wrappers.lambdaQuery();
            wrapper.eq(TaskAlgParamEntity::getTaskId, taskCacheVo.getId());
            wrapper.eq(TaskAlgParamEntity::getEventType, eventType);
            wrapper.eq(TaskAlgParamEntity::getIsDel, IsDel.NORMAL);
            TaskAlgParamEntity taskAlgParamEntity = taskAlgParamService.getOne(wrapper);
            JSONObject algParam = taskAlgParamEntity.getAlgParam();
            JSONObject roi = algParam.getJSONArray("roi").getJSONObject(0);
            updatePoints = JSON.parseObject(roi.getJSONArray("data").toJSONString(), List.class);
        }
        if (ObjectUtil.isEmpty(updatePoints)) {
            return eventDto;
        }

        final List<Integer> points = updatePoints;
        JSONArray snapshot = eventEntity.getSnapshot();
        if (snapshot.size() == index) {
            index--;
        }
        JSONObject snapshotJSONObject = snapshot.getJSONObject(index);

        //枪球联动处理重新画框删除抓拍图
        Boolean reDraw = dto.getReDraw();
        if (BooleanUtil.isTrue(reDraw)) {
            snapshotJSONObject.remove("sphereSnapshotUris");
        }
        String snapshotUri = snapshotJSONObject.getString("snapshotUriRawCompress");
        final String urlValue = snapshotUri;
        if (ObjectUtil.isNotEmpty(snapshotUri)) {
            String snapshotUriName = s3Service.createObjectName(".jpg", "event", "review_snapshotUri", false);
            try {
                log.info("DQ坐标框绘图处理开始...");
                /**
                 * 解决划线过程中，主线程卡住问题
                 */
                CompletableFuture<InputStream> future = null;
                if (POSITION_EVENT_TYPE_LIST.contains(eventType)) {
                    future = CompletableFuture.supplyAsync(() -> SnapShotUtil.drawLine(urlValue, points));
                } else {
                    log.info("人审画框标签:{},{},{},{},{}", urlValue, points, eventType, label, eventId);
                    future = CompletableFuture.supplyAsync(() -> SnapShotUtil.drawReact(urlValue, points, eventType, label, eventId));
                }
                InputStream inputStream = null;
                try {
                    inputStream = future.get(15, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    log.error("newUrlVal={},eventId={},eventType={}", urlValue, eventId, eventType);
                    log.error("drawReact exception", ex);
                    return null;
                }
                if (!future.isDone()) {
                    future.cancel(true);
                    log.error("drawReact逻辑未完成尝试取消，newUrlVal={},eventId={} ", urlValue, eventId);
                }

                if (inputStream != null) {
                    log.info("处理事件->{},DQ坐标图片上传处理开始...", eventId);
                    // 未压缩渲染图上传
                    String fileUrl = s3Service.streamUpload(snapshotUriName, inputStream, s3Config.getDefaultBucket(), null);
                    log.info("review snapshot re upload success -> {}", fileUrl);
                    snapshotJSONObject.put("snapshotUri", fileUrl);
                    snapshotUri = fileUrl;
                }
                log.info("DQ坐标框绘图处理结束，图片地址为:{}", snapshotUri);
            } catch (Exception e) {
                log.error("snapshotFields画框失败");
                log.error(e.getMessage(), e);
                return null;
            }

            // 有配置根据配置压缩，无配置使用原图填充
            String snapshotUriCompress = snapshotUri;
            String snapshotUriDisCompress = snapshotUri;
            // 获取图片压缩配置
            //处理事件上报的图片信息
            BaseConfigVo configVo = baseConfigCache.get(String.valueOf(eventEntity.getProjectId()));
            if (null == configVo) {
                //如果项目没有压缩配置，则获取默认的压缩配置
                configVo = baseConfigCache.get(default_base_config);
            }
            if (ObjectUtil.isNotEmpty(configVo)) {
                boolean enable = configVo.getConfig().getBoolean("enable");
                Integer height = configVo.getConfig().getInteger("height");
                Double quality = configVo.getConfig().getDouble("quality");
                if (enable) {
                    String snapshotUriCompressName = "";
                    String snapshotUridisCompressName = "";
                    if (s3Config.getOpenCloudCompress()) {
                        log.info("review data cloud compress start=======");
                        snapshotUriCompressName = s3Service.createObjectName(".jpg", "event", "review_snapshotUri_compress", false);
                        snapshotUriCompress = PictureCompressUtil.cloudCompress(s3Config, snapshotUriCompress, snapshotUriCompressName, null, null, quality);
                        String snapshotUriCompressDisName = s3Service.createObjectName(".jpg", "event", "review_snapshotUri_dis_compress", false);
                        snapshotUriDisCompress = PictureCompressUtil.cloudCompress(s3Config, snapshotUriCompress, snapshotUriCompressDisName, null, height, quality);
                    } else {
                        log.info("review data program compress start=======");
                        InputStream rawCompressIs = PictureCompressUtil.compress(snapshotUriCompress, null, quality);
                        snapshotUriCompressName = s3Service.createObjectName(".jpg", "event", "review_snapshotUri_compress", false);
                        snapshotUriCompress = s3Service.streamUpload(snapshotUriCompressName, rawCompressIs, s3Config.getDefaultBucket());
                        InputStream rawdisCompressIs = PictureCompressUtil.compress(snapshotUriDisCompress, height, quality);
                        snapshotUridisCompressName = s3Service.createObjectName(".jpg", "event", "review_snapshotUri_dis_compress", false);
                        snapshotUriDisCompress = s3Service.streamUpload(snapshotUridisCompressName, rawdisCompressIs, s3Config.getDefaultBucket());
                    }

                }
            }
            snapshotJSONObject.put("snapshotUriCompress", snapshotUriCompress);
            snapshotJSONObject.put("snapshotUriDisCompress", snapshotUriDisCompress);
            eventDto.setSnapshotUriCompress(snapshotUriCompress);
            log.info("review snapshot re upload end -> {}", urlValue);

        }
        JSONArray array = new JSONArray();
        for (int i = 0; i < updatePoints.size(); i += 2) {
            array.add(updatePoints.stream().skip(i).limit(2).collect(Collectors.toList()));
        }
        snapshotJSONObject.put("pts", array);
        eventDto.setSnapshot(snapshot);
        return eventDto;
    }


    @Override
    public void init() {

    }

    @Override
    public Object get(String key) {
        return null;
    }

}
