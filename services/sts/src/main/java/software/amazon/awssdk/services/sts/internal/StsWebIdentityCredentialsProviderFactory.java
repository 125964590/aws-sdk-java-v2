/*
 * Copyright 2010-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.services.sts.internal;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Supplier;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenCredentialsProviderFactory;
import software.amazon.awssdk.core.retry.RetryPolicyContext;
import software.amazon.awssdk.core.retry.conditions.OrRetryCondition;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleWithWebIdentityCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityRequest;
import software.amazon.awssdk.services.sts.model.IdpCommunicationErrorException;
import software.amazon.awssdk.utils.FunctionalUtils;
import software.amazon.awssdk.utils.IoUtils;
import software.amazon.awssdk.utils.SdkAutoCloseable;

@SdkInternalApi
public final class StsWebIdentityCredentialsProviderFactory implements WebIdentityTokenCredentialsProviderFactory {

    public AwsCredentialsProvider create(String roleArn, String roleSessionName, String webIdentityToken) {
        return new StsWebIdentityCredentialsProvider(roleArn, roleSessionName, webIdentityToken);
    }

    /**
     * A wrapper for a {@link StsAssumeRoleWithWebIdentityCredentialsProvider} that is returned by this factory when
     * {@link #create(String, String, String)} is invoked. This wrapper is important because it ensures the parent
     * credentials provider is closed when the assume-role credentials provider is no longer needed.
     */
    private static final class StsWebIdentityCredentialsProvider implements AwsCredentialsProvider, SdkAutoCloseable {
        private final StsClient stsClient;
        private final StsAssumeRoleWithWebIdentityCredentialsProvider credentialsProvider;

        private StsWebIdentityCredentialsProvider(String roleArn, String roleSessionName, String tokenFilePath) {
            String sessionName = roleSessionName != null ? roleSessionName : "aws-sdk-java-" + System.currentTimeMillis();

            OrRetryCondition retryCondition = OrRetryCondition.create(new StsRetryCondition(),
                                                                      RetryCondition.defaultRetryCondition());

            this.stsClient = StsClient.builder()
                                      .credentialsProvider(AnonymousCredentialsProvider.create())
                                      .overrideConfiguration(o -> o.retryPolicy(r -> r.retryCondition(retryCondition)))
                                      .build();

            AssumeRoleWithWebIdentityRequest request = AssumeRoleWithWebIdentityRequest.builder()
                                                                                       .roleArn(roleArn)
                                                                                       .roleSessionName(sessionName)
                                                                                       .build();

            AssumeRoleWithWebIdentityRequestSupplier supplier = new AssumeRoleWithWebIdentityRequestSupplier(request,
                                                                                                             tokenFilePath);

            this.credentialsProvider =
                StsAssumeRoleWithWebIdentityCredentialsProvider.builder()
                                                               .stsClient(stsClient)
                                                               .refreshRequest(supplier)
                                                               .build();
        }

        @Override
        public AwsCredentials resolveCredentials() {
            return this.credentialsProvider.resolveCredentials();
        }

        @Override
        public void close() {
            IoUtils.closeQuietly(credentialsProvider, null);
            IoUtils.closeQuietly(stsClient, null);
        }
    }

    private static final class AssumeRoleWithWebIdentityRequestSupplier implements Supplier {

        private final AssumeRoleWithWebIdentityRequest request;
        private final String webIdentityTokenFilePath;

        AssumeRoleWithWebIdentityRequestSupplier(AssumeRoleWithWebIdentityRequest request,
                                                 String webIdentityTokenFilePath) {
            this.request = request;
            this.webIdentityTokenFilePath = webIdentityTokenFilePath;
        }

        @Override
        public Object get() {
            return request.toBuilder().webIdentityToken(getToken(webIdentityTokenFilePath)).build();
        }

        private String getToken(String filePath) {
            InputStream webIdentityTokenStream = FunctionalUtils.invokeSafely(() -> Files.newInputStream(Paths.get(filePath)));
            String webIdentityToken = FunctionalUtils.invokeSafely(() -> IoUtils.toUtf8String(webIdentityTokenStream));

            try {
                return webIdentityToken;
            } finally {
                FunctionalUtils.invokeSafely(() -> IoUtils.closeQuietly(webIdentityTokenStream, null));
            }
        }
    }


    private static final class StsRetryCondition implements RetryCondition {

        @Override
        public boolean shouldRetry(RetryPolicyContext context) {
            return context.exception() instanceof IdpCommunicationErrorException;
        }
    }
}
