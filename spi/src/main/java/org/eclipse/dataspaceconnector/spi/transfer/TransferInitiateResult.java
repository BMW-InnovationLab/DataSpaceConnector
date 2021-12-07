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

package org.eclipse.dataspaceconnector.spi.transfer;

import org.eclipse.dataspaceconnector.spi.result.AbstractResult;

public class TransferInitiateResult extends AbstractResult<String, ResponseFailure> {

    public static TransferInitiateResult success(String processId) {
        return new TransferInitiateResult(processId, null);
    }

    private TransferInitiateResult(String content, ResponseFailure failure) {
        super(content, failure);
    }

}
