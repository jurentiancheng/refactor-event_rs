package com.supremind.event.filter;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.supremind.event.constant.CacheStatisticsEventRedisConstants;
import com.supremind.event.dto.event.EventDto;
import com.supremind.event.enums.FilteredType;
import com.supremind.redis.common.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
public class PlateEventsFilter {

    private static final String[] PLATE_COLOR = {"s_yellow", "d_yellow"};

    @Autowired
    private RedisUtils redisUtils;

    public boolean filter(EventDto vo, JSONObject plateJsonObject) {
        // 1.1 黄牌车
        boolean onlyYellowPlate = this.onlyYellowPlate(vo, plateJsonObject);
        if (!onlyYellowPlate) {
            log.info("event filtered -> onlyYellowPlate");
            vo.setFilteredType(FilteredType.yellowPlate.code());
            return true;
        }
        // 1.2 无车牌
        boolean ignoreNoPlateEvents = this.ignoreNoPlateEvents(vo, plateJsonObject);
        if (ignoreNoPlateEvents) {
            log.info("event filtered -> noPlate");
            vo.setFilteredType(FilteredType.noPlate.code());
            return true;
        }
        // 1.3 模糊车牌
        boolean ignoreBlurryPlateEvents = this.ignoreBlurryPlateEvents(vo, plateJsonObject);
        if (ignoreBlurryPlateEvents) {
            log.info("event filtered -> blurryPlate");
            vo.setFilteredType(FilteredType.blurryPlate.code());
            return true;
        }
        // 1.4 车牌类型过滤
        boolean onlyPlateTypes = this.onlyPlateTypes(vo, plateJsonObject);
        if (onlyPlateTypes) {
            log.info("event filtered -> onlyPlateTypes");
            vo.setFilteredType(FilteredType.plateColorFiltered.code());
            return true;
        }
        // 1.5 非机动车车牌类型过滤
        boolean nonMotorPlateTypesFilter = this.nonMotorPlateTypesFilter(vo, plateJsonObject);
        if (nonMotorPlateTypesFilter) {
            log.info("event filtered -> nonMotorPlateTypesFilter");
            vo.setFilteredType(FilteredType.plateColorFiltered.code());
            return true;
        }
        // 1.6 车牌特殊字符过滤
        boolean plateSpecialTextFilter = this.plateSpecialTextFilter(vo, plateJsonObject);
        if (plateSpecialTextFilter) {
            log.info("event filtered -> plateSpecialTextFilter");
            vo.setFilteredType(FilteredType.specialPlateFilter.code());
            return true;
        }
        // 1.7 车牌位数不足过滤
        boolean shortPlateFilter = this.shortPlateFilter(vo, plateJsonObject);
        if (shortPlateFilter) {
            log.info("event filtered -> shortPlateFilter");
            vo.setFilteredType(FilteredType.shortPlateFilter.code());
            return true;
        }
        // 1.8 同车牌
        boolean ignoreSamePlateEvents = this.ignoreSamePlateEvents(vo, plateJsonObject);
        if (ignoreSamePlateEvents) {
            log.info("event filtered -> samePlate");
            vo.setFilteredType(FilteredType.samePlate.code());
            return true;
        }
        return false;
    }

    /**
     * 为 true -> 事件上报 为 false -> 非黄牌车，事件丢弃
     * 
     * @param vo
     * @return
     */
    private boolean onlyYellowPlate(EventDto vo, JSONObject eventsFilterConfig) {
        JSONObject onlyYellowPlate = eventsFilterConfig.getJSONObject("onlyYellowPlate");
        if (onlyYellowPlate == null) {
            return true;
        }
        boolean enable = onlyYellowPlate.getBooleanValue("enable");
        if (!enable) {
            return true;
        }
        String plateColor = vo.getPlateColor();
        JSONArray eventTypes = onlyYellowPlate.getJSONArray("eventTypes");
        String eventType = vo.getEventType();
        // 未配置eventType,默认全部
        if (CollectionUtils.isEmpty(eventTypes)) {
            return true;
        }
        if (eventTypes.contains(eventType)) {
            log.info("onlyYellowPlate filter start -> {}", plateColor);
            if (Arrays.asList(PLATE_COLOR).contains(plateColor)){
                return true;
            }else {
                return false;
            }
        }else {
            return true;
        }
    }

