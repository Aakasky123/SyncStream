package com.syncstream.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

@Component
public class JsonUtil {
    public static final String EMPTY_DOC = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}";

    private final ObjectMapper mapper;

    public JsonUtil(ObjectMapper mapper) {
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String canonical(Object node) {
        try {
            return mapper.writeValueAsString(node == null ? emptyDocumentNode() : node);
        } catch (Exception ex) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid JSON content");
        }
    }

    public String objectToJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize JSON", ex);
        }
    }

    public Object emptyDocumentNode() {
        try {
            return mapper.readValue(EMPTY_DOC, Object.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid empty document JSON", ex);
        }
    }
}
