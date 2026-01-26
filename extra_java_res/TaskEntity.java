package com.supremind.event.entity.task;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.supremind.base.utils.DateUtils;
import com.supremind.common.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 布控任务
 *
 * @author baiyonggang
 * @date 2022-11-01 17:44:28
 */
@Data
@TableName("task")
public class TaskEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 布控编号
     */
    private String code;

    /**
     * 布控任务名字
     */
    private String name;

    /**
     * 摄像头code
     */
    private String cameraCode;

    /**
     * 摄像头名称
     */
    private String cameraName;

    /**
     * 流地址
     */
    private String streamUrl;

    /**
     * 摄像头流地址 wsmp4
     */
    private String cameraUrl;

    /**
     * 效果流地址
     */
    private String effectStreamUrl;

    /**
     * 效果流内部服务地址
     */
    private String effectWriteStreamUrl;

    /**
     * 流视频截图
     */
    private String snapshot;

    /**
     * 任务类型 box 盒子任务  platform 平台任务   singleBall 单球
     */
    private String type;

    /**
     * 如果需要关联盒子检测任务，box_id不能为null，否则可以null
     */
    private Long boxId;

    /**
     * 盒子sn码
     */
    private String boxSn;

    /**
     * 被关联的盒子检测任务ID。对应此表中task_type为box的任务iD。可以为空
     */
    private String boxTaskId;

    /**
     * 摄像头所属组织ID
     */
    private Long orgId;

    /**
     * 摄像头所属组织名称
     */
    private String orgName;

    /**
     * 预置位，暂不清楚何用
     */
    private String preset;

    /**
     * 预置位名称
     */
    private String presetName;

    /**
     * 0 关闭推流  1  开启推流
     */
    private Integer streamOn;

    /**
     * 是否开启效果流  0 关闭  1  开启
     */
    private Integer featureOn;

    /**
     * 区域
     */
    private String region;

    /**
     *
     */
    private Long sceneId;

    /**
     * 布控任务开启状态 0 关闭  1  开启
     */
    private String status;

    /**
     * 0  摄像头类型  1  盒子压力类型  2  图片流类型
     */
    private Integer subType;

    /**
     * 取证视频开启关闭   0 开启 1 关闭
     */
    private Integer evidenceOn;

    /**
     * 未来时间计划，saas系统job控制
     */
    private JSONObject futureCrons;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     *
     */
    private String projectName;

    @TableField(value = "switched_time")
    @DateTimeFormat(pattern = DateUtils.DateTimeFormat)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    Date switchedTime;


    @TableField(value = "create_time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @TableField(value = "update_time")
    @DateTimeFormat(pattern = DateUtils.DateTimeFormat)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    @TableField(value = "create_by")
    private Integer createBy;

    @TableField(value = "update_by")
    private Integer updateBy;

    @TableField(value = "is_del")
    private Integer isDel;

    @TableField(typeHandler = JacksonTypeHandler.class, value = "extra")
    private JSONObject extra;

    private String ledList;
    private String soundList;
    private String weighList;
    private String cameraList;
    private String weighCode;


}