    /**
     * 为true -> 无车牌，事件过滤
     * 
     * @param vo
     * @return
     */
    private boolean ignoreNoPlateEvents(EventDto vo, JSONObject eventsFilterConfig) {
        JSONObject ignoreNoPlateEvents = eventsFilterConfig.getJSONObject("ignoreNoPlateEvents");
        if (ignoreNoPlateEvents == null) {
            return false;
        }
        boolean enable = ignoreNoPlateEvents.getBooleanValue("enable");
        if (!enable) {
            return false;
        }
        String eventType = vo.getEventType();
        JSONArray eventTypes = ignoreNoPlateEvents.getJSONArray("eventTypes");
        if (CollectionUtils.isNotEmpty(eventTypes) && eventTypes.contains(eventType)) {
            String plateNumber = vo.getPlateNumber();
            return StringUtils.isBlank(plateNumber);
        }
        return false;
    }

    /**
     * 模糊车牌过滤 true -> 过滤
     * 
     * @param vo
     * @param eventsFilterConfig
     * @return
     */
    private boolean ignoreBlurryPlateEvents(EventDto vo, JSONObject eventsFilterConfig) {
        JSONObject ignoreBlurryPlateEvents = eventsFilterConfig.getJSONObject("ignoreBlurryPlateEvents");
        if (ignoreBlurryPlateEvents == null) {
            return false;
        }
        boolean enable = ignoreBlurryPlateEvents.getBooleanValue("enable");
        if (!enable) {
            return false;
        }
        Double blurryLevel = ignoreBlurryPlateEvents.getDouble("blurryLevel");
        if (blurryLevel == null || blurryLevel <= 0) {
            log.warn("ignoreBlurryPlateEvents illegal param -> blurryLevel:{}", blurryLevel);
            return false;
        }
        JSONObject extraData = vo.getExtraData();
        if (extraData == null) {
            return false;
        }
        String plateNumber = vo.getPlateNumber();
        if (plateNumber == null) {
            return false;
        }
        Double score = extraData.getDouble("plateNumberScore");
        if (score == null) {
            return false;
        }
        String eventType = vo.getEventType();
        log.info("ignoreBlurryPlateEvents filter start -> {}, {}, {}", eventType, score, blurryLevel);
        JSONArray eventTypes = ignoreBlurryPlateEvents.getJSONArray("eventTypes");
        if (CollectionUtils.isNotEmpty(eventTypes) && eventTypes.contains(eventType)) {
            // 置信度小于指定大小，返回true，过滤
            return score < blurryLevel;
        }
        return false;
    }

    /**
     * true -> 同车牌事件，事件过滤 false -> 不同车牌，同时事件推送至redis
     * 
     * @param vo
     * @return
     */
    private boolean ignoreSamePlateEvents(EventDto vo, JSONObject eventsFilterConfig) {
        String plateNumber = vo.getPlateNumber();
        if (plateNumber == null) {
            return false;
        }
        JSONObject ignoreSamePlateEvents = eventsFilterConfig.getJSONObject("ignoreSamePlateEvents");
        if (ignoreSamePlateEvents == null) {
            return false;
        }
        boolean enable = ignoreSamePlateEvents.getBooleanValue("enable");
        if (!enable) {
            return false;
        }
        Integer coolingSeconds = ignoreSamePlateEvents.getInteger("coolingSeconds");
        if (coolingSeconds == null || coolingSeconds < 0) {
            return false;
        }
        JSONArray eventTypes = ignoreSamePlateEvents.getJSONArray("eventTypes");
        String eventType = vo.getEventType();
        if (CollectionUtils.isEmpty(eventTypes) || !eventTypes.contains(eventType)) {
            return false;
        }
        Long projectId = vo.getProjectId();
        String key = redisUtils.joinKey(CacheStatisticsEventRedisConstants.PLATE_KEY, projectId, eventType, plateNumber);
        Object value = redisUtils.get(key);
        if (value instanceof String) {
            String basePlate = (String)value;
            if (plateNumber.equals(basePlate)) {
                return true;
            }
        }
        redisUtils.set(key, plateNumber, coolingSeconds);
        return false;
    }

