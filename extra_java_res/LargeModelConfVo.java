package com.supremind.event.vo.algorithm;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @ClassName LargeModeConfVo
 * @Description TODO
 * @Author LX
 * @Date 2025/1/2 15:34
 */
@Data
public class LargeModelConfVo implements Serializable {

    private String question;

    private KeyMatcherVo keyMatcher;

    private Integer reqTimeout;

    private Integer reqRetryCount;

    @Data
    public static class KeyMatcherVo {
        private List<String> discardKeys;
        private List<String> matchKeys;
    }
}
