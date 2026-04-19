package com.einvoice.zatca.signing;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import xades4j.algorithms.EnvelopedSignatureTransform;
import xades4j.algorithms.ExclusiveCanonicalXMLWithoutComments;
import xades4j.algorithms.XPath2FilterTransform;
import xades4j.production.DataObjectReference;
import xades4j.production.SignedDataObjects;
import xades4j.production.XadesBesSigningProfile;
import xades4j.production.XadesSigner;
import xades4j.properties.DataObjectDesc;
import xades4j.providers.impl.DirectKeyingDataProvider;

/**
 * XML digital signature service using XAdES-BES for ZATCA compliance.
 *
 * <p>Pre-builds the full UBLExtensions signature structure before signing so
 * the signature element is placed in its final location. XPath2 transforms
 * exclude UBLExtensions from the digest so post-signing changes to QR/PIH
 * within UBLExtensions do not invalidate the signature.</p>
 */
@Service
public class ZatcaSigningService {

    private static final String EXT_NS =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String SIG_NS =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonSignatureComponents-2";
    private static final String SAC_NS =
            "urn:oasis:names:specification:ubl:schema:xsd:SignatureAggregateComponents-2";
    private static final String DS_NS =
            "http://www.w3.org/2000/09/xmldsig#";

    /**
     * Result of a signing operation containing the signed XML and raw
     * Base64-encoded signature value (TLV tag 7 for QR).
     *
     * @param signedXml the complete signed XML document
     * @param signatureValueBase64 the Base64-encoded ds:SignatureValue
     */
    public record SigningResult(String signedXml, String signatureValueBase64) {}

    /**
     * Signs the given XML content with an XAdES-BES enveloped signature.
     *
     * @param xmlContent the XML content to sign
     * @param certificate the X.509 certificate
     * @param privateKey the private key for signing
     * @return signing result with signed XML and extracted signature value
     */
    public SigningResult sign(String xmlContent, X509Certificate certificate,
            PrivateKey privateKey) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(
                    xmlContent.getBytes(StandardCharsets.UTF_8)));

            Element invoiceElement = doc.getDocumentElement();

            Element signatureInformation = buildSignatureStructure(doc, invoiceElement);

            DirectKeyingDataProvider keyingProvider = new DirectKeyingDataProvider(
                    certificate, privateKey);

            XadesSigner signer = new XadesBesSigningProfile(keyingProvider)
                    .newSigner();

            DataObjectDesc ref = new DataObjectReference("")
                    .withTransform(new EnvelopedSignatureTransform())
                    .withTransform(new ExclusiveCanonicalXMLWithoutComments())
                    .withTransform(XPath2FilterTransform.XPath2Filter.subtract(
                                    "ancestor-or-self::*[local-name()='UBLExtensions']")
                            .subtract("ancestor-or-self::*[local-name()='AdditionalDocumentReference'"
                                    + " and child::*[local-name()='ID' and text()='QR']]")
                            .subtract("ancestor-or-self::*[local-name()='AdditionalDocumentReference'"
                                    + " and child::*[local-name()='ID' and text()='PIH']]"));
            signer.sign(new SignedDataObjects(ref), signatureInformation);

            String signatureValueBase64 = extractSignatureValue(doc);

            return new SigningResult(serializeDocument(doc), signatureValueBase64);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign XML document", e);
        }
    }

    @SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:VariableDeclarationUsageDistance"})
    private Element buildSignatureStructure(Document doc, Element invoice) {
        Element ublExtensions = doc.createElementNS(EXT_NS, "ext:UBLExtensions");
        ublExtensions.setAttributeNS(
                "http://www.w3.org/2000/xmlns/", "xmlns:ext", EXT_NS);
        ublExtensions.setAttributeNS(
                "http://www.w3.org/2000/xmlns/", "xmlns:sig", SIG_NS);
        ublExtensions.setAttributeNS(
                "http://www.w3.org/2000/xmlns/", "xmlns:sac", SAC_NS);

        Element ublExtension = doc.createElementNS(EXT_NS, "ext:UBLExtension");
        Element extensionContent = doc.createElementNS(EXT_NS, "ext:ExtensionContent");
        Element ublDocumentSignatures = doc.createElementNS(
                SIG_NS, "sig:UBLDocumentSignatures");
        Element signatureInformation = doc.createElementNS(
                SAC_NS, "sac:SignatureInformation");

        ublDocumentSignatures.appendChild(signatureInformation);
        extensionContent.appendChild(ublDocumentSignatures);
        ublExtension.appendChild(extensionContent);
        ublExtensions.appendChild(ublExtension);

        if (invoice.getFirstChild() != null) {
            invoice.insertBefore(ublExtensions, invoice.getFirstChild());
        } else {
            invoice.appendChild(ublExtensions);
        }
        return signatureInformation;
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String extractSignatureValue(Document doc) {
        NodeList sigValues = doc.getElementsByTagNameNS(DS_NS, "SignatureValue");
        if (sigValues.getLength() > 0) {
            return sigValues.item(0).getTextContent().trim();
        }
        return "";
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String serializeDocument(Document doc) throws Exception {
        javax.xml.transform.Transformer transformer =
                TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
