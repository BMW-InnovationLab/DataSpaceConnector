/*
 *  Copyright (c) 2021 Siemens AG
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Siemens AG - initial implementation
 *
 */

package org.eclipse.dataspaceconnector.transfer.demo.controller;

import org.eclipse.dataspaceconnector.provision.aws.AwsTemporarySecretToken;
import org.eclipse.dataspaceconnector.schema.s3.S3BucketSchema;
import org.eclipse.dataspaceconnector.spi.EdcException;
import org.eclipse.dataspaceconnector.spi.asset.DataAddressResolver;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.transfer.flow.DataFlowController;
import org.eclipse.dataspaceconnector.spi.transfer.flow.DataFlowInitiateResult;
import org.eclipse.dataspaceconnector.spi.transfer.response.ResponseStatus;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataRequest;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3toS3TransferFlowController implements DataFlowController {
    private final Vault vault;
    private final Monitor monitor;
    private final DataAddressResolver dataAddressResolver;
    private final TypeManager typeManager;

    public S3toS3TransferFlowController(Vault vault, Monitor monitor, DataAddressResolver dataAddressResolver, TypeManager typeManager) {
        this.vault = vault;
        this.monitor = monitor;
        this.dataAddressResolver = dataAddressResolver;
        this.typeManager = typeManager;
    }

    @Override
    public boolean canHandle(DataRequest dataRequest) {
        return "dataspaceconnector:s3".equals(dataRequest.getDataDestination().getType());
    }

    @Override
    public @NotNull DataFlowInitiateResult initiateFlow(DataRequest dataRequest) {
        var source = dataAddressResolver.resolveForAsset(dataRequest.getAssetId());

        final String sourceKey = source.getKeyName();
        final String sourceBucketName = source.getProperty(S3BucketSchema.BUCKET_NAME);

        var destinationKey = dataRequest.getDataDestination().getKeyName();
        var awsSecret = vault.resolveSecret(destinationKey);
        var destinationBucketName = dataRequest.getDataDestination().getProperty(S3BucketSchema.BUCKET_NAME);

        var region = dataRequest.getDataDestination().getProperty(S3BucketSchema.REGION);
        var dt = typeManager.readValue(awsSecret, AwsTemporarySecretToken.class);

        return copyToBucket(sourceBucketName, sourceKey, destinationBucketName, destinationKey, region, dt);
    }

    @NotNull
    private DataFlowInitiateResult copyToBucket(
            final String sourceBucketName,
            final String sourceKey,
            final String destinationBucketName,
            final String destinationKey,
            final String region,
            final AwsTemporarySecretToken dt
    ) {
        try (S3Client s3 = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsSessionCredentials.create(dt.getAccessKeyId(), dt.getSecretAccessKey(), dt.getSessionToken())))
                .region(Region.of(region))
                .build()) {

            String etag = null;

            try {
                monitor.debug("Data request: begin transfer...");

                final CopyObjectRequest copyObjectRequest = CopyObjectRequest.builder()
                        .copySource(sourceBucketName + "/" + sourceKey)
                        .destinationBucket(destinationBucketName)
                        .destinationKey(destinationKey)
                        .build();

                var response = s3.copyObject(copyObjectRequest);

                monitor.debug("Data request done.");
                etag = response.copyObjectResult().eTag();
            } catch (S3Exception tmpEx) {
                monitor.info("Data request: transfer not successful");
            }

            return DataFlowInitiateResult.success(etag);
        } catch (S3Exception | EdcException ex) {
            monitor.severe("Data request: transfer failed!");
            return DataFlowInitiateResult.failure(ResponseStatus.FATAL_ERROR, ex.getLocalizedMessage());
        }
    }

}

