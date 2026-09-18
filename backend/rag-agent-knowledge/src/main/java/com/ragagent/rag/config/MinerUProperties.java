package com.ragagent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mineru")
public class MinerUProperties {

    /**
     * MinerU 服务基础地址 (如: http://192.168.100.90:8010)
     */
    private String baseUrl = "http://192.168.100.90:8010";

    /**
     * 语言列表 (如: ch)
     */
    private String lang = "ch";

    /**
     * 容器内部输出路径
     */
    private String outputDir = "/root/output";

    /**
     * 是否开启 LaTeX 公式识别
     */
    private Boolean enableFormula = true;

    /**
     * 是否开启复杂表格识别
     */
    private Boolean enableTable = true;

    /**
     * 后端解析器类型 (如: pipeline)
     */
    private String backend = "pipeline";

    /**
     * 是否返回提取图片 (设为 false 避免返回巨量 base64 污染文本切片)
     */
    private Boolean returnImages = false;

    /**
     * 超时时间 (毫秒)
     */
    private Integer timeout = 180000;
}
