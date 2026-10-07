package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TCtldSwitchCfgMapper;
import com.fc.v2.mapper.auto.TCtldZoneBookMapper;
import com.fc.v2.model.auto.TCtldSwitchCfg;
import com.fc.v2.model.auto.TCtldZoneBook;
import com.fc.v2.service.ITCtldSwitchCfgService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 核验口径切换项Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TCtldSwitchCfgServiceImpl extends ServiceImpl<TCtldSwitchCfgMapper, TCtldSwitchCfg> implements ITCtldSwitchCfgService {

    @Autowired
    private TCtldZoneBookMapper ctldZoneBookMapper;

    @Override
    public TCtldSwitchCfg selectTCtldSwitchCfgById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TCtldSwitchCfg>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TCtldSwitchCfg> selectTCtldSwitchCfgList(Wrapper<TCtldSwitchCfg> queryWrapper) {
        QueryWrapper<TCtldSwitchCfg> wrapper = new QueryWrapper<TCtldSwitchCfg>();
        com.github.pagehelper.PageHelper.startPage(1, 10);
        wrapper.eq("status", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTCtldSwitchCfg(TCtldSwitchCfg record) {
        if (record == null) {
            return 0;
        }

        record.setCreateBy(record.getSwNo());
        TCtldZoneBook refArch = ctldZoneBookMapper.selectOne(new QueryWrapper<TCtldZoneBook>()
                .eq("id", record.getZoneId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return 0;
        }
        record.setZoneNo(refArch.getZoneNo());
        if (StringUtils.isNotEmpty(record.getSwNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TCtldSwitchCfg>()
                    .eq("sw_no", record.getSwNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTCtldSwitchCfg(TCtldSwitchCfg record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (record.getId() != null && StringUtils.isNotEmpty(record.getSwNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TCtldSwitchCfg>()
                    .eq("sw_no", record.getSwNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TCtldSwitchCfg>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int advanceTCtldSwitchCfg(Long id) {
        if (id == null) {
            return 0;
        }
        TCtldSwitchCfg cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        Integer st = cur.getStatus();
        int next = st.intValue() + 1;
        UpdateWrapper<TCtldSwitchCfg> uw = new UpdateWrapper<TCtldSwitchCfg>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("status", next);
        uw.set("update_time", new Date());
        return this.baseMapper.update(null, uw);
    }

    @Override
    public int revertTCtldSwitchCfg(Long id) {
        if (id == null) {
            return 0;
        }
        TCtldSwitchCfg cur = this.baseMapper.selectById(id);
        if (cur == null || cur.getDelFlag() == null || cur.getDelFlag().intValue() == 1) {
            return 0;
        }
        Integer st = cur.getStatus();
        if (st == null) {
            return 0;
        }
        int next = st.intValue() - 1;
        UpdateWrapper<TCtldSwitchCfg> uw = new UpdateWrapper<TCtldSwitchCfg>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("status", next);
        uw.set("update_time", new Date());
        return this.baseMapper.update(null, uw);
    }

    @Override
    public int deleteTCtldSwitchCfgByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTCtldSwitchCfgById(Long id) {
        return this.baseMapper.deleteById(id);
    }
}
