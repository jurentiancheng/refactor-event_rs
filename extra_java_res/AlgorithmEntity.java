package com.supremind.event.entity.algorithm;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.FastjsonTypeHandler;
import com.supremind.event.vo.algorithm.LargeModelConfVo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 算法定义。租户算法关系表中算法启用禁用，受此算法的影响。
 *
 * @author baiyonggang
 * @date 2022-11-01 15:21:13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "algorithm", autoResultMap = true)
public class AlgorithmEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 算法代码
     */
    private String code;

    /**
     * 父算法代码
     */
    private String pcode;

    /**
     * 英文名
     */
    private String enname;

    /**
     * 中文名
     */
    private String cnname;

    /**
     * 0 启用  1 禁用
     */
    private Integer status;

    /**
     * 算法自带指导性配置
     */
    private JSONObject drawConfig;

    /**
     * 编辑配置
     */
    private JSONObject editableConfig;

    /**
     * 人审画框标记文本
     */
    private String label;

    /**
     * 人审图片绘图类型
     */
    private String drawType;

    private String description;

    private Integer isLargeModel;

    @TableField(typeHandler = FastjsonTypeHandler.class)
    private LargeModelConfVo largeModelConf;

    private String largeModelCodeRef;

    private Date createTime;

    private Date updateTime;

    private Long createBy;

    private Long updateBy;

    private Integer isDel;

}
