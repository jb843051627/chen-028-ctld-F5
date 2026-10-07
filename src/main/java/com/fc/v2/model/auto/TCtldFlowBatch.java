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
 * 协同检测流水拨账对象 t_ctld_flow_batch
 * 一拨一份；拨头三栏与行流水同源于一次点算，页面那一份只是复述。
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_ctld_flow_batch")
@ApiModel(value = "TCtldFlowBatch", description = "协同检测流水拨账")
public class TCtldFlowBatch implements Serializable {
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

    /** 同一次递交内的拨序（到顶另起：1..n） */
    @TableField("recv_seq")
    @ApiModelProperty(value = "拨序")
    private Integer recvSeq;

    /** 归一后的本拨容行上限 */
    @TableField("line_cap")
    @ApiModelProperty(value = "本拨容行上限")
    private Integer lineCap;

    /** 拨头抬头那一格原文 */
    @TableField("declared_cap")
    @ApiModelProperty(value = "拨头抬头原文")
    private String declaredCap;

    /** 报文切法 DELIM/FIXED */
    @TableField("parse_kind")
    @ApiModelProperty(value = "报文切法")
    private String parseKind;

    /** 回执栏一：进门行数（与逐行去向同一次点算） */
    @TableField("total_rows")
    @ApiModelProperty(value = "进门行数")
    private Integer totalRows;

    /** 回执栏二：收下行数 */
    @TableField("accepted_rows")
    @ApiModelProperty(value = "收下行数")
    private Integer acceptedRows;

    /** 回执栏三：退回行数 */
    @TableField("rejected_rows")
    @ApiModelProperty(value = "退回行数")
    private Integer rejectedRows;

    /** 空拨 0否 1是 */
    @TableField("empty_flag")
    @ApiModelProperty(value = "空拨标记")
    private Integer emptyFlag;

    /** 回执原话 */
    @TableField("receipt_text")
    @ApiModelProperty(value = "回执原话")
    private String receiptText;

    /** 删除标记 0正常 1删除（老行一条不减） */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    private String updateBy;

    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    @TableField("remark")
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

    public Integer getLineCap() {
        return lineCap;
    }

    public void setLineCap(Integer lineCap) {
        this.lineCap = lineCap;
    }

    public String getDeclaredCap() {
        return declaredCap;
    }

    public void setDeclaredCap(String declaredCap) {
        this.declaredCap = declaredCap;
    }

    public String getParseKind() {
        return parseKind;
    }

    public void setParseKind(String parseKind) {
        this.parseKind = parseKind;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getAcceptedRows() {
        return acceptedRows;
    }

    public void setAcceptedRows(Integer acceptedRows) {
        this.acceptedRows = acceptedRows;
    }

    public Integer getRejectedRows() {
        return rejectedRows;
    }

    public void setRejectedRows(Integer rejectedRows) {
        this.rejectedRows = rejectedRows;
    }

    public Integer getEmptyFlag() {
        return emptyFlag;
    }

    public void setEmptyFlag(Integer emptyFlag) {
        this.emptyFlag = emptyFlag;
    }

    public String getReceiptText() {
        return receiptText;
    }

    public void setReceiptText(String receiptText) {
        this.receiptText = receiptText;
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
