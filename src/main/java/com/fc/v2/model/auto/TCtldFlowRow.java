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
 * 土壤-农产品协同检测流水核收行对象 t_ctld_flow_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_ctld_flow_row")
@ApiModel(value = "TCtldFlowRow", description = "土壤-农产品协同检测流水核收行")
public class TCtldFlowRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 协同检测流水批次码 */
    @TableField("batch_no")
    @ApiModelProperty(value = "协同检测流水批次码")
    private String batchNo;

    /** 期数 1镇级上报 2县局复核 */
    @TableField("period_no")
    @ApiModelProperty(value = "期数 1镇级上报 2县局复核")
    private Integer periodNo;

    /** 本行进的是这一次递交里的第几拨（行不许漂拨） */
    @TableField("recv_seq")
    @ApiModelProperty(value = "本行进的是这一次递交里的第几拨")
    private Integer recvSeq;

    /** 原报行次 */
    @TableField("row_no")
    @ApiModelProperty(value = "原报行次")
    private Integer rowNo;

    /** 退回重报行当初那一个行次（重报不重新排队，无则同行次） */
    @TableField("orig_row_no")
    @ApiModelProperty(value = "退回重报行当初那一个行次")
    private Integer origRowNo;

    /** 被对上的地块码 */
    @TableField("item_code")
    @ApiModelProperty(value = "被对上的地块码")
    private String itemCode;

    /** 本行送检样品数 */
    @TableField("qty")
    @ApiModelProperty(value = "本行送检样品数")
    private BigDecimal qty;

    /** 本行配对达标数 */
    @TableField("chk_num")
    @ApiModelProperty(value = "本行配对达标数")
    private BigDecimal chkNum;

    /** 本行土壤镉实测数（高过当地告警线退回） */
    @TableField("cd_value")
    @ApiModelProperty(value = "本行土壤镉实测数")
    private BigDecimal cdValue;

    /** 样别 SOIL土壤样 PRODUCT农产品样 */
    @TableField("sample_kind")
    @ApiModelProperty(value = "样别 SOIL土壤样 PRODUCT农产品样")
    private String sampleKind;

    /** 收获季（同一处地块两季各报一行，各定各的去向） */
    @TableField("harvest_season")
    @ApiModelProperty(value = "收获季")
    private String harvestSeason;

    /** 来路（县农技中心直报／镇级补报两条来路） */
    @TableField("put_kind")
    @ApiModelProperty(value = "来路（县农技中心直报／镇级补报两条来路）")
    private String putKind;

    /** 行落地情形 0未勾 1已收下 2已退回 */
    @TableField("status")
    @ApiModelProperty(value = "行落地情形 0未勾 1已收下 2已退回")
    private Integer status;

    /** 退回条款码（收下/未勾为空） */
    @TableField("reject_code")
    @ApiModelProperty(value = "退回条款码")
    private String rejectCode;

    /** 退回条款原话（写明为哪一条退，逐行追溯） */
    @TableField("reject_msg")
    @ApiModelProperty(value = "退回条款原话")
    private String rejectMsg;

    /** 报文里的地块题名（只用来比对，不作准） */
    @TableField("msg_title")
    @ApiModelProperty(value = "报文里的地块题名")
    private String msgTitle;

    /** 报文里的分区代号（只用来比对，不作准） */
    @TableField("msg_zone_no")
    @ApiModelProperty(value = "报文里的分区代号")
    private String msgZoneNo;

    /** 底册带出的地块题名（行面一律以这一份作准） */
    @TableField("book_title")
    @ApiModelProperty(value = "底册带出的地块题名")
    private String bookTitle;

    /** 底册带出的分区代号（行面一律以这一份作准） */
    @TableField("book_zone_no")
    @ApiModelProperty(value = "底册带出的分区代号")
    private String bookZoneNo;

    /** 是否回核人事后定夺 0当场落定 1事后从待核定夺（不计入当场三栏） */
    @TableField("manual_settle")
    @ApiModelProperty(value = "是否回核人事后定夺")
    private Integer manualSettle;

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

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getChkNum() {
        return chkNum;
    }

    public void setChkNum(BigDecimal chkNum) {
        this.chkNum = chkNum;
    }

    public BigDecimal getCdValue() {
        return cdValue;
    }

    public void setCdValue(BigDecimal cdValue) {
        this.cdValue = cdValue;
    }

    public String getSampleKind() {
        return sampleKind;
    }

    public void setSampleKind(String sampleKind) {
        this.sampleKind = sampleKind;
    }

    public String getHarvestSeason() {
        return harvestSeason;
    }

    public void setHarvestSeason(String harvestSeason) {
        this.harvestSeason = harvestSeason;
    }

    public Integer getPeriodNo() {
        return periodNo;
    }

    public void setPeriodNo(Integer periodNo) {
        this.periodNo = periodNo;
    }

    public Integer getRecvSeq() {
        return recvSeq;
    }

    public void setRecvSeq(Integer recvSeq) {
        this.recvSeq = recvSeq;
    }

    public Integer getOrigRowNo() {
        return origRowNo;
    }

    public void setOrigRowNo(Integer origRowNo) {
        this.origRowNo = origRowNo;
    }

    public String getRejectCode() {
        return rejectCode;
    }

    public void setRejectCode(String rejectCode) {
        this.rejectCode = rejectCode;
    }

    public String getRejectMsg() {
        return rejectMsg;
    }

    public void setRejectMsg(String rejectMsg) {
        this.rejectMsg = rejectMsg;
    }

    public String getMsgTitle() {
        return msgTitle;
    }

    public void setMsgTitle(String msgTitle) {
        this.msgTitle = msgTitle;
    }

    public String getMsgZoneNo() {
        return msgZoneNo;
    }

    public void setMsgZoneNo(String msgZoneNo) {
        this.msgZoneNo = msgZoneNo;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public String getBookZoneNo() {
        return bookZoneNo;
    }

    public void setBookZoneNo(String bookZoneNo) {
        this.bookZoneNo = bookZoneNo;
    }

    public Integer getManualSettle() {
        return manualSettle;
    }

    public void setManualSettle(Integer manualSettle) {
        this.manualSettle = manualSettle;
    }

    public String getPutKind() {
        return putKind;
    }

    public void setPutKind(String putKind) {
        this.putKind = putKind;
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
