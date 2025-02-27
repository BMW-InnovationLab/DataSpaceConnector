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

package org.eclipse.dataspaceconnector.transfer.core;

import org.eclipse.dataspaceconnector.spi.EdcException;
import org.eclipse.dataspaceconnector.spi.EdcSetting;
import org.eclipse.dataspaceconnector.spi.command.BoundedCommandQueue;
import org.eclipse.dataspaceconnector.spi.command.CommandHandlerRegistry;
import org.eclipse.dataspaceconnector.spi.command.CommandRunner;
import org.eclipse.dataspaceconnector.spi.iam.TokenGenerationService;
import org.eclipse.dataspaceconnector.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.dataspaceconnector.spi.proxy.DataProxyManager;
import org.eclipse.dataspaceconnector.spi.proxy.ProxyEntryHandlerRegistry;
import org.eclipse.dataspaceconnector.spi.retry.ExponentialWaitStrategy;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.system.CoreExtension;
import org.eclipse.dataspaceconnector.spi.system.Inject;
import org.eclipse.dataspaceconnector.spi.system.Provides;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;
import org.eclipse.dataspaceconnector.spi.transfer.TransferProcessManager;
import org.eclipse.dataspaceconnector.spi.transfer.edr.EndpointDataReferenceReceiverRegistry;
import org.eclipse.dataspaceconnector.spi.transfer.edr.EndpointDataReferenceTransformer;
import org.eclipse.dataspaceconnector.spi.transfer.flow.DataFlowManager;
import org.eclipse.dataspaceconnector.spi.transfer.inline.DataOperatorRegistry;
import org.eclipse.dataspaceconnector.spi.transfer.observe.TransferProcessObservable;
import org.eclipse.dataspaceconnector.spi.transfer.provision.ProvisionManager;
import org.eclipse.dataspaceconnector.spi.transfer.provision.ResourceManifestGenerator;
import org.eclipse.dataspaceconnector.spi.transfer.retry.TransferWaitStrategy;
import org.eclipse.dataspaceconnector.spi.transfer.store.TransferProcessStore;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataRequest;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.StatusCheckerRegistry;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.command.TransferProcessCommand;
import org.eclipse.dataspaceconnector.transfer.core.edr.DefaultEndpointDataReferenceTransformer;
import org.eclipse.dataspaceconnector.transfer.core.edr.EndpointDataReferenceReceiverRegistryImpl;
import org.eclipse.dataspaceconnector.transfer.core.edr.ProxyEndpointDataReferenceTransformer;
import org.eclipse.dataspaceconnector.transfer.core.flow.DataFlowManagerImpl;
import org.eclipse.dataspaceconnector.transfer.core.inline.DataOperatorRegistryImpl;
import org.eclipse.dataspaceconnector.transfer.core.observe.TransferProcessObservableImpl;
import org.eclipse.dataspaceconnector.transfer.core.provision.ProvisionManagerImpl;
import org.eclipse.dataspaceconnector.transfer.core.provision.ResourceManifestGeneratorImpl;
import org.eclipse.dataspaceconnector.transfer.core.synchronous.DataProxyManagerImpl;
import org.eclipse.dataspaceconnector.transfer.core.transfer.ProxyEntryHandlerRegistryImpl;
import org.eclipse.dataspaceconnector.transfer.core.transfer.StatusCheckerRegistryImpl;
import org.eclipse.dataspaceconnector.transfer.core.transfer.TransferProcessManagerImpl;

/**
 * Provides core data transfer services to the system.
 */
@CoreExtension
@Provides({StatusCheckerRegistry.class, ResourceManifestGenerator.class, TransferProcessManager.class, TransferProcessObservable.class,
        DataProxyManager.class, ProxyEntryHandlerRegistry.class, DataOperatorRegistry.class, DataFlowManager.class,
        EndpointDataReferenceReceiverRegistry.class, EndpointDataReferenceTransformer.class})
public class CoreTransferExtension implements ServiceExtension {
    private static final long DEFAULT_ITERATION_WAIT = 5000; // millis

    @EdcSetting
    public static final String DATAPLANE_PUBLIC_ENDPOINT = "edc.dataplane.public-endpoint";

    @EdcSetting
    public static final String DATAPLANE_CONSUMER_PROXY_ENABLED = "edc.dataplane.consumer-proxy.enabled";

