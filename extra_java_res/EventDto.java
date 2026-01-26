package com.supremind.event.dto.event;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.supremind.common.utils.DateUtils;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;

@Data
public class EventDto {
    private Long id;
    @ApiModelProperty("ids")
    private List<Long> ids;
    @ApiModelProperty("布控ID")
    private String taskCode;
    @ApiModelProperty("布控任务")
    private String taskName;
    @ApiModelProperty("事件来源，老系统固定platform。新系统：box 盒子，platform 平台")
    private String source;
    @ApiModelProperty("项目ID")
    private Long projectId;
    @ApiModelProperty("项目名称")
    private String projectName;
    @ApiModelProperty("组织ID")
    private Long companyId;
    @ApiModelProperty("组织名称")
    private String companyName;
    @ApiModelProperty("事件类型，对应算法code")
    private String eventType;
    @ApiModelProperty("场景ID")
    private Long sceneId;
    @ApiModelProperty("事件类型名")
    private String eventTypeName;
    @ApiModelProperty("预警时间，对应源数据中的updatedAt字段，如果updatedAt不存在，取timeStamp字段值")
    @DateTimeFormat(pattern = DateUtils.DateTimeFormat)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date eventTime;
    @ApiModelProperty("事件结束时间")
    @DateTimeFormat(pattern = DateUtils.DateTimeFormat)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;
    @ApiModelProperty(" '预警 init   作废 discard  违法 event',")
    private String marking;
    @ApiModelProperty(" 作废原因Id")
    private Long discardId;
    @ApiModelProperty("标记时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date markingTime;
    @ApiModelProperty(" 引擎事件ID，通过此ID关联事件源数据，源数据丢到ES")
    private String engineEventId;
    @ApiModelProperty("车辆类型")
    private String vehicleType;
    @ApiModelProperty("车牌号")
    private String plateNumber;
    @ApiModelProperty("车牌颜色")
    private String plateColor;
    @ApiModelProperty("特种车辆类型")
    private String specialCarType;
    @ApiModelProperty("引擎版本")
    private String engineVersion;
    @ApiModelProperty("关联进场事件id，车辆进离场使用")
    private Long carInEvent;
    @ApiModelProperty("对应源数据snapshot数组字段")
    private JSONArray snapshot;
    @ApiModelProperty("带框压缩图  存储源数据snapshot数组中第一个元素的snapshot_uri'")
    private String snapshotUriCompress;
    @ApiModelProperty("不带框压缩图   基于源数据snapshot数组中第一个元素的snapshot_uri_raw压缩'")
    private String snapshotUriRawCompress;
    @ApiModelProperty("'封面图    基于源数据snapshot数组中第一个元素的snapshot_uri高比例压缩'")
    private String snapshotUriCoverCompress;
    @ApiModelProperty("事件的额外数据[引擎端]")
    private JSONObject extraData;
    @ApiModelProperty("摄像头ID")
    private String cameraCode;
    @ApiModelProperty("摄像头名称")
    private String cameraCodeName;
    @ApiModelProperty("'success开启  failed 关闭  blank未启用'")
    private String evidenceStatus;
    @ApiModelProperty("'视频地址")
    private String evidenceUrl;
    @ApiModelProperty("'事件算法位置'")
    private Integer originalViolationIndex;
    @ApiModelProperty("'扩展字段，存放额外展示字段[中心端]")
    private JSONObject extra;
    @ApiModelProperty("过滤类型")
    private String filteredType;
    @ApiModelProperty("次数")
    private Integer markingCount;
}
