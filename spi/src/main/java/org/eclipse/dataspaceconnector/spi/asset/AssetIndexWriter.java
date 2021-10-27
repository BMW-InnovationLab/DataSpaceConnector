package org.eclipse.dataspaceconnector.spi.asset;

import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;

public interface AssetIndexWriter {
    void add(Asset asset);
}
