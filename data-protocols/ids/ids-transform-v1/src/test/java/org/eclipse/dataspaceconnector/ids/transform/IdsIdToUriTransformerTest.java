/*
 *  Copyright (c) 2021 Daimler TSS GmbH
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Daimler TSS GmbH - Initial Implementation
 *
 */

package org.eclipse.dataspaceconnector.ids.transform;

import org.eclipse.dataspaceconnector.ids.spi.IdsId;
import org.eclipse.dataspaceconnector.ids.spi.IdsType;
import org.eclipse.dataspaceconnector.ids.spi.transform.TransformerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdsIdToUriTransformerTest {

    private static final IdsType IDS_ID_TYPE = IdsType.ARTIFACT;
    private static final String IDS_ID_VALUE = "1c6865e0-80ca-4811-bcf0-fcad250b538f";

    private IdsIdToUriTransformer transformer;

    private IdsId idsId;
    private TransformerContext context;

    @BeforeEach
    public void setup() {
        transformer = new IdsIdToUriTransformer();
        idsId = mock(IdsId.class);
        context = mock(TransformerContext.class);
    }


    @Test
    void testThrowsNullPointerExceptionForAll() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            transformer.transform(null, null);
        });
    }

    @Test
    void testThrowsNullPointerExceptionForContext() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            transformer.transform(idsId, null);
        });
    }

    @Test
    void testReturnsNull() {
        var result = transformer.transform(null, context);

        Assertions.assertNull(result);
    }


    @Test
    void testSuccessfulSimple() {
        when(idsId.getType()).thenReturn(IDS_ID_TYPE);
        when(idsId.getValue()).thenReturn(IDS_ID_VALUE);

        var result = transformer.transform(idsId, context);

        Assertions.assertNotNull(result);
    }

}
