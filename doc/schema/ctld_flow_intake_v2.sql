-- ctld 土壤-农产品协同检测流水「四节核收」增量迁移
-- 与基线 doc/schema/ctld.sql 配套：本脚本只做增量，不碰既有的老收发口径（老的一套冻住，按镇收、按季收将来各另起一条）。
-- 口径：这一拨各行落到什么下场、同一批次码重到门口怎么办、整拨成不成、拨头上三个数从哪出来——四问只认后端核收这一处。
-- 库：chen_028

-- ============ 一、拨账：一拨打头一份（收拨与流水联动，同事务落） ============
-- 同一批次码两回（镇级上报、县局复核）各留一份流水，用 period_no 分期；
-- 到顶另起的拨用 recv_seq 接续；同一拨并着进两回，uk 挡第二遍，业务上照头一拨回话（幂等）。
CREATE TABLE IF NOT EXISTS t_ctld_flow_batch (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) NOT NULL COMMENT '协同检测流水批次码',
  period_no int NOT NULL COMMENT '期数 1镇级上报 2县局复核（两回各留一份流水，并起来期数才对得平）',
  recv_seq int NOT NULL COMMENT '同一次递交内的拨序（到顶即止，多出来的另起一拨：1..n）',
  line_cap int NOT NULL COMMENT '本拨容行上限（抬头空着/负数/离谱按300认，归一后落此处）',
  declared_cap varchar(64) DEFAULT NULL COMMENT '拨头抬头那一格的原文（日后翻得出当初写了什么）',
  parse_kind varchar(16) DEFAULT NULL COMMENT '报文切法 DELIM按分隔符 FIXED按定宽（一拨之内只认一种）',
  total_rows int NOT NULL COMMENT '回执栏一：几行进门（当场点出，与逐行去向同一次点算）',
  accepted_rows int NOT NULL COMMENT '回执栏二：几行收下',
  rejected_rows int NOT NULL COMMENT '回执栏三：几行退回',
  empty_flag int NOT NULL DEFAULT 0 COMMENT '空拨 0否 1是（空拨只落「空拨」，不出收讫一句）',
  receipt_text varchar(500) DEFAULT NULL COMMENT '回执原话（三栏的复述，数据源仍是本栏与行流水）',
  del_flag int NOT NULL DEFAULT 0 COMMENT '删除标记 0正常 1删除（老行一条不减）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_period_seq (batch_no, period_no, recv_seq),
  KEY idx_batch_period (batch_no, period_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协同检测流水拨账（一拨一份，三栏与行流水同源于一次点算）';

-- ============ 二、标注账：切不开/带不上编码/只到一个数/题名比对不合 ============
-- 这几类不进门、不算三栏，但不许装没看见，单列给回核的人逐行定去向，系统不替它收。
CREATE TABLE IF NOT EXISTS t_ctld_flow_row_mark (
  id bigint NOT NULL COMMENT '主键',
  batch_id bigint NOT NULL COMMENT '所属拨账 id',
  batch_no varchar(64) NOT NULL COMMENT '批次码',
  period_no int NOT NULL COMMENT '期数 1镇级上报 2县局复核',
  recv_seq int NOT NULL COMMENT '拨序',
  row_no int DEFAULT NULL COMMENT '能点出行次的记行次；整段带不上编码点不出的可空',
  row_id bigint DEFAULT NULL COMMENT '题名比对不合那类挂得到行的，记行 id；切不开/半行无行可挂为空',
  mark_kind varchar(32) NOT NULL COMMENT '标注类 UNSplittable切不开 ENCODING带不上编码 HALF只到一个数 TITLE_MISMATCH题名分区比对不合',
  mark_detail varchar(500) DEFAULT NULL COMMENT '点名说明（哪一刀没切开/混进了什么/缺哪个数）',
  raw_line varchar(1000) DEFAULT NULL COMMENT '报文原文（回核照原样看，不改字）',
  handled int NOT NULL DEFAULT 0 COMMENT '回核处置 0待核 1已定（系统不自作主张替它收）',
  del_flag int NOT NULL DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_mark_batch (batch_no, period_no, recv_seq),
  KEY idx_mark_pending (handled, mark_kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协同检测流水异常标注账（不进门，待回核逐行定去向）';

-- ============ 三、流水行增列（既有列一字不动，只增列） ============
ALTER TABLE t_ctld_flow_row
  ADD COLUMN sample_kind varchar(16) DEFAULT NULL COMMENT '样别 SOIL土壤样 PRODUCT农产品样（一行只承担一处地块一回配对里的一样）' AFTER put_kind,
  ADD COLUMN harvest_season varchar(32) DEFAULT NULL COMMENT '收获季（同一处地块两季各报一行，各定各的去向）' AFTER sample_kind,
  ADD COLUMN cd_value decimal(10,3) DEFAULT NULL COMMENT '本行土壤镉实测数（高过当地告警线退回）' AFTER chk_num,
  ADD COLUMN period_no int DEFAULT NULL COMMENT '期数 1镇级上报 2县局复核' AFTER batch_no,
  ADD COLUMN recv_seq int DEFAULT NULL COMMENT '本行进的是这一次递交里的第几拨（行不许漂拨）' AFTER period_no,
  ADD COLUMN orig_row_no int DEFAULT NULL COMMENT '退回重报行当初那一个行次（重报不重新排队，无则同行次）' AFTER row_no,
  ADD COLUMN reject_code varchar(32) DEFAULT NULL COMMENT '退回条款码（收下行为空）：CD_OVER_WARN/QTY_ZERO/QTY_NEGATIVE/CODE_EMPTY/CODE_TOO_SHORT/CODE_TOO_LONG' AFTER status,
  ADD COLUMN reject_msg varchar(255) DEFAULT NULL COMMENT '退回条款原话（写明为哪一条退，逐行追溯）' AFTER reject_code,
  ADD COLUMN msg_title varchar(255) DEFAULT NULL COMMENT '报文里的地块题名（只用来比对，不作准）' AFTER reject_msg,
  ADD COLUMN msg_zone_no varchar(64) DEFAULT NULL COMMENT '报文里的分区代号（只用来比对，不作准）' AFTER msg_title,
  ADD COLUMN book_title varchar(255) DEFAULT NULL COMMENT '底册带出的地块题名（行面一律以这一份作准）' AFTER msg_zone_no,
  ADD COLUMN book_zone_no varchar(64) DEFAULT NULL COMMENT '底册带出的分区代号（行面一律以这一份作准）' AFTER book_title,
  ADD COLUMN manual_settle int NOT NULL DEFAULT 0 COMMENT '是否回核人事后逐行定夺 0当场点算落定 1事后从待核定夺（不计入拨头当场三栏）' AFTER book_zone_no,
  ADD KEY idx_row_batch (batch_no, period_no, recv_seq),
  ADD KEY idx_row_status (status, del_flag);
