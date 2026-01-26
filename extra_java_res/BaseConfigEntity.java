package com.supremind.event.entity.configuration;

import java.util.Date;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.TableName;
import com.supremind.common.entity.BaseEntity;
import lombok.Data;

/**
 * @author wzhua
 * @date 2022-11-15 17:42:48
 */
@Data
@TableName("base_config")
public class BaseConfigEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     *
     */

    /**
     * 公司Id
     */
    private Integer companyId;

    /**
     * 项目ID
     */
    private Integer projectId;

    /**
     * key
     */
    private String code;

    /**
     * 配置
     */
    private JSONObject config;

    private Date createTime;

    private Date updateTime;

    private Long createBy;

    private Long updateBy;

    private Integer isDel;

}
