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

package org.eclipse.dataspaceconnector.junit;

import org.easymock.EasyMock;
import org.eclipse.dataspaceconnector.junit.launcher.EdcExtension;
import org.eclipse.dataspaceconnector.security.NullVaultExtension;
import org.eclipse.dataspaceconnector.spi.iam.ClaimToken;
import org.eclipse.dataspaceconnector.spi.iam.IdentityService;
import org.eclipse.dataspaceconnector.spi.iam.TokenRepresentation;
import org.eclipse.dataspaceconnector.spi.message.MessageContext;
import org.eclipse.dataspaceconnector.spi.message.RemoteMessageDispatcher;
import org.eclipse.dataspaceconnector.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.dataspaceconnector.spi.result.Result;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;
import org.eclipse.dataspaceconnector.spi.system.VaultExtension;
import org.eclipse.dataspaceconnector.spi.transfer.TransferProcessManager;
import org.eclipse.dataspaceconnector.spi.transfer.flow.DataFlowController;
import org.eclipse.dataspaceconnector.spi.transfer.flow.DataFlowInitiateResult;
import org.eclipse.dataspaceconnector.spi.transfer.flow.DataFlowManager;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;
import org.eclipse.dataspaceconnector.spi.types.domain.message.RemoteMessage;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@ExtendWith(EdcExtension.class)
public class EndToEndTest {

    @Test
    void processConsumerRequest(TransferProcessManager processManager, RemoteMessageDispatcherRegistry dispatcherRegistry) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        RemoteMessageDispatcher dispatcher = EasyMock.createMock(RemoteMessageDispatcher.class);

        dispatcher.protocol();
        EasyMock.expectLastCall().andReturn("ids-rest");

        EasyMock.expect(dispatcher.send(EasyMock.notNull(), EasyMock.isA(RemoteMessage.class), EasyMock.isA(MessageContext.class))).andAnswer(() -> {
            var future = new CompletableFuture<>();
            future.complete(null);
            latch.countDown();
            return future;
        });

        EasyMock.replay(dispatcher);

        dispatcherRegistry.register(dispatcher);

        var artifactId = "test123";
        var connectorId = "https://test";

        var entry = Asset.Builder.newInstance().id(artifactId).build();
        var request = DataRequest.Builder.newInstance().protocol("ids-rest").assetId(entry.getId())
                .connectorId(connectorId).connectorAddress(connectorId).destinationType("S3").build();

        processManager.initiateConsumerRequest(request);

        latch.await(1, TimeUnit.MINUTES);

        EasyMock.verify(dispatcher);
    }

    @Test
    void processProviderRequest(TransferProcessManager processManager, DataFlowManager dataFlowManager) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        DataFlowController controllerMock = EasyMock.createMock(DataFlowController.class);

        EasyMock.expect(controllerMock.canHandle(EasyMock.isA(DataRequest.class))).andReturn(true);
        EasyMock.expect(controllerMock.initiateFlow(EasyMock.isA(DataRequest.class))).andAnswer(() -> {
            latch.countDown();
            return DataFlowInitiateResult.success("");
        });

        EasyMock.replay(controllerMock);

        dataFlowManager.register(controllerMock);


        var artifactId = "test123";
        var connectorId = "https://test";

        var asset = Asset.Builder.newInstance().id(artifactId).build();
        var request = DataRequest.Builder.newInstance().protocol("ids-rest").assetId(asset.getId())
                .connectorId(connectorId).connectorAddress(connectorId).destinationType("S3").id(UUID.randomUUID().toString()).build();

        processManager.initiateProviderRequest(request);

        latch.await(1, TimeUnit.MINUTES);

        EasyMock.verify(controllerMock);
    }

    @BeforeEach
    void before(EdcExtension extension) {
        extension.registerSystemExtension(VaultExtension.class, new NullVaultExtension());
        extension.registerSystemExtension(ServiceExtension.class, new ServiceExtension() {
            @Override
            public Set<String> provides() {
                return Set.of(IdentityService.FEATURE);
            }

            @Override
            public void initialize(ServiceExtensionContext context) {
                context.registerService(IdentityService.class, new IdentityService() {
                    @Override
                    public Result<TokenRepresentation> obtainClientCredentials(String scope) {
                        return Result.success(TokenRepresentation.Builder.newInstance().token("test").build());
                    }

                    @Override
                    public Result<ClaimToken> verifyJwtToken(String token, String audience) {
                        return Result.success(ClaimToken.Builder.newInstance().build());
                    }
                });
            }
        });
    }
}
