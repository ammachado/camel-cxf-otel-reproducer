/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.opentelemetry;

import java.util.EnumMap;
import java.util.Map;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import org.apache.camel.tracing.SpanAdapter;
import org.apache.camel.tracing.Tag;

public class OpenTelemetrySpanAdapter implements SpanAdapter {

    private static final String DEFAULT_EVENT_NAME = "log";
    private static final Map<Tag, String> TAG_MAP = new EnumMap<>(Tag.class);

    static {
        TAG_MAP.put(Tag.COMPONENT, "component");
        TAG_MAP.put(Tag.DB_TYPE, DbIncubatingAttributes.DB_SYSTEM.getKey());
        TAG_MAP.put(Tag.DB_STATEMENT, DbIncubatingAttributes.DB_STATEMENT.getKey());
        TAG_MAP.put(Tag.DB_INSTANCE, DbIncubatingAttributes.DB_NAME.toString());
        TAG_MAP.put(Tag.HTTP_METHOD, SemanticAttributes.HTTP_REQUEST_METHOD.getKey());
        TAG_MAP.put(Tag.HTTP_STATUS, SemanticAttributes.HTTP_RESPONSE_STATUS_CODE.getKey());
        TAG_MAP.put(Tag.HTTP_URL, SemanticAttributes.URL_FULL.getKey());
        TAG_MAP.put(Tag.MESSAGE_BUS_DESTINATION, SemanticAttributes.MESSAGING_DESTINATION_NAME.getKey());
    }

    private final OpenTelemetryTracer.Holder holder;
    private final io.opentelemetry.api.trace.Span span;

    OpenTelemetrySpanAdapter(OpenTelemetryTracer.Holder holder) {
        this.span = Span.fromContext(holder.getContext());
        this.holder = holder;
    }

    io.opentelemetry.api.trace.Span getOpenTelemetrySpan() {
        return this.span;
    }

    @Override
    public void setComponent(String component) {
        this.span.setAttribute("component", component);
    }

    @Override
    public void setError(boolean error) {
        this.span.setAttribute("error", error);
    }

    @Override
    public void setTag(Tag key, String value) {
        String attribute = TAG_MAP.getOrDefault(key, key.getAttribute());
        this.span.setAttribute(attribute, value);
        if (!attribute.equals(key.getAttribute())) {
            this.span.setAttribute(key.getAttribute(), value);
        }
    }

    @Override
    public void setTag(Tag key, Number value) {
        this.span.setAttribute(TAG_MAP.getOrDefault(key, key.getAttribute()), value.intValue());
    }

    @Override
    public void setTag(String key, String value) {
        this.span.setAttribute(key, value);
    }

    @Override
    public void setTag(String key, Number value) {
        this.span.setAttribute(key, value.intValue());
    }

    @Override
    public void setTag(String key, Boolean value) {
        this.span.setAttribute(key, value);
    }

    @Override
    public void log(Map<String, String> fields) {
        span.addEvent(getEventNameFromFields(fields), convertToAttributes(fields));
    }

    @Override
    public String traceId() {
        return span.getSpanContext().getTraceId();
    }

    @Override
    public String spanId() {
        return span.getSpanContext().getSpanId();
    }

    @Override
    public AutoCloseable makeCurrent() {
        return span.makeCurrent();
    }

    String getEventNameFromFields(Map<String, ?> fields) {
        Object eventValue = fields == null ? null : fields.get("event");
        if (eventValue != null) {
            return eventValue.toString();
        }

        return DEFAULT_EVENT_NAME;
    }

    Attributes convertToAttributes(Map<String, ?> fields) {
        AttributesBuilder attributesBuilder = Attributes.builder();

        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof Byte
                    || value instanceof Short
                    || value instanceof Integer
                    || value instanceof Long) {
                attributesBuilder.put(key, ((Number) value).longValue());
            } else if (value instanceof Float || value instanceof Double) {
                attributesBuilder.put(key, ((Number) value).doubleValue());
            } else if (value instanceof Boolean) {
                attributesBuilder.put(key, (Boolean) value);
            } else {
                attributesBuilder.put(key, value.toString());
            }
        }
        return attributesBuilder.build();
    }

    public Baggage getBaggage() {
        return holder.getBaggage();
    }

    public void setBaggage(Baggage baggage) {
        this.holder.setBaggage(baggage);
    }

    public void setCorrelationContextItem(String key, String value) {
        BaggageBuilder builder = this.holder.getBaggage().toBuilder().put(key, value);
        this.holder.setBaggage(builder.build());
    }

    public String getContextPropagationItem(String key) {
        return this.holder.getBaggage().getEntryValue(key);
    }
}
