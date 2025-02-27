/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */

package org.eclipse.dataspaceconnector.spi.types.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An address such as a data source or destination.
 */
@JsonDeserialize(builder = DataAddress.Builder.class)
public class DataAddress {
    private static final String TYPE = "type";
    private static final String KEYNAME = "keyName";
    private final Map<String, String> properties = new HashMap<>();

    private DataAddress() {
    }

    @NotNull
    public String getType() {
        return properties.get(TYPE);
    }

    @JsonIgnore
    public void setType(String type) {
        Objects.requireNonNull(type);
        properties.put(TYPE, type);
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public String getKeyName() {
        return properties.get(KEYNAME);
    }

    @JsonIgnore
    public void setKeyName(String keyName) {
        Objects.requireNonNull(keyName);
        properties.put(KEYNAME, keyName);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final DataAddress address;

        private Builder() {
            address = new DataAddress();
        }

        @JsonCreator()
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder type(String type) {
            address.properties.put(TYPE, Objects.requireNonNull(type));
            return this;
        }

        public Builder property(String key, String value) {
            Objects.requireNonNull(key, "Property key null.");
            address.properties.put(key, value);
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            properties.forEach(this::property);
            return this;
        }

        public Builder keyName(String keyName) {
            address.getProperties().put(KEYNAME, Objects.requireNonNull(keyName));
            return this;
        }

        public DataAddress build() {
            Objects.requireNonNull(address.getType(), "DataAddress builder missing Type property.");
            return address;
        }
    }
}
