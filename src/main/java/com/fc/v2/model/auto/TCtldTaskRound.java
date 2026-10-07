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
 * 农时催办早班翻单趟账对象 t_ctld_task_round
 * 一趟一份；趟尾三栏（挑中/递出/未成）与逐行记号同一次算定，页面那一屏只是复述。
 * round_no 是同一轮的唯一凭据：第二回认这个号照头一趟回话，不另落一趟、不动一行。
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_ctld_task_round")
@ApiModel(value = "TCtldTaskRound", description = "农时催办早班翻单趟账")
public class TCtldTaskRound implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 趟号（同一轮的唯一凭据） */
    @TableField("round_no")
    @ApiModelProperty(value = "趟号")
    private String roundNo;

    /** 本趟窗口开不开 0闸门外未动单 1闸门内动了单 */
    @TableField("gate_open")
    @ApiModelProperty(value = "窗口开不开")
    private Integer gateOpen;

    /** 趟尾数栏一：挑中几行 */
    @TableField("picked_rows")
    @ApiModelProperty(value = "挑中几行")
    private Integer pickedRows;

    /** 趟尾数栏二：递出几行 */
    @TableField("sent_rows")
    @ApiModelProperty(value = "递出几行")
    private Integer sentRows;

    /** 趟尾数栏三：几行未成 */
    @TableField("failed_rows")
    @ApiModelProperty(value = "几行未成")
    private Integer failedRows;

    /** 文面缺件按兵不发几行 */
    @TableField("held_rows")
    @ApiModelProperty(value = "缺件按住几行")
    private Integer heldRows;

    /** 被一日去重挡下几行 */
    @TableField("dedup_skipped_rows")
    @ApiModelProperty(value = "去重挡下几行")
    private Integer dedupSkippedRows;

    /** 其中走短信几条 */
    @TableField("sms_rows")
    @ApiModelProperty(value = "走短信几条")
    private Integer smsRows;

    /** 其中回落站内几条 */
    @TableField("inner_rows")
    @ApiModelProperty(value = "回落站内几条")
    private Integer innerRows;

    /** 触发那一刻 */
    @TableField("run_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "触发那一刻")
    private Date runAt;

    /** 趟子收尾那一刻 */
    @TableField("finish_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "收尾那一刻")
    private Date finishAt;

    /** 趟尾回话原话 */
    @TableField("reply_text")
    @ApiModelProperty(value = "趟尾回话原话")
    private String replyText;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
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

    public String getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(String roundNo) {
        this.roundNo = roundNo;
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

    public Integer getHeldRows() {
        return heldRows;
    }

    public void setHeldRows(Integer heldRows) {
        this.heldRows = heldRows;
    }

    public Integer getDedupSkippedRows() {
        return dedupSkippedRows;
    }

    public void setDedupSkippedRows(Integer dedupSkippedRows) {
        this.dedupSkippedRows = dedupSkippedRows;
    }

    public Integer getSmsRows() {
        return smsRows;
    }

    public void setSmsRows(Integer smsRows) {
        this.smsRows = smsRows;
    }

    public Integer getInnerRows() {
        return innerRows;
    }

    public void setInnerRows(Integer innerRows) {
        this.innerRows = innerRows;
    }

    public Date getRunAt() {
        return runAt;
    }

    public void setRunAt(Date runAt) {
        this.runAt = runAt;
    }

    public Date getFinishAt() {
        return finishAt;
    }

    public void setFinishAt(Date finishAt) {
        this.finishAt = finishAt;
    }

    public String getReplyText() {
        return replyText;
    }

    public void setReplyText(String replyText) {
        this.replyText = replyText;
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
