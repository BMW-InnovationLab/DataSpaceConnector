package org.eclipse.dataspaceconnector.metadata.memory;

import org.eclipse.dataspaceconnector.spi.asset.AssetIndexWriter;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;

public class InMemoryAssetIndexWriter implements AssetIndexWriter {

    private final InMemoryAssetIndex assetIndex;

    public InMemoryAssetIndexWriter(InMemoryAssetIndex assetIndex) {
        this.assetIndex = assetIndex;
    }

    @Override
    public void add(Asset asset) {
        assetIndex.add(asset, null);
    }
}
