/*
 *  Copyright (c) 2021 Microsoft Corporation
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
package org.eclipse.dataspaceconnector.transfer.functions.core;

import org.eclipse.dataspaceconnector.spi.EdcSetting;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;
import org.eclipse.dataspaceconnector.spi.transfer.flow.DataFlowManager;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.StatusCheckerRegistry;
import org.eclipse.dataspaceconnector.transfer.functions.core.flow.http.HttpFunctionConfiguration;
import org.eclipse.dataspaceconnector.transfer.functions.core.flow.http.HttpFunctionDataFlowController;
import org.eclipse.dataspaceconnector.transfer.functions.core.flow.http.HttpStatusChecker;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpClient;
import java.util.HashSet;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;

/**
 * Bootstraps the transfer functions extension.
 */
public class TransferFunctionsCoreServiceExtension implements ServiceExtension {

    @EdcSetting
    static final String ENABLED_PROTOCOLS_KEY = "edc.transfer.functions.enabled.protocols";

    @EdcSetting
    static final String TRANSFER_URL_KEY = "edc.transfer.functions.transfer.endpoint";

    @EdcSetting
    static final String CHECK_URL_KEY = "edc.transfer.functions.check.endpoint";

    private static final String DEFAULT_LOCAL_TRANSFER_URL = "http://localhost:9090/transfer";
    private static final String DEFAULT_LOCAL_CHECK_URL = "http://localhost:9090/checker";

    private Monitor monitor;

    private Set<String> protocols;

    @Override
    public Set<String> provides() {
        return Set.of("dataspaceconnector:transfer-function");
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor();

        protocols = getSupportedProtocols(context);

        initializeHttpFunctions(context);

        monitor.info("Initialized Transfer Functions Core extension");
    }

    @Override
    public void start() {
        monitor.info("Started Transfer Functions Core extension");
    }

    @Override
    public void shutdown() {
        monitor.info("Shutdown Transfer Functions Core extension");
    }

    private void initializeHttpFunctions(ServiceExtensionContext context) {
        var httpClient = context.getService(HttpClient.class);

        var typeManager = context.getTypeManager();
        var transferEndpoint = context.getSetting(TRANSFER_URL_KEY, DEFAULT_LOCAL_TRANSFER_URL);
        var checkEndpoint = context.getSetting(CHECK_URL_KEY, DEFAULT_LOCAL_CHECK_URL);
        var configuration = HttpFunctionConfiguration.Builder.newInstance()
                .transferEndpoint(transferEndpoint)
                .checkEndpoint(checkEndpoint)
                .clientSupplier(() -> httpClient) // TODO: pass the client directly
                .protocols(protocols)
                .typeManager(typeManager)
                .monitor(monitor)
                .build();

        var flowController = new HttpFunctionDataFlowController(configuration);
        var flowManager = context.getService(DataFlowManager.class);
        flowManager.register(flowController);

        var statusChecker = new HttpStatusChecker(configuration);
        var statusCheckerRegistry = context.getService(StatusCheckerRegistry.class);
        protocols.forEach(protocol -> statusCheckerRegistry.register(protocol, statusChecker));

        monitor.info("HTTP transfer functions are enabled");
    }

    /**
     * Parses the protocols supported by the configured transfer function.
     */
    @NotNull
    private Set<String> getSupportedProtocols(ServiceExtensionContext context) {
        var protocolsString = context.getSetting(ENABLED_PROTOCOLS_KEY, null);
        if (protocolsString == null) {
            monitor.info(format("No protocol is enabled for the Transfer Functions extension. One or more protocols can be enabled using the %s key. " +
                    "The extension will be disabled.", ENABLED_PROTOCOLS_KEY));
            return emptySet();
        } else {
            return new HashSet<>(asList(protocolsString.split(",")));
        }
    }

}
