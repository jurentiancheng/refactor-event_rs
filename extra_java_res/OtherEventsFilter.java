package com.supremind.event.filter;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.supremind.event.constant.CacheStatisticsEventRedisConstants;
import com.supremind.event.dto.event.EventDto;
import com.supremind.event.enums.FilteredType;
import com.supremind.redis.common.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Slf4j
@Component
public class OtherEventsFilter {

    private static final String CONTINUOUS_LANE_CHANGE = "2111";

    private static final String NULL_VALUE = "nullValue";

    private static final List<String> ILLEGAL_VALUES = new ArrayList<String>() {
        {
            add("未知");
            add("null");
        }
    };

    @Autowired
    private RedisUtils redisUtils;

    public boolean filter(EventDto vo, JSONObject otherJsonObject) {
        // 1.5 同位置(机动车)
        boolean ignoreSamePosEvents = this.ignoreSamePosEvents(vo, otherJsonObject);
        if (ignoreSamePosEvents) {
            log.info("event filtered -> samePosition");
            vo.setFilteredType(FilteredType.samePosition.code());
            return true;
        }

        //添加事件过滤需求，详见https://cf.supremind.info/pages/viewpage.action?pageId=105397380
        //过滤全部事件
        boolean ignoreAllEvents = this.ignoreAllEvents(vo, otherJsonObject);
        if (ignoreAllEvents){
            log.info("event filtered -> ignoreAllEvents");
            vo.setFilteredType(FilteredType.ignoreAllEvents.code());
            return true;
        }

        //过滤部分事件
        boolean ignorePartEvents = this.ignorePartEvents(vo, otherJsonObject);
        if (ignorePartEvents){
            log.info("event filtered -> ignorePartEvents");
            vo.setFilteredType(FilteredType.ignorePartEvents.code());
            return true;
        }

        return false;
    }

    private boolean ignorePartEvents(EventDto vo, JSONObject otherJsonObject) {
        JSONObject ignoreSamePosEvents = otherJsonObject.getJSONObject("ignorePartEvents");
        if (ignoreSamePosEvents == null) {
            return false;
        }
        boolean enable = ignoreSamePosEvents.getBooleanValue("enable");
        if (!enable) {
            return false;
        }
        // 算法类型是否开启此过滤
        JSONArray eventTypes = ignoreSamePosEvents.getJSONArray("eventTypes");
        String eventResult = ignoreSamePosEvents.getString("eventResult");
        String eventType = vo.getEventType();
        if (CollectionUtils.isNotEmpty(eventTypes)) {
            if (!eventTypes.contains(eventType)){
                return false;
            }else {
                //过滤到对应车辆冲洗结果
                if (!vo.getExtraData().getJSONObject("eventResult").getString("result").equals(eventResult)){
                    return false;
                }
            }
        }else {
            return false;
        }
        return enable;
    }

    private boolean ignoreAllEvents(EventDto vo, JSONObject eventsFilterConfig) {
        JSONObject ignoreSamePosEvents = eventsFilterConfig.getJSONObject("ignoreAllEvents");
        if (ignoreSamePosEvents == null) {
            return false;
        }
        boolean enable = ignoreSamePosEvents.getBooleanValue("enable");
        if (!enable) {
            return false;
        }
        // 算法类型是否开启此过滤
        JSONArray eventTypes = ignoreSamePosEvents.getJSONArray("eventTypes");
        String eventType = vo.getEventType();
        if (CollectionUtils.isEmpty(eventTypes) || !eventTypes.contains(eventType)) {
            return false;
        }
        return enable;
    }

    /**
     * 同位置过滤入口 true -> 过滤
     * 
     * @param vo
     * @param eventsFilterConfig
     * @return
     */
    private boolean ignoreSamePosEvents(EventDto vo, JSONObject eventsFilterConfig) {
        JSONObject ignoreSamePosEvents = eventsFilterConfig.getJSONObject("ignoreSamePosEvents");
        if (ignoreSamePosEvents == null) {
            return false;
        }
        boolean enable = ignoreSamePosEvents.getBooleanValue("enable");
        if (!enable) {
            return false;
        }
        // 算法类型是否开启此过滤
        JSONArray eventTypes = ignoreSamePosEvents.getJSONArray("eventTypes");
        String eventType = vo.getEventType();
        if (CollectionUtils.isEmpty(eventTypes) || !eventTypes.contains(eventType)) {
            return false;
        }
        Integer coolingSeconds = ignoreSamePosEvents.getInteger("coolingSeconds");
        if (coolingSeconds == null || coolingSeconds < 0) {
            log.warn("illegal cooling second -> {}", coolingSeconds);
            return false;
        }
        Double posOverlapPercent = ignoreSamePosEvents.getDouble("posOverlapPercent");
        if (posOverlapPercent == null || posOverlapPercent < 0) {
            log.warn("illegal posOverlapPercent -> {}", posOverlapPercent);
            return false;
        }
        JSONObject data = vo.getExtraData();
        if (data == null) {
            return false;
        }
        JSONArray position = data.getJSONArray("position");
        if (null != position && CollectionUtils.isNotEmpty(position)) {
            return this.ignoreSamePosFlowEvents(vo, coolingSeconds, posOverlapPercent);
        } else {
            return this.ignoreSamePosOtherEvents(vo, coolingSeconds, posOverlapPercent);
        }
    }

