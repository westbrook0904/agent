package com.helper.tempagent.template.ingestion;

import com.helper.tempagent.config.TemplateEngineProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PlaceholderTokenizer {

    private final Pattern placeholderPattern;

    public PlaceholderTokenizer(TemplateEngineProperties properties) {
        this.placeholderPattern = Pattern.compile(properties.getPlaceholderPattern());
    }

    public List<String> extract(String text) {
        Matcher matcher = placeholderPattern.matcher(text);
        List<String> placeholders = new ArrayList<>();
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return placeholders;
    }
}
