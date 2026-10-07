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
 * 农时催办早班趟账对象 t_ctld_task_dispatch
 * 一日一份；趟末三个数（挑中/递出/未成）与逐行记号同一次算定后落此，页面那一屏只是复述。
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_ctld_task_dispatch")
@ApiModel(value = "TCtldTaskDispatch", description = "农时催办早班趟账")
public class TCtldTaskDispatch implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 本轮所属那一一日（闸门内一日只认头一趟） */
    @TableField("run_date")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "本轮所属那一一日")
    private Date runDate;

    /** 头一趟动手那一刻（闸门内） */
    @TableField("run_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "头一趟动手那一刻")
    private Date runAt;

    /** 本趟闸是否开着 0闭 1开 */
    @TableField("gate_open")
    @ApiModelProperty(value = "本趟闸是否开着 0闭 1开")
    private Integer gateOpen;

    /** 趟末数一 */
    @TableField("picked_rows")
    @ApiModelProperty(value = "趟末数一：本轮挑中几行")
    private Integer pickedRows;

    /** 趟末数二 */
    @TableField("sent_rows")
    @ApiModelProperty(value = "趟末数二：实际递出几行")
    private Integer sentRows;

    /** 趟末数三 */
    @TableField("failed_rows")
    @ApiModelProperty(value = "趟末数三：几行未成")
    private Integer failedRows;

    /** 趟末回话原文 */
    @TableField("result_msg")
    @ApiModelProperty(value = "趟末回话原文")
    private String resultMsg;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
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

    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getRunDate() {
        return runDate;
    }

    public void setRunDate(Date runDate) {
        this.runDate = runDate;
    }

    public Date getRunAt() {
        return runAt;
    }

    public void setRunAt(Date runAt) {
        this.runAt = runAt;
    }

    public Integer getGateOpen() {
        return gateOpen;
    }

    public void setGateOpen(Integer gateOpen) {
        this.gateOpen = gateOpen;
    }

    public Integer getPickedRows() {
        return pickedRows;
    }

    public void setPickedRows(Integer pickedRows) {
        this.pickedRows = pickedRows;
    }

    public Integer getSentRows() {
        return sentRows;
    }

    public void setSentRows(Integer sentRows) {
        this.sentRows = sentRows;
    }

    public Integer getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(Integer failedRows) {
        this.failedRows = failedRows;
    }

    public String getResultMsg() {
        return resultMsg;
    }

    public void setResultMsg(String resultMsg) {
        this.resultMsg = resultMsg;
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