    /**
     * 客流类型数据同位置过滤 true -> 过滤
     * 
     * @param vo
     * @param coolingSeconds
     * @param posOverlapPercent
     * @return
     */
    private boolean ignoreSamePosFlowEvents(EventDto vo, int coolingSeconds, double posOverlapPercent) {
        JSONObject data = vo.getExtraData();
        if (data == null) {
            return false;
        }
        JSONArray position = data.getJSONArray("position");
        if (CollectionUtils.isEmpty(position) || position.size() < 4) {
            return false;
        }
        Long companyId = vo.getProjectId();
        String taskId = vo.getTaskCode();
        String eventType = vo.getEventType();
        String key = redisUtils.joinKey(CacheStatisticsEventRedisConstants.POS_KEY, companyId, taskId, eventType);
        Object value = redisUtils.get(key);
        if (!(value instanceof JSONArray)) {
            redisUtils.set(key, position, coolingSeconds);
            return false;
        }
        JSONArray positionBase = (JSONArray)value;
        log.info("base position -> {}", positionBase);
        if (positionBase.size() < 4) {
            redisUtils.set(key, position, coolingSeconds);
            return false;
        }
        double rate = this.calculateFlowRate(position, positionBase);
        log.info("ignoreSamePosFlowEvents start -> {}, {}, {}", eventType, rate, posOverlapPercent);
        if (rate > posOverlapPercent) {
            return true;
        } else {
            redisUtils.set(key, position, coolingSeconds);
            return false;
        }
    }

    private double calculateFlowRate(JSONArray position, JSONArray positionBase) {
        Integer leftUpX = position.getInteger(0);
        Integer leftUpY = position.getInteger(1);
        Integer rightBelowX = position.getInteger(2);
        Integer rightBelowY = position.getInteger(3);

        Integer leftUpXBase = positionBase.getInteger(0);
        Integer leftUpYBase = positionBase.getInteger(1);
        Integer rightBelowXBase = positionBase.getInteger(2);
        Integer rightBelowYBase = positionBase.getInteger(3);

        int innerLeftUpX = Math.max(leftUpX, leftUpXBase);
        int innerLeftUpY = Math.max(leftUpY, leftUpYBase);
        int innerRightBelowX = Math.min(rightBelowX, rightBelowXBase);
        int innerRightBelowY = Math.min(rightBelowY, rightBelowYBase);

        double innerArea = Math.max(0, innerRightBelowX - innerLeftUpX) * Math.max(0, innerRightBelowY - innerLeftUpY);
        double posArea = (rightBelowX - leftUpX) * (rightBelowY - leftUpY);
        double baseArea = (rightBelowXBase - leftUpXBase) * (rightBelowYBase - leftUpYBase);

        return innerArea / (posArea + baseArea - innerArea);
    }

    /**
     * true -> 位置相同，事件过滤 false -> 位置不相同，同时推送数据到redis
     * 
     * @param vo
     * @param coolingSeconds
     * @return
     */
    private boolean ignoreSamePosOtherEvents(EventDto vo, int coolingSeconds, double posOverlapPercent) {
        JSONArray snapshot =  vo.getSnapshot();
//        JSONArray snapshot = data.getJSONArray("snapshot");
        if (CollectionUtils.isEmpty(snapshot)) {
            return false;
        }
        JSONArray pts = snapshot.getJSONObject(0).getJSONArray("pts");
        if (!isRightPts(pts)) {
            return false;
        }
        Long companyId = vo.getProjectId();
        String taskId = vo.getTaskCode();
        String eventType = vo.getEventType();
        String key = redisUtils.joinKey(CacheStatisticsEventRedisConstants.POS_KEY, companyId, taskId, eventType);
        Object value = redisUtils.get(key);
        if (!(value instanceof JSONArray)) {
            redisUtils.set(key, pts, coolingSeconds);
            return false;
        }
        JSONArray ptsBase = (JSONArray)value;
        log.info("base position -> {}", ptsBase);
        if (!isRightPts(ptsBase)) {
            redisUtils.set(key, pts, coolingSeconds);
            return false;
        }
        double rate = this.calculateOtherRate(pts, ptsBase);
        log.info("ignoreSamePosEvents start -> {}, {}, {}", eventType, rate, posOverlapPercent);
        if (rate > posOverlapPercent) {
            return true;
        } else {
            redisUtils.set(key, pts, coolingSeconds);
            return false;
        }
    }

