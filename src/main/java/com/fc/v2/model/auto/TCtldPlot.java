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
 * 受污染耕地地块档案底册对象 t_ctld_plot
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_ctld_plot")
@ApiModel(value = "TCtldPlot", description = "受污染耕地地块档案底册")
public class TCtldPlot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 地块码 */
    @TableField("plot_no")
    @ApiModelProperty(value = "地块码")
    private String plotNo;

    /** 挂在哪一个管控分区名下 */
    @TableField("zone_id")
    @ApiModelProperty(value = "挂在哪一个管控分区名下")
    private Integer zoneId;

    /** 分区代号（冗余自分区册） */
    @TableField("zone_no")
    @ApiModelProperty(value = "分区代号（冗余自分区册）")
    private String zoneNo;

    /** 来路（省里下发／镇级补报／县级自录三条来路） */
    @TableField("src_kind")
    @ApiModelProperty(value = "来路（省里下发／镇级补报／县级自录三条来路）")
    private String srcKind;

    /** 核定应落措施项数 */
    @TableField("need_num")
    @ApiModelProperty(value = "核定应落措施项数")
    private Integer needNum;

    /** 已核验落实的项数 */
    @TableField("got_num")
    @ApiModelProperty(value = "已核验落实的项数")
    private Integer gotNum;

    /** 还差几项（轧出来，不留填数的空位） */
    @TableField("lack_num")
    @ApiModelProperty(value = "还差几项（轧出来，不留填数的空位）")
    private Integer lackNum;

    /** 随件交来的地块题名与主栽作物 */
    @TableField("content")
    @ApiModelProperty(value = "随件交来的地块题名与主栽作物")
    private String content;

    /** 册面情形 0新入册 1已核齐 2缺项 */
    @TableField("status")
    @ApiModelProperty(value = "册面情形 0新入册 1已核齐 2缺项")
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

    public String getPlotNo() {
        return plotNo;
    }

    public void setPlotNo(String plotNo) {
        this.plotNo = plotNo;
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

    public String getSrcKind() {
        return srcKind;
    }

    public void setSrcKind(String srcKind) {
        this.srcKind = srcKind;
    }

    public Integer getNeedNum() {
        return needNum;
    }

    public void setNeedNum(Integer needNum) {
        this.needNum = needNum;
    }

    public Integer getGotNum() {
        return gotNum;
    }

    public void setGotNum(Integer gotNum) {
        this.gotNum = gotNum;
    }

    public Integer getLackNum() {
        return lackNum;
    }

    public void setLackNum(Integer lackNum) {
        this.lackNum = lackNum;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
