package com.supremind.event.entity.configuration;

import java.io.Serializable;
import java.util.Date;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.TableName;
import com.supremind.common.entity.BaseEntity;
import lombok.Data;

/**
 * 事件过滤配置
 *
 * @author wzhua
 * @date 2022-11-14 14:41:22
 */
@Data
@TableName("event_filter_config")
public class EventFilterConfigEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     *
     */

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 配置分组
     */
    private String settingGroup;

    /**
     * 分组名
     */
    private String groupName;

    /**
     *
     */
    private JSONObject config;

    private Integer sort;

    private Date createTime;

    private Date updateTime;

    private Long createBy;

    private Long updateBy;

    private Integer isDel;

}
