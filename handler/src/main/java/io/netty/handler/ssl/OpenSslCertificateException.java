/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import org.apache.tomcat.jni.CertificateVerifier;

import java.security.cert.CertificateException;

/**
 * A special {@link CertificateException} which allows to specify which error code is included in the
 * SSL Record. This only work when {@link SslProvider#OPENSSL} is used.
 */
public final class OpenSslCertificateException extends CertificateException {
    private static final long serialVersionUID = 5542675253797129798L;

    private final int errorCode;

    /**
     * Construct a new exception with the
     * <a href="https://www.openssl.org/docs/manmaster/apps/verify.html">error code</a>.
     */
    public OpenSslCertificateException(int errorCode) {
        this((String) null, errorCode);
    }

    /**
     * Construct a new exception with the msg and
     * <a href="https://www.openssl.org/docs/manmaster/apps/verify.html">error code</a> .
     */
    public OpenSslCertificateException(String msg, int errorCode) {
        super(msg);
        this.errorCode = checkErrorCode(errorCode);
    }

    /**
     * Construct a new exception with the msg, cause and
     * <a href="https://www.openssl.org/docs/manmaster/apps/verify.html">error code</a> .
     */
    public OpenSslCertificateException(String message, Throwable cause, int errorCode) {
        super(message, cause);
        this.errorCode = checkErrorCode(errorCode);
    }

    /**
     * Construct a new exception with the cause and
     * <a href="https://www.openssl.org/docs/manmaster/apps/verify.html">error code</a> .
     */
    public OpenSslCertificateException(Throwable cause, int errorCode) {
        this(null, cause, errorCode);
    }

    /**
     * Return the <a href="https://www.openssl.org/docs/manmaster/apps/verify.html">error code</a> to use.
     */
    public int errorCode() {
        return errorCode;
    }

    private static int checkErrorCode(int errorCode) {
        if (errorCode < CertificateVerifier.X509_V_OK || errorCode > CertificateVerifier.X509_V_ERR_DANE_NO_MATCH) {
            throw new IllegalArgumentException("errorCode must be " + CertificateVerifier.X509_V_OK + " => "
                    + CertificateVerifier.X509_V_ERR_DANE_NO_MATCH +
                    ". See https://www.openssl.org/docs/manmaster/apps/verify.html .");
        }
        return errorCode;
    }
}
