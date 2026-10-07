package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 产地准出核验口径切换开关表对象 t_ctld_switch_cfg
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_ctld_switch_cfg")
@ApiModel(value = "TCtldSwitchCfg", description = "产地准出核验口径切换开关表")
public class TCtldSwitchCfg implements Serializable {
    private static final long serialVersionUID = 1L;
    /** 核验串（随版次走） */
    @TableField("content")
    @ApiModelProperty(value = "核验串（随版次走）")
    private String content;


    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 核验口径切换项代号（由系统排上，不留手填的格子） */
    @TableField("sw_no")
    @ApiModelProperty(value = "核验口径切换项代号（由系统排上，不留手填的格子）")
    private String swNo;

    /** 切换名目（同季配对回话／逐条人工签认／两式并行） */
    @TableField("sw_name")
    @ApiModelProperty(value = "切换名目（同季配对回话／逐条人工签认／两式并行）")
    private String swName;

    /** 开关三态 0一律走旧口径 1一律走新口径 2按桶推进 */
    @TableField("sw_kind")
    @ApiModelProperty(value = "开关三态 0一律走旧口径 1一律走新口径 2按桶推进")
    private Integer swKind;

    /** 开桶个数 0..10（按地块码尾数落桶，写 3 即尾数 0/1/2 走新口径） */
    @TableField("gray_num")
    @ApiModelProperty(value = "开桶个数 0..10（按地块码尾数落桶，写 3 即尾数 0/1/2 走新口径）")
    private Integer grayNum;

    /** 定档层级（省定／市定／县定／系统兜底） */
    @TableField("level_kind")
    @ApiModelProperty(value = "定档层级（省定／市定／县定／系统兜底）")
    private String levelKind;

    /** 继承自哪一项 */
    @TableField("parent_no")
    @ApiModelProperty(value = "继承自哪一项")
    private String parentNo;

    /** 挂在哪一个管控分区名下 */
    @TableField("zone_id")
    @ApiModelProperty(value = "挂在哪一个管控分区名下")
    private Integer zoneId;

    /** 适用的管控分区代号（冗余自分区册） */
    @TableField("zone_no")
    @ApiModelProperty(value = "适用的管控分区代号（冗余自分区册）")
    private String zoneNo;

    /** 分区全称（冗余一份，以分区册为准回写） */
    @TableField("zone_name")
    @ApiModelProperty(value = "分区全称（冗余一份，以分区册为准回写）")
    private String zoneName;

    /** 改动次序（数值大的先说话） */
    @TableField("ver_no")
    @ApiModelProperty(value = "改动次序（数值大的先说话）")
    private Integer verNo;

    /** 本项应覆盖处数 */
    @TableField("crt_num")
    @ApiModelProperty(value = "本项应覆盖处数")
    private Integer crtNum;

    /** 已切到新口径的处数 */
    @TableField("hit_num")
    @ApiModelProperty(value = "已切到新口径的处数")
    private Integer hitNum;

    /** 还差几处（轧出来，不留填数的空位） */
    @TableField("lack_num")
    @ApiModelProperty(value = "还差几处（轧出来，不留填数的空位）")
    private Integer lackNum;

    /** 启用那一一日 */
    @TableField("eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "启用那一一日")
    private Date effStart;

    /** 交班那一一日(不含) */
    @TableField("eff_end")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "交班那一一日(不含)")
    private Date effEnd;

    /** 行的情形 0在位 1已撤下 */
    @TableField("status")
    @ApiModelProperty(value = "行的情形 0在位 1已撤下")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSwNo() {
        return swNo;
    }

    public void setSwNo(String swNo) {
        this.swNo = swNo;
    }

    public String getSwName() {
        return swName;
    }

    public void setSwName(String swName) {
        this.swName = swName;
    }

    public Integer getSwKind() {
        return swKind;
    }

    public void setSwKind(Integer swKind) {
        this.swKind = swKind;
    }

    public Integer getGrayNum() {
        return grayNum;
    }

    public void setGrayNum(Integer grayNum) {
        this.grayNum = grayNum;
    }

    public String getLevelKind() {
        return levelKind;
    }

    public void setLevelKind(String levelKind) {
        this.levelKind = levelKind;
    }

    public String getParentNo() {
        return parentNo;
    }

    public void setParentNo(String parentNo) {
        this.parentNo = parentNo;
    }

    public Integer getZoneId() {
        return zoneId;
    }

    public void setZoneId(Integer zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneNo() {
        return zoneNo;
    }

    public void setZoneNo(String zoneNo) {
        this.zoneNo = zoneNo;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public Integer getVerNo() {
        return verNo;
    }

    public void setVerNo(Integer verNo) {
        this.verNo = verNo;
    }

    public Integer getCrtNum() {
        return crtNum;
    }

    public void setCrtNum(Integer crtNum) {
        this.crtNum = crtNum;
    }

    public Integer getHitNum() {
        return hitNum;
    }

    public void setHitNum(Integer hitNum) {
        this.hitNum = hitNum;
    }

    public Integer getLackNum() {
        return lackNum;
    }

    public void setLackNum(Integer lackNum) {
        this.lackNum = lackNum;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public Date getEffEnd() {
        return effEnd;
    }

    public void setEffEnd(Date effEnd) {
        this.effEnd = effEnd;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }


    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