    /**
     * 车牌颜色类型过滤
     * 
     * @param vo
     * @param eventsFilterConfig
     * @return true -> 过滤列表
     */
    private boolean onlyPlateTypes(EventDto vo, JSONObject eventsFilterConfig) {
        JSONObject onlyPlateTypes = eventsFilterConfig.getJSONObject("onlyPlateTypes");
        if (onlyPlateTypes == null) {
            return false;
        }
        boolean enable = onlyPlateTypes.getBooleanValue("enable");
        if (!enable) {
            return false;
        }
        JSONArray plateColors = onlyPlateTypes.getJSONArray("plateColor");
        if (CollectionUtils.isEmpty(plateColors)) {
            return false;
        }
        String plateColor = vo.getPlateColor();
        String eventType = vo.getEventType();
        JSONArray eventTypes = onlyPlateTypes.getJSONArray("eventTypes");
        // 不存在配置里的车牌颜色过滤
        if (CollectionUtils.isNotEmpty(eventTypes) && eventTypes.contains(eventType)) {
            return !plateColors.contains(plateColor);
        }
        return false;
    }

    /**
     * 非机动车车牌类型过滤 true -> 过滤
     * 
     * @param vo
     * @param eventsFilterConfig
     * @return
     */
    private boolean nonMotorPlateTypesFilter(EventDto vo, JSONObject eventsFilterConfig) {
        JSONArray nonMotorPlateTypesFilters = eventsFilterConfig.getJSONArray("nonMotorPlateTypesFilter");
        if (CollectionUtils.isEmpty(nonMotorPlateTypesFilters)) {
            return false;
        }
        JSONObject data = vo.getExtraData();
        String eventType = vo.getEventType();
        if (data == null) {
            log.warn("illegal vo, no data -> {}", vo);
            return false;
        }
        JSONObject summary = data.getJSONObject("summary");
        if (summary == null) {
            log.warn("illegal data, no summary -> {}", data);
            return false;
        }
        String plateColor = "nullValue";
        JSONObject color = summary.getJSONObject("plate/type");
        if (color != null) {
            String label = color.getString("label");
            if (StringUtils.isNotBlank(label)) {
                plateColor = label;
            }
        }
        for (int i = 0; i < nonMotorPlateTypesFilters.size(); i++) {
            JSONObject nonMotorPlateTypesFilter = nonMotorPlateTypesFilters.getJSONObject(i);
            JSONArray plateColors = nonMotorPlateTypesFilter.getJSONArray("plateColor");
            if (CollectionUtils.isEmpty(plateColors)) {
                continue;
            }
            JSONArray eventTypes = nonMotorPlateTypesFilter.getJSONArray("eventTypes");
            if (CollectionUtils.isNotEmpty(eventTypes) && eventTypes.contains(eventType)) {
                if (!plateColors.contains(plateColor)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 车牌特殊字符过滤
     * 
     * @param vo
     * @param eventsFilterConfig
     * @return true -> 包含特殊字符，过滤
     */
    private boolean plateSpecialTextFilter(EventDto vo, JSONObject eventsFilterConfig) {
        JSONObject plateSpecialTextFilter = eventsFilterConfig.getJSONObject("plateSpecialTextFilter");
        if (plateSpecialTextFilter == null) {
            return false;
        }
        JSONArray specialTexts = plateSpecialTextFilter.getJSONArray("specialTexts");
        if (CollectionUtils.isEmpty(specialTexts)) {
            return false;
        }
        String plateNumber = vo.getPlateNumber();
        if (StringUtils.isBlank(plateNumber)) {
            return false;
        }
        String eventType = vo.getEventType();
        JSONArray eventTypes = plateSpecialTextFilter.getJSONArray("eventTypes");
        if (CollectionUtils.isNotEmpty(eventTypes) && eventTypes.contains(eventType)) {
            for (int i = 0; i < specialTexts.size(); i++) {
                String specialText = specialTexts.getString(i);
                if (plateNumber.contains(specialText)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 车牌位数不足7位过滤
     * 
     * "shortPlateFilter":{"enable": false, "eventTypes":[]}
     * 
     * @param vo
     * @param eventsFilterConfig
     * @return true -> 过滤
     */
    private boolean shortPlateFilter(EventDto vo, JSONObject eventsFilterConfig) {
        JSONObject shortPlateFilter = eventsFilterConfig.getJSONObject("shortPlateFilter");
        if (shortPlateFilter == null) {
            return false;
        }
        boolean enable = shortPlateFilter.getBooleanValue("enable");
        if (!enable) {
            return false;
        }
        String eventType = vo.getEventType();
        JSONArray eventTypes = shortPlateFilter.getJSONArray("eventTypes");
        if (CollectionUtils.isNotEmpty(eventTypes) && eventTypes.contains(eventType)) {
            String plateNumber = vo.getPlateNumber();
            if (plateNumber != null && plateNumber.length() < 7) {
                log.info("plate number length:{}", plateNumber.length());
                return true;
            }
        }
        return false;
    }

}
