package com.supremind.event.entity.event;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.supremind.common.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 事件
 * chenwenxu
 */
@TableName("event")
@Data
public class EventEntity implements Serializable {
    @TableId
    private Long id;//,
    // '布控编号'
    private String taskCode;
    // '布控任务'
    private String taskName;
    //'事件来源，老系统固定platform。新系统：box 盒子，platform 平台'
    private String source;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long projectId;// '项目ID',
    private String projectName;// '项目名称',
    private Long companyId;// '组织ID',
    private String companyName;// '组织名称',
    private String eventType;// '事件类型，对应算法code',
    private Long sceneId;//'场景ID',
    private String eventTypeName;// '事件类型名',
    private Date eventTime;// '预警时间，对应源数据中的updatedAt字段，如果updatedAt不存在，取timeStamp字段值',
    private Date endTime;// '事件结束时间',
    private String marking;// '预警 init   作废 discard  违法 event',
    private Long discardId;// '作废原因Id',
    private String engineEventId;//'引擎事件ID，通过此ID关联事件源数据，源数据丢到ES',
    private String vehicleType;// '车辆类型',
    private String plateNumber;// '车牌号',
    private String plateColor;//'车牌颜色',
    private String specialCarType;// '特种车辆类型',
    private String engineVersion;// '',
    private Long carInEvent;//'关联进场事件id，车辆进离场使用',
    @TableField(typeHandler = JacksonTypeHandler.class, value = "snapshot")
    private JSONArray snapshot;// '对应源数据snapshot数组字段',
    private String snapshotUriCompress;//'带框压缩图  存储源数据snapshot数组中第一个元素的snapshot_uri',
    private String snapshotUriRawCompress;// '不带框压缩图   基于源数据snapshot数组中第一个元素的snapshot_uri_raw压缩',
    private String snapshotUriCoverCompress;//'封面图    基于源数据snapshot数组中第一个元素的snapshot_uri高比例压缩',
    @TableField(typeHandler = JacksonTypeHandler.class, value = "extra_data")
    private JSONObject extraData;// '事件的额外数据',
    private Integer isDel;//'0' COMMENT '0 正常  1  删除',
    private String cameraCode;// '摄像头ID',
    private String evidenceStatus;// 'success开启  failed 关闭  blank未启用',
    private String evidenceUrl;//,
    private Integer originalViolationIndex;// '事件算法位置',
    @TableField(typeHandler = JacksonTypeHandler.class, value = "extra")
    private JSONObject extra;// '扩展字段，存放额外展示字段',
    @ApiModelProperty("过滤类型")
    private String filteredType;
    private Date markingTime;
    private Integer markingCount;
    private Date createTime;

    private Date updateTime;

    private Long createBy;

    private Long updateBy;

}