    /**
     * 计算重复率
     * 
     * @param pts
     * @param ptsBase
     * @return
     */
    private double calculateOtherRate(JSONArray pts, JSONArray ptsBase) {
        // base 坐标
        Integer ptsBaseX1 = ptsBase.getJSONArray(0).getInteger(0);
        Integer ptsBaseY1 = ptsBase.getJSONArray(0).getInteger(1);
        Integer ptsBaseX2 = ptsBase.getJSONArray(1).getInteger(0);
        Integer ptsBaseY2 = ptsBase.getJSONArray(1).getInteger(1);
        // pts坐标
        Integer ptsX1 = pts.getJSONArray(0).getInteger(0);
        Integer ptsY1 = pts.getJSONArray(0).getInteger(1);
        Integer ptsX2 = pts.getJSONArray(1).getInteger(0);
        Integer ptsY2 = pts.getJSONArray(1).getInteger(1);
        // 重叠位置坐标
        int xMin = Math.max(ptsBaseX1, ptsX1);
        int yMin = Math.max(ptsBaseY1, ptsY1);
        int xMax = Math.min(ptsBaseX2, ptsX2);
        int yMax = Math.min(ptsBaseY2, ptsY2);
        // 重叠位置面积
        double interArea = Math.max(0, xMax - xMin) * Math.max(0, yMax - yMin);
        double basePtsArea = (ptsBaseX2 - ptsBaseX1) * (ptsBaseY2 - ptsBaseY1);
        double ptsArea = (ptsX2 - ptsX1) * (ptsY2 - ptsY1);
        // 重复率
        return interArea / (basePtsArea + ptsArea - interArea);
    }

    /**
     * 检查pts格式
     * 
     * @param pts
     * @return false -> 违法格式
     */
    private boolean isRightPts(JSONArray pts) {
        if (CollectionUtils.isEmpty(pts) || pts.size() < 2) {
            return false;
        }
        JSONArray pts1 = pts.getJSONArray(0);
        if (CollectionUtils.isEmpty(pts1) || pts1.size() < 2) {
            log.info("illegal pts1");
            return false;
        }
        JSONArray pts2 = pts.getJSONArray(1);
        if (CollectionUtils.isEmpty(pts2) || pts2.size() < 2) {
            log.info("illegal pts2");
            return false;
        }
        return true;
    }

    /**
     * 连续变道夹角参数过滤 true -> 事件夹角小于目标夹角 -> 进入过滤列表
     * 
     * @param vo
     * @param eventsFilterConfig
     * @return
     */
    private boolean angleFilter(EventDto vo, JSONObject eventsFilterConfig) {
        String eventType = vo.getEventType();
        // 非连续变道算法
        if (!CONTINUOUS_LANE_CHANGE.equals(eventType)) {
            return false;
        }
        JSONObject angleFilter = eventsFilterConfig.getJSONObject("angleFilter");
        if (angleFilter == null) {
            return false;
        }
        // 目标夹角参数
        Double angle = angleFilter.getDouble("angle");
        if (angle == null) {
            return false;
        }
        JSONObject data = vo.getExtraData();
        if (data == null) {
            return false;
        }
        JSONArray snapshot = data.getJSONArray("snapshot");
        if (CollectionUtils.isEmpty(snapshot) || snapshot.size() < 3) {
            return false;
        }
        JSONObject snapshot2 = snapshot.getJSONObject(1);
        if (snapshot2 == null) {
            return false;
        }
        JSONObject origin = snapshot2.getJSONObject("origin");
        if (origin == null) {
            return false;
        }
        // 事件夹角参数
        Double directionAngleMin = origin.getDouble("direction_angle_min");
        if (directionAngleMin == null) {
            return false;
        }
        // 事件夹角小于目标夹角，进入过滤列表
        log.info("angleFilter start --> {}, {}, {}", eventType, directionAngleMin, angle);
        return directionAngleMin < angle;
    }

    /**
     * true -> 过滤 非机动车车型过滤
     * 
     * @param vo
     * @param eventsFilterConfig
     * @return
     */
    private boolean nonMotorVehicleFilter(EventDto vo, JSONObject eventsFilterConfig) {
        JSONArray nonMotorVehicleTypesArray = eventsFilterConfig.getJSONArray("nonMotorVehicleTypesFilter");
        if (CollectionUtils.isEmpty(nonMotorVehicleTypesArray)) {
            return false;
        }
        JSONObject data = vo.getExtraData();
        if (data == null) {
            return false;
        }
        JSONObject summary = data.getJSONObject("summary");
        if (summary == null) {
            return false;
        }
        String type = NULL_VALUE;
        JSONObject nonMotorType = summary.getJSONObject("nonmotor/type");
        if (nonMotorType != null) {
            String label = nonMotorType.getString("label");
            if (label != null) {
                type = label;
            }
        }
        String eventType = vo.getEventType();
        for (int i = 0; i < nonMotorVehicleTypesArray.size(); i++) {
            JSONObject nonMotorVehicleTypesFilter = nonMotorVehicleTypesArray.getJSONObject(i);
            JSONArray nonMotorVehicleTypes = nonMotorVehicleTypesFilter.getJSONArray("nonMotorVehicleTypes");
            if (CollectionUtils.isEmpty(nonMotorVehicleTypes)) {
                continue;
            }
            JSONArray eventTypes = nonMotorVehicleTypesFilter.getJSONArray("eventTypes");
            if (CollectionUtils.isEmpty(eventTypes) || eventTypes.contains(eventType)) {
                if (nonMotorVehicleTypes.contains(type)) {
                    return true;
                }
            }
        }
        return false;
    }

}
