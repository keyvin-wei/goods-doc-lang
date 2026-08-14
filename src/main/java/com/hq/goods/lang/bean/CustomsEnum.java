package com.hq.goods.lang.bean;

/**
 * @Description 通用枚举类
 * @Author weiwenhan
 * @Date 2025/2/26 13:40
 */
public enum CustomsEnum {
    //目标语言：1英语，2简体中文，3繁体中文，4日语，5俄语
    LANG_EN(1, "英语"),
    LANG_ZH(2, "简体中文"),
    LANG_ZH_TW(3, "繁体中文"),
    LANG_JP(4, "日语"),
    LANG_RU(5, "俄语"),

    LANG_SOURCE_0(0, "正常"),
    LANG_SOURCE_1(1, "EQ异常工单"),

    //海关文件解析状态
    IMPORT_STATUS1(1, "导入中"),
    IMPORT_STATUS2(2, "导入完成"),
    IMPORT_STATUS3(3, "导入失败"),

    //报关状态
    DECLARE_CUSTOMS_STATUS0(0, "待报关"),
    DECLARE_CUSTOMS_STATUS1(1, "已报关"),

    ITEM_IDENTIFY0(0, "识别：否"),
    ITEM_IDENTIFY1(1, "识别：是"),

    PCB_STATUS_XD(0x00001, "下单"),
    PCB_STATUS_SH(0x00002, "审核通过"),
    PCB_STATUS_BTG(0x00004, "审核不通过"),

    //邮件类型：1收件人邮箱 2抄送人邮箱
    SEND_QUEUE_EMAIL_1(1, "收件人邮箱"),
    SEND_QUEUE_EMAIL_2(2, "抄送人邮箱"),
    //邮件通知状态：0未通知，1已通知
    NOTICE_FLAG_0(0, "0未通知"),
    NOTICE_FLAG_1(1, "1已通知"),

    //DFA状态：-1-已取消,1-待审核,2-审核不通过,3-审核通过
    DFA_AUDIT_STATE_N1(-1,"已取消"),
    DFA_AUDIT_STATE_1(1,"待审核"),
    DFA_AUDIT_STATE_2(2,"审核不通过"),
    DFA_AUDIT_STATE_3(3,"审核通过"),

    //分析状态 1分析中 2分析完成 5全部忽略 6解析参数与下单参数完全一致 7解析参数与下单参数部分或全部不一致  9解析参数包含我司不支持
    PCB_ANALYSE_STATUS_1(1,"分析中"),
    PCB_ANALYSE_STATUS_2(2,"分析完成"),
    PCB_ANALYSE_STATUS_5(5,"全部忽略"),
    PCB_ANALYSE_STATUS_6(6,"解析参数与下单参数完全一致"),
    PCB_ANALYSE_STATUS_7(7,"解析参数与下单参数部分或全部不一致"),
    PCB_ANALYSE_STATUS_9(9,"解析参数包含我司不支持"),
    ;

    private Integer code;
    private String name;

    CustomsEnum(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    public Integer getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    //根据code查询目标语言
    public static String getLangName(Integer code) {
        for (CustomsEnum customsEnum : CustomsEnum.values()) {
            if (customsEnum.name().startsWith("LANG_") && customsEnum.getCode().equals(code)) {
                return customsEnum.getName();
            }
        }
        return LANG_EN.getName();
    }

    /**
     * pcb状态对比，status是不是包含target状态；
     * status和target相‘与’为0就是true
     * @return
     */
    public static boolean checkPcbStatus(Integer status, CustomsEnum target) {
        if(status==null||target==null){
            return false;
        }
        int code = status & target.getCode();
        return code == 0;
    }
}
