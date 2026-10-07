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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 超标地块纳入受限与解除办件流转单对象 t_ctld_case_flow
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_ctld_case_flow")
@ApiModel(value = "TCtldCaseFlow", description = "超标地块纳入受限与解除办件流转单")
public class TCtldCaseFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 超标地块纳入受限与解除的办件件次 */
    @TableField("biz_no")
    @ApiModelProperty(value = "超标地块纳入与解除的办件件次")
    private String bizNo;

    /** 地块码（起件四要件之一） */
    @TableField("plot_no")
    @ApiModelProperty(value = "地块码（起件四要件之一）")
    private String plotNo;

    /** 所属分区代号（起件四要件之二，对分区册） */
    @TableField("zone_no")
    @ApiModelProperty(value = "所属分区代号（起件四要件之二）")
    private String zoneNo;

    /** 土壤镉实测值（起件四要件之三） */
    @TableField("cd_value")
    @ApiModelProperty(value = "土壤镉实测值（起件四要件之三）")
    private BigDecimal cdValue;

    /** 拟采用的措施名目（起件四要件之四） */
    @TableField("measure_name")
    @ApiModelProperty(value = "拟采用的措施名目（起件四要件之四）")
    private String measureName;

    /** 递件人（本人方可收回反映） */
    @TableField("submitter")
    @ApiModelProperty(value = "递件人（本人方可收回反映）")
    private String submitter;

    /** 当前关次 0..3（初核受理/纳入受限/措施核查/解除复核，档数后端从履历轧出） */
    @TableField("stage")
    @ApiModelProperty(value = "当前关次 0..3（初核受理/纳入受限/措施核查/解除复核）")
    private Integer stage;

    /** 单子走到哪一关 0未起 1在办 2已封住 */
    @TableField("status")
    @ApiModelProperty(value = "单子走到哪一关 0未起 1在办 2已封住")
    private Integer status;

    /** 一关一记 */
    @TableField("content")
    @ApiModelProperty(value = "一关一记")
    private String content;

    /** 当前档留下的那一句按语（一档只许一句，二次投来不覆不存） */
    @TableField("stage_note")
    @ApiModelProperty(value = "当前档按语（一档一句，二次投来不覆不存）")
    private String stageNote;

    /** 纳入受限档核收 0未核 1可 2不可（不记可不许开工） */
    @TableField("limit_pass")
    @ApiModelProperty(value = "纳入受限档核收 0未核 1可 2不可")
    private Integer limitPass;

    /** 解除复核见证岗第一笔 */
    @TableField("witness1")
    @ApiModelProperty(value = "解除复核见证岗第一笔")
    private String witness1;

    /** 解除复核见证岗第二笔（须与第一笔不同岗） */
    @TableField("witness2")
    @ApiModelProperty(value = "解除复核见证岗第二笔（须与第一笔不同岗）")
    private String witness2;

    /** 措施核验凭证串（写回本行方闭解除复核门槛） */
    @TableField("voucher_no")
    @ApiModelProperty(value = "措施核验凭证串（写回本行方闭门槛）")
    private String voucherNo;

    /** 收口情形 1走满末档 2递件人收回 3分区并走 NULL尚未收口 */
    @TableField("close_kind")
    @ApiModelProperty(value = "收口情形 1走满末档 2收回 3并走 NULL未收口")
    private Integer closeKind;

    /** 本件累计入档笔数（后端从履历轧出，不信报文序号） */
    @TableField("enter_count")
    @ApiModelProperty(value = "本件累计入档笔数（后端轧出）")
    private Integer enterCount;

    /** 最近一次挪关动作 */
    @TableField("last_action")
    @ApiModelProperty(value = "最近一次挪关动作")
    private String lastAction;

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

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getPlotNo() {
        return plotNo;
    }

    public void setPlotNo(String plotNo) {
        this.plotNo = plotNo;
    }

    public String getZoneNo() {
        return zoneNo;
    }

    public void setZoneNo(String zoneNo) {
        this.zoneNo = zoneNo;
    }

    public BigDecimal getCdValue() {
        return cdValue;
    }

    public void setCdValue(BigDecimal cdValue) {
        this.cdValue = cdValue;
    }

    public String getMeasureName() {
        return measureName;
    }

    public void setMeasureName(String measureName) {
        this.measureName = measureName;
    }

    public String getSubmitter() {
        return submitter;
    }

    public void setSubmitter(String submitter) {
        this.submitter = submitter;
    }

    public String getStageNote() {
        return stageNote;
    }

    public void setStageNote(String stageNote) {
        this.stageNote = stageNote;
    }

    public Integer getLimitPass() {
        return limitPass;
    }

    public void setLimitPass(Integer limitPass) {
        this.limitPass = limitPass;
    }

    public String getWitness1() {
        return witness1;
    }

    public void setWitness1(String witness1) {
        this.witness1 = witness1;
    }

    public String getWitness2() {
        return witness2;
    }

    public void setWitness2(String witness2) {
        this.witness2 = witness2;
    }

    public String getVoucherNo() {
        return voucherNo;
    }

    public void setVoucherNo(String voucherNo) {
        this.voucherNo = voucherNo;
    }

    public Integer getCloseKind() {
        return closeKind;
    }

    public void setCloseKind(Integer closeKind) {
        this.closeKind = closeKind;
    }

    public Integer getEnterCount() {
        return enterCount;
    }

    public void setEnterCount(Integer enterCount) {
        this.enterCount = enterCount;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
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

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
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
