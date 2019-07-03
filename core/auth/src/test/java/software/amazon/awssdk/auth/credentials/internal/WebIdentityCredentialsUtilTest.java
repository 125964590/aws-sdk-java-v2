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
package software.amazon.awssdk.auth.credentials.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenCredentialsProviderFactory;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sts.internal.StsWebIdentityCredentialsProviderFactory;

public class WebIdentityCredentialsUtilTest {

    @Test
    public void resolveWebIdentityToken_ReturnsWebIdentityTokenString() {
        Path filePath = Paths.get("/Users/finks/GitHubv2/v2-master/core/auth/src/test/resources/token.jwt");
        String webIdentityToken = WebIdentityCredentialsUtil.resolveWebIdentityToken(filePath);

        assertThat(webIdentityToken).isNotBlank();
    }

    @Test(expected = SdkClientException.class)
    public void resolveWebIdentityToken_RelativePath_ThrowsRuntimeException() {
        Path filePath = Paths.get("token.jwt");
        WebIdentityCredentialsUtil.resolveWebIdentityToken(filePath);
    }
}
