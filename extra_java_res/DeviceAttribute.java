package com.supremind.event.vo.camera;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Author baiyonggang
 * @Date 2022/11/9 1:28 下午
 */
@Data
public class DeviceAttribute implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("端口")
    private Integer port;

    @ApiModelProperty("传输协议")
    private Integer transport;

    @ApiModelProperty("设备名称")
    private String name;

    @ApiModelProperty(value = "对接方式 1：ONVIF 2：URL流地址 3：设备sdk 4：rtsp", example = "1：ONVIF 2：URL流地址 3：设备sdk 4:rtsp")
    private Integer discoveryProtocol;

    @ApiModelProperty("设备IP地址")
    private String ip;

    @ApiModelProperty("设备厂商")
    private Integer equipmentManufacturer;

    @ApiModelProperty("接流设备 1-硬盘录像机 2-摄像头")
    private Integer receivingEquipment;

    @ApiModelProperty("用户名")
    private String account;

    @ApiModelProperty("密码")
    private String password;

    @ApiModelProperty(value = "通道类型 1：模拟通道 2：数字通道", example = "1：模拟通道 2：数字通道")
    private Integer channelType;

    @ApiModelProperty(value = "设备内部通道号 起始值从1开始", example = "起始值从1开始")
    private Integer internalChannel;

    @ApiModelProperty("通道号")
    private Integer channelNo;

    @ApiModelProperty("流地址")
    private String upstreamUrl;

    @ApiModelProperty("码流类型 1-主码流 2-辅码流")
    private Integer streamType;

    @ApiModelProperty("摄像头厂商")
    private Integer vendor;

    @ApiModelProperty("描述内容")
    private String description;

    @ApiModelProperty("内网视频流播放地址")
    private String internalPlayUrl;

    @ApiModelProperty("外网视频流播放地址")
    private String publicPlayUrl;
    /**
     * 云台，控制摄像转动
     */
    @ApiModelProperty("摄像头类型：枪机、球机、海螺半球、其他")
    private Integer ptzType;

    @ApiModelProperty("放大倍数")
    private Integer zoom;

    @ApiModelProperty("球机PTZ")
    private List<Integer> ptz;

    @ApiModelProperty("球机PTZ onvif格式")
    private List<Float> onvifPtz;

    @ApiModelProperty("onvif 截图")
    private String snapUrl;

    @ApiModelProperty("视场角")
    private List<Float> fieldView;
}
