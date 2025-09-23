package com.dream.codegenerate.model.enums;

import lombok.Getter;

/**
 * AI模型枚举类，包含不同模型及其相对价格
 */
@Getter
public enum AIModel {
    QWQ_32B("qwq-32b", 1f),
    GLM_4_5V("glm-4.5v", 10f),
    GLM_4_5_AIR("glm-4.5-air", 0.5f),
    GLM_4_5("glm-4.5", 2f),
    GEMINI_2_5_PRO("gemini-2.5-pro", 2f);

    private final String modelName;
    private final float price;

    /**
     * 构造函数
     * @param modelName 模型名称
     * @param price 权重值(0-1之间)
     */
    AIModel(String modelName, float price) {
        this.modelName = modelName;
        this.price = price;
    }

    public String getModelName() {
        return modelName;
    }

    public float getprice() {
        return price;
    }

    /**
     * 获取所有模型的总权重
     * @return 总权重值
     */
    public static float getTotalprice() {
        float total = 0f;
        for (AIModel model : values()) {
            total += model.getprice();
        }
        return total;
    }

    /**
     * 根据权重随机选择一个模型
     * @return 随机选择的模型
     */
    public static AIModel selectRandomModel() {
        float totalprice = getTotalprice();
        float random = (float) (Math.random() * totalprice);
        float cumulativeprice = 0f;

        for (AIModel model : values()) {
            cumulativeprice += model.getprice();
            if (random <= cumulativeprice) {
                return model;
            }
        }

        return GLM_4_5V; // 默认返回权重最高的模型
    }
}
