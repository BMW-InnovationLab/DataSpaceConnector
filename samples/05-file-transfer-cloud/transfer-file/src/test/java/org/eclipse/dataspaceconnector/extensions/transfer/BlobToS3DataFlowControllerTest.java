package org.eclipse.dataspaceconnector.extensions.transfer;

import org.eclipse.dataspaceconnector.aws.testfixtures.AbstractS3Test;
import org.eclipse.dataspaceconnector.provision.aws.AwsTemporarySecretToken;
import org.eclipse.dataspaceconnector.schema.s3.S3BucketSchema;
import org.eclipse.dataspaceconnector.spi.asset.DataAddressResolver;
import org.eclipse.dataspaceconnector.spi.monitor.Monitor;
import org.eclipse.dataspaceconnector.spi.security.Vault;
import org.eclipse.dataspaceconnector.spi.transfer.flow.DataFlowInitiateResponse;
import org.eclipse.dataspaceconnector.spi.transfer.response.ResponseStatus;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataAddress;
import org.eclipse.dataspaceconnector.spi.types.domain.transfer.DataRequest;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.regions.Region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.niceMock;
import static org.easymock.EasyMock.replay;

class BlobToS3DataFlowControllerTest extends AbstractS3Test {

    private final TypeManager typeManager = new TypeManager();

    @Test
    void write_and_read_from_a_bucket() {
        AwsSessionCredentials credentials = (AwsSessionCredentials) getCredentials();
        AwsTemporarySecretToken awsTemporarySecretToken = new AwsTemporarySecretToken(credentials.accessKeyId(), credentials.secretAccessKey(), credentials.sessionToken(), 0);
        String jsonToken = typeManager.writeValueAsString(awsTemporarySecretToken);

        DataAddressResolver dataAddressResolver = niceMock(DataAddressResolver.class);
        DataAddress source = DataAddress.Builder.newInstance()
                .type("AmazonS3")
                .keyName("source")
                .property(S3BucketSchema.REGION, Region.EU_WEST_1.id())
                .property(S3BucketSchema.BUCKET_NAME, "edc-ndr")
                .build();
        DataAddress destination = DataAddress.Builder.newInstance()
                .type("AmazonS3")
                .property(S3BucketSchema.REGION, Region.EU_WEST_1.id())
                .property(S3BucketSchema.BUCKET_NAME, "edc-ndr")
                .keyName("dest")
                .build();
        expect(dataAddressResolver.resolveForAsset("assetId")).andReturn(source);
        Vault vault = niceMock(Vault.class);
        expect(vault.resolveSecret(anyString())).andReturn(jsonToken).anyTimes();
        replay(dataAddressResolver, vault);
        BlobToS3DataFlowController controller = new BlobToS3DataFlowController(vault, niceMock(Monitor.class), typeManager, dataAddressResolver);
        DataRequest dataRequest = DataRequest.Builder.newInstance()
                .dataDestination(destination)
                .assetId("assetId")
                .build();

        DataFlowInitiateResponse response = controller.initiateFlow(dataRequest);

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.OK);
    }
}