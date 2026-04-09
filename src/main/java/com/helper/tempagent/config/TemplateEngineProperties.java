package com.helper.tempagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tempagent.template")
public class TemplateEngineProperties {

    private String placeholderPattern;
    private String eachStartPattern;
    private String eachEndToken;
    private MissingValuePolicy missingValuePolicy = MissingValuePolicy.KEEP_TOKEN;

    public String getPlaceholderPattern() {
        return placeholderPattern;
    }

    public void setPlaceholderPattern(String placeholderPattern) {
        this.placeholderPattern = placeholderPattern;
    }

    public String getEachStartPattern() {
        return eachStartPattern;
    }

    public void setEachStartPattern(String eachStartPattern) {
        this.eachStartPattern = eachStartPattern;
    }

    public String getEachEndToken() {
        return eachEndToken;
    }

    public void setEachEndToken(String eachEndToken) {
        this.eachEndToken = eachEndToken;
    }

    public MissingValuePolicy getMissingValuePolicy() {
        return missingValuePolicy;
    }

    public void setMissingValuePolicy(MissingValuePolicy missingValuePolicy) {
        this.missingValuePolicy = missingValuePolicy;
    }
}
