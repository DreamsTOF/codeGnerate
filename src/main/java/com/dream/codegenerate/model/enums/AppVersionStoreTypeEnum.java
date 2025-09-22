package com.dream.codegenerate.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;
/**
 * 应用版本存储枚举类型
 */
@Getter
public enum AppVersionStoreTypeEnum {
    FULL("全量", "full"),
    DIFF("增量", "diff");

    private final String text;

    private final String value;

    AppVersionStoreTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static AppVersionStoreTypeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (AppVersionStoreTypeEnum anEnum : AppVersionStoreTypeEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
