package org.eclipse.dataspaceconnector.extensions.transfer;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.eclipse.dataspaceconnector.provision.aws.AwsTemporarySecretToken;
import org.eclipse.dataspaceconnector.schema.s3.S3BucketSchema;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataAddress;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.time.temporal.ChronoUnit;

class S3BucketReader implements DataReader {
    private final Monitor monitor;
    private final TypeManager typeManager;
    private final RetryPolicy<Object> retryPolicy;
    private final Vault vault;

    public S3BucketReader(Monitor monitor, TypeManager typeManager, Vault vault) {
        this.monitor = monitor;
        this.typeManager = typeManager;
        this.vault = vault;
        this.retryPolicy = new RetryPolicy<>()
                .withBackoff(500, 5000, ChronoUnit.MILLIS)
                .withMaxRetries(3);
    }

    @Override
    public byte[] read(DataAddress source) {
        var region = source.getProperty(S3BucketSchema.REGION);
        try (S3Client s3 = S3Client.builder()
                .credentialsProvider(credentialsProvider())
                .region(Region.of(region))
                .build()) {

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(source.getProperty(S3BucketSchema.BUCKET_NAME))
                    .key(source.getKeyName())
                    .build();

            monitor.debug("Data request: begin transfer...");
            var response = Failsafe.with(retryPolicy).get(() -> s3.getObject(request));
            monitor.debug("Data request done.");
            return response.readAllBytes();


        } catch (Exception ex) {
            monitor.severe("Data request: transfer failed!", ex);
            return new byte[0];
        }
    }

    @NotNull
    private AwsCredentialsProvider credentialsProvider() {
        var secret = vault.resolveSecret("aws-credentials");
        var awsSecretToken = typeManager.readValue(secret, AwsTemporarySecretToken.class);
        var credentials = AwsSessionCredentials.create(
                awsSecretToken.getAccessKeyId(),
                awsSecretToken.getSecretAccessKey(),
                awsSecretToken.getSessionToken()
        );

        return StaticCredentialsProvider.create(credentials);
    }


}
