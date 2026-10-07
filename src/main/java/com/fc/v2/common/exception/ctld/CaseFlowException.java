package com.fc.v2.common.exception.ctld;

/**
 * 流转单办事规矩被拒时抛出。
 * code 给固定口径，msg 必须点到具体档/具体栏，不允许只回「信息不全」。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class CaseFlowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 4xx：来件本身不成立（缺件、越档、封口、重复按语等），整条留在原地 */
    public static final int CODE_REJECT = 460;
    /** 4xx：同件重递，头一笔已收，照头一笔回话 */
    public static final int CODE_IDEMPOTENT = 461;

    private final int code;

    public CaseFlowException(String message) {
        this(CODE_REJECT, message);
    }

    public CaseFlowException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
