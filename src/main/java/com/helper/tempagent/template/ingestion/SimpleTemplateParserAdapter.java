package com.helper.tempagent.template.ingestion;

import com.helper.tempagent.config.TemplateEngineProperties;
import com.helper.tempagent.template.model.EachBlockNode;
import com.helper.tempagent.template.model.TemplateDocument;
import com.helper.tempagent.template.model.TemplateNode;
import com.helper.tempagent.template.model.TextNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SimpleTemplateParserAdapter implements TemplateParserAdapter {

    private final PlaceholderTokenizer tokenizer;
    private final Pattern eachStartPattern;
    private final String eachEndToken;

    public SimpleTemplateParserAdapter(PlaceholderTokenizer tokenizer, TemplateEngineProperties properties) {
        this.tokenizer = tokenizer;
        this.eachStartPattern = Pattern.compile(properties.getEachStartPattern());
        this.eachEndToken = properties.getEachEndToken();
    }

    @Override
    public TemplateDocument parse(String content) {
        List<TemplateNode> nodes = new ArrayList<>();
        Set<String> placeholders = new LinkedHashSet<>();

        int cursor = 0;
        while (cursor < content.length()) {
            Matcher matcher = eachStartPattern.matcher(content);
            if (!matcher.find(cursor)) {
                String tail = content.substring(cursor);
                nodes.add(new TextNode(tail));
                placeholders.addAll(tokenizer.extract(tail));
                break;
            }

            if (matcher.start() > cursor) {
                String head = content.substring(cursor, matcher.start());
                nodes.add(new TextNode(head));
                placeholders.addAll(tokenizer.extract(head));
            }

            int bodyStart = matcher.end();
            int endIndex = content.indexOf(eachEndToken, bodyStart);
            if (endIndex < 0) {
                throw new TemplateValidationException("Missing {{/each}} token for collection: " + matcher.group(1));
            }

            String collectionPath = matcher.group(1);
            String body = content.substring(bodyStart, endIndex);
            nodes.add(new EachBlockNode(collectionPath, body));
            placeholders.add(collectionPath);
            placeholders.addAll(tokenizer.extract(body));

            cursor = endIndex + eachEndToken.length();
        }

        return new TemplateDocument(nodes, new ArrayList<>(placeholders));
    }
}
