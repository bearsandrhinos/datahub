package com.linkedin.gms.factory.s3;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import com.linkedin.metadata.utils.aws.S3Util;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.SkipException;
import org.testng.annotations.Test;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

@Configuration
class TestStsClientConfiguration {

  @Bean
  @Primary
  public StsClient mockStsClient() {
    StsClient mockClient = org.mockito.Mockito.mock(StsClient.class);

    // Mock STS assumeRole to return fake credentials with future expiry
    Credentials mockCredentials =
        Credentials.builder()
            .accessKeyId("test-access-key")
            .secretAccessKey("test-secret-key")
            .sessionToken("test-session-token")
            .expiration(Instant.now().plusSeconds(3600)) // 1 hour from now
            .build();

    AssumeRoleResponse mockResponse =
        AssumeRoleResponse.builder().credentials(mockCredentials).build();

    when(mockClient.assumeRole(any(AssumeRoleRequest.class))).thenReturn(mockResponse);

    return mockClient;
  }
}

@SpringBootTest(classes = {S3UtilFactory.class, TestStsClientConfiguration.class})
@TestPropertySource(properties = {"datahub.s3.roleArn="})
public class S3UtilFactoryTest extends AbstractTestNGSpringContextTests {

  // Set AWS region before any Spring context initialization
  static {
    System.setProperty("aws.region", "us-east-1");
  }

  @Autowired
  @Qualifier("s3Util")
  private S3Util s3Util;

  @Test
  public void testS3UtilCreationWithoutRoleArn() {
    // When/Then
    assertNotNull(s3Util, "S3Util bean should be created successfully");
  }

  @Test
  public void testS3UtilBeanName() {
    // Verify the bean is registered with the correct name
    assertNotNull(s3Util, "S3Util bean should be available with name 's3Util'");
  }
}

/** Test class for S3UtilFactory with STS role ARN configuration */
@SpringBootTest(classes = {S3UtilFactory.class, TestStsClientConfiguration.class})
@TestPropertySource(properties = {"datahub.s3.roleArn=arn:aws:iam::123456789012:role/test-role"})
class S3UtilFactoryWithRoleArnTest extends AbstractTestNGSpringContextTests {

  // Set AWS region before any Spring context initialization
  static {
    System.setProperty("aws.region", "us-east-1");
  }

  @Autowired
  @Qualifier("s3Util")
  private S3Util s3Util;

  @Test
  public void testS3UtilCreationWithRoleArn() {
    // When/Then
    assertNotNull(s3Util, "S3Util bean should be created successfully with STS role ARN");
  }
}

/**
 * Verifies S3Util is not created when no AWS config is present (quickstart/local dev scenario: no
 * datahub.s3.roleArn, no AWS_ENDPOINT_URL, no AWS_REGION, no aws.region).
 *
 * <p>Skips when AWS_REGION or AWS_ENDPOINT_URL is set in the environment, since the factory will
 * then create an S3 client and the assertion would not apply.
 */
@SpringBootTest(classes = {S3UtilFactory.class})
@TestPropertySource(properties = {"datahub.s3.roleArn="})
class S3UtilFactoryNoAwsConfigTest extends AbstractTestNGSpringContextTests {

  static {
    System.clearProperty("aws.region");
  }

  @Autowired(required = false)
  @Qualifier("s3Util")
  private S3Util s3Util;

  @Test
  public void testS3UtilIsNullWhenNoAwsConfig() {
    String awsRegion = System.getenv("AWS_REGION");
    String awsEndpoint = System.getenv("AWS_ENDPOINT_URL");
    if ((awsRegion != null && !awsRegion.isEmpty())
        || (awsEndpoint != null && !awsEndpoint.isEmpty())) {
      throw new SkipException(
          "Skipping: AWS_REGION or AWS_ENDPOINT_URL is set, so S3Util is created");
    }
    assertNull(s3Util, "S3Util bean should be null when no AWS region or endpoint is configured");
  }
}
