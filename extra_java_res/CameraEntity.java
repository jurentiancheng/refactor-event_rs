package com.supremind.event.entity.camera;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.FastjsonTypeHandler;
import com.supremind.event.vo.camera.DeviceAdditionAttribute;
import com.supremind.event.vo.camera.DeviceAttribute;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 摄像头
 *
 * @author baiyonggang
 * @date 2022-11-02 13:54:45
 */
@Data
@TableName(value = "camera",autoResultMap = true)
public class CameraEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 摄像头编码
     */
    private String code;

    /**
     * 摄像头名称
     */
    private String name;

    /**
     * 摄像头类型(三方接入类型(国标平台 海康8200 第三方视频平台 直连))
     */
    private Integer type;

    /**
     * 摄像头状态
     */
    private Integer status;


    /**
     *
     */
    private Integer presetAble;

    /**
     * 是否可获取 0 Y  1 N
     */
    private Integer obtainAble;

    /**
     * 是否可设置 0 Y  1 N
     */
    private Integer setAble;

    /**
     * 是否抓拍机 0 Y 1 N
     */
    private Integer snapshotAble;

    /**
     * 缓存时长
     */
    private Integer cacheLimit;

    /**
     *
     */
    private JSONObject video;

    /**
     *
     */
    private JSONObject extra;

    /**
     *摄像头流状态
     */
    private Integer streamStatus;

    /**
     * 摄像头流最后时间
     */
    private Date streamTime;

    /**
     * 云台，控制摄像转动
     */
    private Integer ptzType;

    /**
     *
     */
    private Long boxId;

    private JSONObject boxSnap;

    private String boxSn;

    private String boxName;
    /**
     * 加 --摄像头别名 --chenwenxu
     */

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String aliasName;
    /**
     *
     */

    @TableField(typeHandler = FastjsonTypeHandler.class)
    private DeviceAttribute attribute;

    /**
     * 附加信息
     */
    @TableField(typeHandler = FastjsonTypeHandler.class)
    private DeviceAdditionAttribute additionAttribute;

    /**
     * 三方平台id
     */
    private Long thirdpartyId;

    /**
     *
     */
    private Long projectId;

    /**
     *
     */
    private String projectName;

    private Date createTime;

    private Date updateTime;

    private Long createBy;

    private Long updateBy;

    private Integer isDel;

}
