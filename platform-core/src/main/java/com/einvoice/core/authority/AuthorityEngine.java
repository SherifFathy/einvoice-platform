package com.einvoice.core.authority;

/** Javadoc. */
public interface AuthorityEngine {

    SerializedPayload serialize(DocumentInput input);

    SignedPayload sign(SerializedPayload payload, CertificateMaterial cert);

    AuthorityResponse submit(SignedPayload signed, AuthorityCredentials creds);

    AuthorityResponse cancel(CancelInput input);

    AuthorityResponse checkStatus(StatusInput input);
}
