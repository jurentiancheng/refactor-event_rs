package com.supremind.event.vo.camera;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author baiyonggang
 * @Date 2022/11/15 10:11 上午
 */
@Data
public class DeviceAdditionAttribute {
    private static final long serialVersionUID = 1L;
    @ApiModelProperty("设备编号")
    private String deviceNo;
    @ApiModelProperty("所属区域")
    private String areas;
    @ApiModelProperty("经度")
    private BigDecimal longitude;
    @ApiModelProperty("纬度")
    private BigDecimal latitude;
    @ApiModelProperty("取证地点描述")
    private String addressDesc;
    @ApiModelProperty("道路编号")
    private String roadNo;
    @ApiModelProperty("协议类型 1-Onvif,2-URL,3-设备SDK")
    private String protocolType;
    @ApiModelProperty("IP地址")
    private String ipAddress;
    @ApiModelProperty("账号")
    private String account;
    @ApiModelProperty("密码")
    private String password;
    @ApiModelProperty("备用1")
    private String backUp1;
    @ApiModelProperty("备用2")
    private String backUp2;
}
