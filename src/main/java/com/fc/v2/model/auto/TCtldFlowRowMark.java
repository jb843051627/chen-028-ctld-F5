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
 * 协同检测流水异常标注账对象 t_ctld_flow_row_mark
 * 切不开/带不上编码/只到一个数/题名比对不合：不进门、不进三栏，单列给回核逐行定去向。
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_ctld_flow_row_mark")
@ApiModel(value = "TCtldFlowRowMark", description = "协同检测流水异常标注账")
public class TCtldFlowRowMark implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属拨账 id */
    @TableField("batch_id")
    @ApiModelProperty(value = "所属拨账 id")
    private Long batchId;

    /** 批次码 */
    @TableField("batch_no")
    @ApiModelProperty(value = "批次码")
    private String batchNo;

    /** 期数 */
    @TableField("period_no")
    @ApiModelProperty(value = "期数")
    private Integer periodNo;

    /** 拨序 */
    @TableField("recv_seq")
    @ApiModelProperty(value = "拨序")
    private Integer recvSeq;

    /** 行次（点不出的可空） */
    @TableField("row_no")
    @ApiModelProperty(value = "行次")
    private Integer rowNo;

    /** 挂得到行的记行 id */
    @TableField("row_id")
    @ApiModelProperty(value = "行 id")
    private Long rowId;

    /** 标注类 UNSPLITTABLE/ENCODING/HALF_NUM/TITLE_MISMATCH */
    @TableField("mark_kind")
    @ApiModelProperty(value = "标注类")
    private String markKind;

    /** 点名说明 */
    @TableField("mark_detail")
    @ApiModelProperty(value = "点名说明")
    private String markDetail;

    /** 报文原文 */
    @TableField("raw_line")
    @ApiModelProperty(value = "报文原文")
    private String rawLine;

    /** 回核处置 0待核 1已定 */
    @TableField("handled")
    @ApiModelProperty(value = "回核处置 0待核 1已定")
    private Integer handled;

    /** 删除标记 */
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

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
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

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public Long getRowId() {
        return rowId;
    }

    public void setRowId(Long rowId) {
        this.rowId = rowId;
    }

    public String getMarkKind() {
        return markKind;
    }

    public void setMarkKind(String markKind) {
        this.markKind = markKind;
    }

    public String getMarkDetail() {
        return markDetail;
    }

    public void setMarkDetail(String markDetail) {
        this.markDetail = markDetail;
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }

    public Integer getHandled() {
        return handled;
    }

    public void setHandled(Integer handled) {
        this.handled = handled;
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