    @Inject
    private TransferProcessStore transferProcessStore;
    @Inject
    private CommandHandlerRegistry registry;
    @Inject
    private RemoteMessageDispatcherRegistry dispatcherRegistry;
    @Inject(required = false)
    private TokenGenerationService tokenGenerationService;

    private TransferProcessManagerImpl processManager;

    @Override
    public String name() {
        return "Core Transfer";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        var telemetry = context.getTelemetry();

        var typeManager = context.getTypeManager();

        registerTypes(typeManager);

        var dataFlowManager = new DataFlowManagerImpl();
        context.registerService(DataFlowManager.class, dataFlowManager);

        var manifestGenerator = new ResourceManifestGeneratorImpl();
        context.registerService(ResourceManifestGenerator.class, manifestGenerator);

        var statusCheckerRegistry = new StatusCheckerRegistryImpl();
        context.registerService(StatusCheckerRegistry.class, statusCheckerRegistry);

        var dataOperatorRegistry = new DataOperatorRegistryImpl();
        context.registerService(DataOperatorRegistry.class, dataOperatorRegistry);

        var vault = context.getService(Vault.class);

        var provisionManager = new ProvisionManagerImpl(monitor);
        context.registerService(ProvisionManager.class, provisionManager);

        var waitStrategy = context.hasService(TransferWaitStrategy.class) ? context.getService(TransferWaitStrategy.class) : new ExponentialWaitStrategy(DEFAULT_ITERATION_WAIT);

        var endpointDataReferenceReceiverRegistry = new EndpointDataReferenceReceiverRegistryImpl();
        context.registerService(EndpointDataReferenceReceiverRegistry.class, endpointDataReferenceReceiverRegistry);

        registerEndpointDataReferenceTransformer(context);

        var dataProxyManager = new DataProxyManagerImpl();
        context.registerService(DataProxyManager.class, dataProxyManager);

        var proxyEntryHandlerRegistry = new ProxyEntryHandlerRegistryImpl();
        context.registerService(ProxyEntryHandlerRegistry.class, proxyEntryHandlerRegistry);

        var commandQueue = new BoundedCommandQueue<TransferProcessCommand>(10);
        var observable = new TransferProcessObservableImpl();
        context.registerService(TransferProcessObservable.class, observable);

        processManager = TransferProcessManagerImpl.Builder.newInstance()
                .waitStrategy(waitStrategy)
                .manifestGenerator(manifestGenerator)
                .dataFlowManager(dataFlowManager)
                .provisionManager(provisionManager)
                .dispatcherRegistry(dispatcherRegistry)
                .statusCheckerRegistry(statusCheckerRegistry)
                .monitor(monitor)
                .telemetry(telemetry)
                .vault(vault)
                .typeManager(typeManager)
                .commandQueue(commandQueue)
                .commandRunner(new CommandRunner<>(registry, monitor))
                .dataProxyManager(dataProxyManager)
                .proxyEntryHandlerRegistry(proxyEntryHandlerRegistry)
                .observable(observable)
                .build();


        context.registerService(TransferProcessManager.class, processManager);
    }

    @Override
    public void start() {
        processManager.start(transferProcessStore);
    }

    @Override
    public void shutdown() {
        if (processManager != null) {
            processManager.stop();
        }
    }

    private void registerTypes(TypeManager typeManager) {
        typeManager.registerTypes(DataRequest.class);
    }

    private void registerEndpointDataReferenceTransformer(ServiceExtensionContext context) {
        EndpointDataReferenceTransformer transformer;
        boolean consumerProxyEnabled = Boolean.parseBoolean(context.getSetting(DATAPLANE_CONSUMER_PROXY_ENABLED, Boolean.FALSE.toString()));
        if (consumerProxyEnabled) {
            if (tokenGenerationService != null) {
                var dataPlanePublicEndpoint = context.getSetting(DATAPLANE_PUBLIC_ENDPOINT, "/api/public/transfer");
                transformer = new ProxyEndpointDataReferenceTransformer(tokenGenerationService, dataPlanePublicEndpoint, context.getTypeManager());
            } else {
                throw new EdcException("Missing mandatory TokenGenerationService");
            }
        } else {
            transformer = new DefaultEndpointDataReferenceTransformer();
        }
        context.registerService(EndpointDataReferenceTransformer.class, transformer);
    }
}
