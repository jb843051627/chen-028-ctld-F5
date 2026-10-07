package com.fc.v2.model.auto;

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
 * 农时催办一日去重账对象 t_ctld_task_dedup
 * 键只由本行地块码 + 事由名目两段接成；同一处地块、同一桩事由，一日内只递一回。
 * 只有真递出去的行进这本账；发不成的行不占去重名额，次日条件仍够还得再试。
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_ctld_task_dedup")
@ApiModel(value = "TCtldTaskDedup", description = "农时催办一日去重账")
public class TCtldTaskDedup implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 头一回占下这个键的条目 id */
    @TableField("item_id")
    @ApiModelProperty(value = "头一回占键的条目 id")
    private Long itemId;

    /** 地块码（键前段） */
    @TableField("plot_code")
    @ApiModelProperty(value = "地块码")
    private String plotCode;

    /** 事由名目（键后段） */
    @TableField("measure_name")
    @ApiModelProperty(value = "事由名目")
    private String measureName;

    /** 两段接成的键原文 */
    @TableField("dedup_key")
    @ApiModelProperty(value = "去重键原文")
    private String dedupKey;

    /** 占键那一日 */
    @TableField("dedup_day")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "占键那一日")
    private Date dedupDay;

    /** 占键这一回走的渠道 SMS/INNER */
    @TableField("channel")
    @ApiModelProperty(value = "走的渠道")
    private String channel;

    /** 占键的那一趟号 */
    @TableField("round_no")
    @ApiModelProperty(value = "趟号")
    private String roundNo;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getPlotCode() {
        return plotCode;
    }

    public void setPlotCode(String plotCode) {
        this.plotCode = plotCode;
    }

    public String getMeasureName() {
        return measureName;
    }

    public void setMeasureName(String measureName) {
        this.measureName = measureName;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public Date getDedupDay() {
        return dedupDay;
    }

    public void setDedupDay(Date dedupDay) {
        this.dedupDay = dedupDay;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(String roundNo) {
        this.roundNo = roundNo;
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
}
