package org.eclipse.dataspaceconnector.demo.assetindex;

import org.eclipse.dataspaceconnector.metadata.memory.InMemoryAssetIndex;
import org.eclipse.dataspaceconnector.metadata.memory.InMemoryAssetIndexWriter;
import org.eclipse.dataspaceconnector.spi.asset.AssetIndex;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.protocol.web.WebService;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtension;
import org.eclipse.dataspaceconnector.spi.system.ServiceExtensionContext;

import java.util.Set;

public class DemoAssetIndexExtension implements ServiceExtension {
    private static final String NAME = "Demo Asset Index Extension";
    private Monitor monitor;

    @Override
    public Set<String> requires() {
        return Set.of(AssetIndex.FEATURE, "edc:webservice");
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor();

        var assetIndex = context.getService(AssetIndex.class);
        var webService = context.getService(WebService.class);
        var assetIndexWriter = new InMemoryAssetIndexWriter((InMemoryAssetIndex) assetIndex);

        webService.registerController(new AssetIndexController(assetIndex, assetIndexWriter));

        monitor.info("Inizialized " + NAME);
    }

    @Override
    public void start() {
        monitor.info("Started " + NAME);
    }

    @Override
    public void shutdown() {
        monitor.info("Shutdown " + NAME);
    }
}
