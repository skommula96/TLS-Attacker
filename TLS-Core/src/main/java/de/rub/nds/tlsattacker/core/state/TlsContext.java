/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.state;

import de.rub.nds.modifiablevariable.util.ArrayConverter;
import de.rub.nds.modifiablevariable.util.BadRandom;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.AuthzDataFormat;
import de.rub.nds.tlsattacker.core.constants.CertificateStatusRequestType;
import de.rub.nds.tlsattacker.core.constants.CertificateType;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.ClientCertificateType;
import de.rub.nds.tlsattacker.core.constants.CompressionMethod;
import de.rub.nds.tlsattacker.core.constants.ECPointFormat;
import de.rub.nds.tlsattacker.core.constants.ExtensionType;
import de.rub.nds.tlsattacker.core.constants.HeartbeatMode;
import de.rub.nds.tlsattacker.core.constants.MaxFragmentLength;
import de.rub.nds.tlsattacker.core.constants.NamedCurve;
import de.rub.nds.tlsattacker.core.constants.PRFAlgorithm;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.constants.PskKeyExchangeMode;
import de.rub.nds.tlsattacker.core.constants.SignatureAndHashAlgorithm;
import de.rub.nds.tlsattacker.core.constants.SrtpProtectionProfiles;
import de.rub.nds.tlsattacker.core.constants.Tls13KeySetType;
import de.rub.nds.tlsattacker.core.constants.TokenBindingKeyParameters;
import de.rub.nds.tlsattacker.core.constants.TokenBindingVersion;
import de.rub.nds.tlsattacker.core.constants.UserMappingExtensionHintType;
import de.rub.nds.tlsattacker.core.crypto.MessageDigestCollector;
import de.rub.nds.tlsattacker.core.crypto.ec.CustomECPoint;
import de.rub.nds.tlsattacker.core.exceptions.ConfigurationException;
import de.rub.nds.tlsattacker.core.protocol.message.extension.KS.KSEntry;
import de.rub.nds.tlsattacker.core.protocol.message.extension.SNI.SNIEntry;
import de.rub.nds.tlsattacker.core.protocol.message.extension.cachedinfo.CachedObject;
import de.rub.nds.tlsattacker.core.protocol.message.extension.certificatestatusrequestitemv2.RequestItemV2;
import de.rub.nds.tlsattacker.core.protocol.message.extension.trustedauthority.TrustedAuthority;
import de.rub.nds.tlsattacker.core.record.layer.RecordLayer;
import de.rub.nds.tlsattacker.core.record.layer.RecordLayerFactory;
import de.rub.nds.tlsattacker.core.record.layer.RecordLayerType;
import static de.rub.nds.tlsattacker.core.state.State.LOGGER;
import de.rub.nds.tlsattacker.core.state.http.HttpContext;
import de.rub.nds.tlsattacker.core.workflow.chooser.Chooser;
import de.rub.nds.tlsattacker.core.workflow.chooser.ChooserFactory;
import de.rub.nds.tlsattacker.transport.ConnectionEnd;
import de.rub.nds.tlsattacker.transport.ConnectionEndType;
import de.rub.nds.tlsattacker.transport.TransportHandler;
import de.rub.nds.tlsattacker.transport.TransportHandlerFactory;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import javax.xml.bind.annotation.XmlTransient;
import org.bouncycastle.crypto.tls.Certificate;

public class TlsContext {

    /**
     * TLS-Attacker related configurations.
     */
    private Config config;

    private List<Session> sessionList;

    private HttpContext httpContext;

    /**
     * The end point of the TLS connection that this context represents.
     */
    private ConnectionEnd connectionEnd;

    /**
     * Shared key established during the handshake.
     */
    private byte[] handshakeSecret;

    private byte[] clientHandshakeTrafficSecret;

    private byte[] serverHandshakeTrafficSecret;
    /**
     * shared key established during the handshake
     */
    private byte[] clientApplicationTrafficSecret;
    /**
     * shared key established during the handshake
     */
    private byte[] serverApplicationTrafficSecret;

    /**
     * Early traffic secret used to encrypt early data.
     */
    private byte[] clientEarlyTrafficSecret;

    /**
     * ChiperSuite used for early data.
     */
    private CipherSuite earlyDataCipherSuite;

    /**
     * EarlySecret used to derive EarlyTrafficSecret and more.
     */
    private byte[] earlySecret;

    /**
     * The selected Pre Shared key.
     */
    private byte[] psk;

    /**
     * Identity of the PSK used for earlyData.
     */
    private byte[] earlyDataPSKIdentity;

    /**
     * Identity of the PSK used for earlyData.
     */
    private int selectedIdentityIndex;

    /**
     * The Client's chosen Kex-Modes.
     */
    private List<PskKeyExchangeMode> clientPskKeyExchangeModes;

    /**
     * Did we just encrypt the EOED-Message?
     */
    private boolean encryptedEndOfEarlyData = false;

    /**
     * SequenceNumber of AEADCipher to be restored after encrypting
     * EOED-Message.
     */
    private long storedSequenceNumberDec = 0;

    /**
     * SequenceNumber of AEADCipher to be restored after decrypting
     * EOED-Message.
     */
    private long storedSequenceNumberEnc = 0;

    /**
     * Maximum number of bytes to transmit as early-data.
     */
    private long maxEarlyDataSize;

    /**
     * Master secret established during the handshake.
     */
    private byte[] masterSecret;

    /**
     * Premaster secret established during the handshake.
     */
    private byte[] preMasterSecret;

    /**
     * Client random, including unix time.
     */
    private byte[] clientRandom;

    /**
     * Server random, including unix time.
     */
    private byte[] serverRandom;

    /**
     * Selected cipher suite.
     */
    private CipherSuite selectedCipherSuite = null;

    /**
     * Selected compression algorithm.
     */
    private CompressionMethod selectedCompressionMethod;

    /**
     * Server session ID.
     */
    private byte[] serverSessionId;

    /**
     * Client session ID.
     */
    private byte[] clientSessionId;

    /**
     * Server certificate parsed from the server certificate message.
     */
    private Certificate serverCertificate;

    /**
     * Client certificate parsed from the client certificate message.
     */
    private Certificate clientCertificate;

    private MessageDigestCollector digest;

    private RecordLayer recordLayer;

    private TransportHandler transportHandler;

    private ConnectionEndType talkingConnectionEndType = ConnectionEndType.CLIENT;

    private byte[] dtlsCookie;

    private ProtocolVersion selectedProtocolVersion;

    private ProtocolVersion highestClientProtocolVersion;

    private List<CipherSuite> clientSupportedCiphersuites;

    private List<CompressionMethod> clientSupportedCompressions;

    private List<SignatureAndHashAlgorithm> serverSupportedSignatureAndHashAlgorithms;

    private List<SignatureAndHashAlgorithm> clientSupportedSignatureAndHashAlgorithms;

    private HeartbeatMode heartbeatMode;

    private MaxFragmentLength maxFragmentLength;

    private SignatureAndHashAlgorithm selectedSigHashAlgorithm;

    private boolean cachedInfoExtensionClientState;

    private List<CachedObject> cachedInfoExtensionObjects;

    private List<RequestItemV2> statusRequestV2RequestList;

    /**
     * These are the padding bytes as used in the padding extension.
     */
    private byte[] paddingExtensionBytes;

    /**
     * This is the session ticket of the SessionTicketTLS extension.
     */
    private byte[] sessionTicketTLS;

    /**
     * The renegotiation info of the RenegotiationInfo extension.
     */
    private byte[] renegotiationInfo;
    /**
     * The requestContext from the CertificateRequest messsage in TLS 1.3.
     */
    private byte[] certificateRequestContext;
    /**
     * Timestamp of the SignedCertificateTimestamp extension.
     */
    private byte[] signedCertificateTimestamp;

    /**
     * This is the request type of the CertificateStatusRequest extension
     */
    private CertificateStatusRequestType certificateStatusRequestExtensionRequestType;

    /**
     * This is the responder ID list of the CertificateStatusRequest extension
     */
    private byte[] certificateStatusRequestExtensionResponderIDList;

    /**
     * This is the request extension of the CertificateStatusRequest extension
     */
    private byte[] certificateStatusRequestExtensionRequestExtension;

    /**
     * This is the user identifier of the SRP extension
     */
    private byte[] secureRemotePasswordExtensionIdentifier;

    /**
     * These are the protection profiles of the SRTP extension
     */
    private List<SrtpProtectionProfiles> secureRealTimeTransportProtocolProtectionProfiles;

    /**
     * This is the master key identifier of the SRTP extension
     */
    private byte[] secureRealTimeProtocolMasterKeyIdentifier;

    /**
     * User mapping extension hint type
     */
    private UserMappingExtensionHintType userMappingExtensionHintType;

    /**
     * Client authz extension data format list
     */
    private List<AuthzDataFormat> clientAuthzDataFormatList;

    /**
     * Server authz extension data format list
     */
    private List<AuthzDataFormat> serverAuthzDataFormatList;

    private BigInteger dhGenerator;

    private BigInteger dhModulus;

    private BigInteger serverDhPrivateKey;

    private BigInteger serverDhPublicKey;

    private BigInteger clientDhPrivateKey;

    private BigInteger clientDhPublicKey;

    private NamedCurve selectedCurve;

    private CustomECPoint clientEcPublicKey;

    private CustomECPoint serverEcPublicKey;

    private BigInteger serverEcPrivateKey;

    private BigInteger clientEcPrivateKey;

    private BigInteger rsaModulus;

    private BigInteger serverRSAPublicKey;

    private BigInteger clientRSAPublicKey;

    private BigInteger serverRSAPrivateKey;

    private BigInteger clientRSAPrivateKey;

    private List<NamedCurve> clientNamedCurvesList;

    private List<ECPointFormat> clientPointFormatsList;

    private List<ECPointFormat> serverPointFormatsList;

    private boolean receivedFatalAlert = false;

    private boolean encryptActive = false;

    private List<ClientCertificateType> clientCertificateTypes;

    private byte[] distinguishedNames;

    private ProtocolVersion lastRecordVersion;

    private List<SNIEntry> clientSNIEntryList;

    private List<KSEntry> clientKeyShareEntryList;

    private KSEntry serverKSEntry;

    /**
     * the currently used type of keySet
     */
    private Tls13KeySetType activeKeySetType = Tls13KeySetType.HANDSHAKE_TRAFFIC_SECRETS;

    /**
     * are we expecting an EndOfEarlyData?
     */
    private boolean expectingEndOfEarlyData;

    /**
     * sequence number used for the encryption
     */
    private long writeSequenceNumber = 0;
    /**
     * sequence number used for the decryption
     */
    private long readSequenceNumber = 0;
    /**
     * supported protocol versions
     */
    private List<ProtocolVersion> clientSupportedProtocolVersions;

    private TokenBindingVersion tokenBindingVersion;

    private List<TokenBindingKeyParameters> tokenBindingKeyParameters;

    /**
     * Whether Token Binding negotiation completed successful or not.
     */
    private boolean tokenBindingNegotiatedSuccessfully = false;

    private byte[] AlpnAnnouncedProtocols;

    private List<CertificateType> certificateTypeClientDesiredTypes;

    private List<CertificateType> serverCertificateTypeDesiredTypes;

    private List<CertificateType> clientCertificateTypeDesiredTypes;

    private List<TrustedAuthority> trustedCaIndicationExtensionCas;

    private SignatureAndHashAlgorithm selectedSignatureAndHashAlgorithm;

    private PRFAlgorithm prfAlgorithm;

    private RecordLayerType recordLayerType;

    private ProtocolVersion highestProtocolVersion;

    private Boolean clientAuthentication;

    /**
     * Last application message data received/send by this context. This is
     * especially useful for forwarding application messages via ForwardAction.
     */
    private byte[] lastHandledApplicationMessageData;

    private byte[] lastClientVerifyData;

    private byte[] lastServerVerifyData;

    private Random random;

    @XmlTransient
    private Chooser chooser;

    /**
     * Contains the TLS extensions proposed by the client. private boolean
     * earlyCleanShutdown;
     */
    private final EnumSet<ExtensionType> proposedExtensionSet = EnumSet.noneOf(ExtensionType.class);

    /**
     * Contains the TLS extensions proposed by the server.
     */
    private final EnumSet<ExtensionType> negotiatedExtensionSet = EnumSet.noneOf(ExtensionType.class);

    /**
     * The "secure_renegotiation" flag of the Renegotiation Indication Extension
     * as defined in RFC5746. Indicates whether secure renegotiation is in use
     * for the connection. Note that this flag reflects a connection "state" and
     * differs from isProposedTlsExtensions*(ExtensionType.RENEGOTIATION_INFO).
     * The latter merely says that the extension was send by client or server.
     */
    private boolean secureRenegotiation = false;

    /**
     * Whether to use the extended master secret or not. This flag is set if the
     * EMS extension was send by both peers. Note that this flag reflects a
     * connection "state" and differs from
     * isProposedTlsExtensions*(ExtensionType. EXTENDED_MASTER_SECRET). The
     * latter merely says that the extension was sent by client or server.
     */
    private boolean useExtendedMasterSecret;

    private Boolean earlyCleanShutdown = false;

    public TlsContext() {
        this(Config.createConfig());
        httpContext = new HttpContext();
    }

    /**
     * This constructor assumes that the config holds exactly one connection
     * end. This is usually used when working with the default connection end in
     * single context scenarios.
     *
     * @param config
     *            The Config for which the TlsContext should be created
     */
    public TlsContext(Config config) {
        if (config.getConnectionEnds().size() > 1) {
            throw new ConfigurationException("Attempting to create context from a config containing"
                    + " multiple connection ends. Please specify the connection end to use.");
        }
        init(config, config.getConnectionEnd());
    }

    public TlsContext(Config config, ConnectionEnd conEnd) {
        init(config, conEnd);
    }

    private void init(Config config, ConnectionEnd conEnd) {
        this.config = config;
        digest = new MessageDigestCollector();
        connectionEnd = conEnd;
        recordLayerType = config.getRecordLayerType();
        httpContext = new HttpContext();
        sessionList = new LinkedList<>();
        random = new Random(0);
    }

    public Chooser getChooser() {
        if (chooser == null) {
            chooser = ChooserFactory.getChooser(config.getChooserType(), this, config);
        }
        return chooser;
    }

    public HttpContext getHttpContext() {
        return httpContext;
    }

    public void setHttpContext(HttpContext httpContext) {
        this.httpContext = httpContext;
    }

    public Session getSession(byte[] sessionId) {
        for (Session session : sessionList) {
            if (Arrays.equals(session.getSessionId(), sessionId)) {
                return session;
            }
        }
        return null;
    }

    public boolean hasSession(byte[] sessionId) {
        return getSession(sessionId) != null;
    }

    public void addNewSession(Session session) {
        sessionList.add(session);
    }

    public List<Session> getSessionList() {
        return sessionList;
    }

    public void setSessionList(List<Session> sessionList) {
        this.sessionList = sessionList;
    }

    public byte[] getLastClientVerifyData() {
        return lastClientVerifyData;
    }

    public void setLastClientVerifyData(byte[] lastClientVerifyData) {
        this.lastClientVerifyData = lastClientVerifyData;
    }

    public byte[] getLastServerVerifyData() {
        return lastServerVerifyData;
    }

    public void setLastServerVerifyData(byte[] lastServerVerifyData) {
        this.lastServerVerifyData = lastServerVerifyData;
    }

    public List<CertificateType> getCertificateTypeClientDesiredTypes() {
        return certificateTypeClientDesiredTypes;
    }

    public void setCertificateTypeClientDesiredTypes(List<CertificateType> certificateTypeClientDesiredTypes) {
        this.certificateTypeClientDesiredTypes = certificateTypeClientDesiredTypes;
    }

    public boolean isSecureRenegotiation() {
        return secureRenegotiation;
    }

    public void setSecureRenegotiation(boolean secureRenegotiation) {
        this.secureRenegotiation = secureRenegotiation;
    }

    public List<ProtocolVersion> getClientSupportedProtocolVersions() {
        return clientSupportedProtocolVersions;
    }

    public void setClientSupportedProtocolVersions(List<ProtocolVersion> clientSupportedProtocolVersions) {
        this.clientSupportedProtocolVersions = clientSupportedProtocolVersions;
    }

    public void setClientSupportedProtocolVersions(ProtocolVersion... clientSupportedProtocolVersions) {
        this.clientSupportedProtocolVersions = new ArrayList(Arrays.asList(clientSupportedProtocolVersions));
    }

    public BigInteger getRsaModulus() {
        return rsaModulus;
    }

    public void setRsaModulus(BigInteger rsaModulus) {
        this.rsaModulus = rsaModulus;
    }

    public BigInteger getServerRSAPublicKey() {
        return serverRSAPublicKey;
    }

    public void setServerRSAPublicKey(BigInteger serverRSAPublicKey) {
        this.serverRSAPublicKey = serverRSAPublicKey;
    }

    public BigInteger getClientRSAPublicKey() {
        return clientRSAPublicKey;
    }

    public void setClientRSAPublicKey(BigInteger clientRSAPublicKey) {
        this.clientRSAPublicKey = clientRSAPublicKey;
    }

    public BigInteger getServerEcPrivateKey() {
        return serverEcPrivateKey;
    }

    public void setServerEcPrivateKey(BigInteger serverEcPrivateKey) {
        this.serverEcPrivateKey = serverEcPrivateKey;
    }

    public BigInteger getClientEcPrivateKey() {
        return clientEcPrivateKey;
    }

    public void setClientEcPrivateKey(BigInteger clientEcPrivateKey) {
        this.clientEcPrivateKey = clientEcPrivateKey;
    }

    public NamedCurve getSelectedCurve() {
        return selectedCurve;
    }

    public void setSelectedCurve(NamedCurve selectedCurve) {
        this.selectedCurve = selectedCurve;
    }

    public CustomECPoint getClientEcPublicKey() {
        return clientEcPublicKey;
    }

    public void setClientEcPublicKey(CustomECPoint clientEcPublicKey) {
        this.clientEcPublicKey = clientEcPublicKey;
    }

    public CustomECPoint getServerEcPublicKey() {
        return serverEcPublicKey;
    }

    public void setServerEcPublicKey(CustomECPoint serverEcPublicKey) {
        this.serverEcPublicKey = serverEcPublicKey;
    }

    public BigInteger getDhGenerator() {
        return dhGenerator;
    }

    public void setDhGenerator(BigInteger dhGenerator) {
        this.dhGenerator = dhGenerator;
    }

    public BigInteger getDhModulus() {
        return dhModulus;
    }

    public void setDhModulus(BigInteger dhModulus) {
        this.dhModulus = dhModulus;
    }

    public BigInteger getServerDhPublicKey() {
        return serverDhPublicKey;
    }

    public void setServerDhPublicKey(BigInteger serverDhPublicKey) {
        this.serverDhPublicKey = serverDhPublicKey;
    }

    public BigInteger getClientDhPrivateKey() {
        return clientDhPrivateKey;
    }

    public void setClientDhPrivateKey(BigInteger clientDhPrivateKey) {
        this.clientDhPrivateKey = clientDhPrivateKey;
    }

    public BigInteger getClientDhPublicKey() {
        return clientDhPublicKey;
    }

    public void setClientDhPublicKey(BigInteger clientDhPublicKey) {
        this.clientDhPublicKey = clientDhPublicKey;
    }

    public BigInteger getServerDhPrivateKey() {
        return serverDhPrivateKey;
    }

    public void setServerDhPrivateKey(BigInteger serverDhPrivateKey) {
        this.serverDhPrivateKey = serverDhPrivateKey;
    }

    public SignatureAndHashAlgorithm getSelectedSignatureAndHashAlgorithm() {
        return selectedSignatureAndHashAlgorithm;
    }

    public void setSelectedSignatureAndHashAlgorithm(SignatureAndHashAlgorithm selectedSignatureAndHashAlgorithm) {
        this.selectedSignatureAndHashAlgorithm = selectedSignatureAndHashAlgorithm;
    }

    public List<NamedCurve> getClientNamedCurvesList() {
        return clientNamedCurvesList;
    }

    public void setClientNamedCurvesList(List<NamedCurve> clientNamedCurvesList) {
        this.clientNamedCurvesList = clientNamedCurvesList;
    }

    public void setClientNamedCurvesList(NamedCurve... clientNamedCurvesList) {
        this.clientNamedCurvesList = new ArrayList(Arrays.asList(clientNamedCurvesList));
    }

    public List<ECPointFormat> getServerPointFormatsList() {
        return serverPointFormatsList;
    }

    public void setServerPointFormatsList(List<ECPointFormat> serverPointFormatsList) {
        this.serverPointFormatsList = serverPointFormatsList;
    }

    public void setServerPointFormatsList(ECPointFormat... serverPointFormatsList) {
        this.serverPointFormatsList = new ArrayList(Arrays.asList(serverPointFormatsList));
    }

    public List<SignatureAndHashAlgorithm> getClientSupportedSignatureAndHashAlgorithms() {
        return clientSupportedSignatureAndHashAlgorithms;
    }

    public void setClientSupportedSignatureAndHashAlgorithms(
            List<SignatureAndHashAlgorithm> clientSupportedSignatureAndHashAlgorithms) {
        this.clientSupportedSignatureAndHashAlgorithms = clientSupportedSignatureAndHashAlgorithms;
    }

    public void setClientSupportedSignatureAndHashAlgorithms(
            SignatureAndHashAlgorithm... clientSupportedSignatureAndHashAlgorithms) {
        this.clientSupportedSignatureAndHashAlgorithms = new ArrayList(
                Arrays.asList(clientSupportedSignatureAndHashAlgorithms));
    }

    public List<SNIEntry> getClientSNIEntryList() {
        return clientSNIEntryList;
    }

    public void setClientSNIEntryList(List<SNIEntry> clientSNIEntryList) {
        this.clientSNIEntryList = clientSNIEntryList;
    }

    public void setClientSNIEntryList(SNIEntry... clientSNIEntryList) {
        this.clientSNIEntryList = new ArrayList(Arrays.asList(clientSNIEntryList));
    }

    public ProtocolVersion getLastRecordVersion() {
        return lastRecordVersion;
    }

    public void setLastRecordVersion(ProtocolVersion lastRecordVersion) {
        this.lastRecordVersion = lastRecordVersion;
    }

    public byte[] getDistinguishedNames() {
        return distinguishedNames;
    }

    public void setDistinguishedNames(byte[] distinguishedNames) {
        this.distinguishedNames = distinguishedNames;
    }

    public List<ClientCertificateType> getClientCertificateTypes() {
        return clientCertificateTypes;
    }

    public void setClientCertificateTypes(List<ClientCertificateType> clientCertificateTypes) {
        this.clientCertificateTypes = clientCertificateTypes;
    }

    public void setClientCertificateTypes(ClientCertificateType... clientCertificateTypes) {
        this.clientCertificateTypes = new ArrayList(Arrays.asList(clientCertificateTypes));
    }

    public boolean isReceivedFatalAlert() {
        return receivedFatalAlert;
    }

    public void setReceivedFatalAlert(boolean receivedFatalAlert) {
        this.receivedFatalAlert = receivedFatalAlert;
    }

    public boolean isEncryptActive() {
        return encryptActive;
    }

    public void setEncryptActive(boolean encryptActive) {
        this.encryptActive = encryptActive;
    }

    public List<ECPointFormat> getClientPointFormatsList() {
        return clientPointFormatsList;
    }

    public void setClientPointFormatsList(List<ECPointFormat> clientPointFormatsList) {
        this.clientPointFormatsList = clientPointFormatsList;
    }

    public void setClientPointFormatsList(ECPointFormat... clientPointFormatsList) {
        this.clientPointFormatsList = new ArrayList(Arrays.asList(clientPointFormatsList));
    }

    public SignatureAndHashAlgorithm getSelectedSigHashAlgorithm() {
        return selectedSigHashAlgorithm;
    }

    public void setSelectedSigHashAlgorithm(SignatureAndHashAlgorithm selectedSigHashAlgorithm) {
        this.selectedSigHashAlgorithm = selectedSigHashAlgorithm;
    }

    public MaxFragmentLength getMaxFragmentLength() {
        return maxFragmentLength;
    }

    public void setMaxFragmentLength(MaxFragmentLength maxFragmentLength) {
        this.maxFragmentLength = maxFragmentLength;
    }

    public HeartbeatMode getHeartbeatMode() {
        return heartbeatMode;
    }

    public void setHeartbeatMode(HeartbeatMode heartbeatMode) {
        this.heartbeatMode = heartbeatMode;
    }

    public byte[] getPaddingExtensionBytes() {
        return paddingExtensionBytes;
    }

    public void setPaddingExtensionBytes(byte[] paddingExtensionBytes) {
        this.paddingExtensionBytes = paddingExtensionBytes;
    }

    public List<CompressionMethod> getClientSupportedCompressions() {
        return clientSupportedCompressions;
    }

    public void setClientSupportedCompressions(List<CompressionMethod> clientSupportedCompressions) {
        this.clientSupportedCompressions = clientSupportedCompressions;
    }

    public void setClientSupportedCompressions(CompressionMethod... clientSupportedCompressions) {
        this.clientSupportedCompressions = new ArrayList(Arrays.asList(clientSupportedCompressions));
    }

    public long getWriteSequenceNumber() {
        return writeSequenceNumber;
    }

    public void setWriteSequenceNumber(long writeSequenceNumber) {
        this.writeSequenceNumber = writeSequenceNumber;
    }

    public void increaseWriteSequenceNumber() {
        this.writeSequenceNumber++;
    }

    public long getReadSequenceNumber() {
        return readSequenceNumber;
    }

    public void setReadSequenceNumber(long readSequenceNumber) {
        this.readSequenceNumber = readSequenceNumber;
    }

    public void increaseReadSequenceNumber() {
        this.readSequenceNumber++;
    }

    public List<CipherSuite> getClientSupportedCiphersuites() {
        return clientSupportedCiphersuites;
    }

    public void setClientSupportedCiphersuites(List<CipherSuite> clientSupportedCiphersuites) {
        this.clientSupportedCiphersuites = clientSupportedCiphersuites;
    }

    public void setClientSupportedCiphersuites(CipherSuite... clientSupportedCiphersuites) {
        this.clientSupportedCiphersuites = new ArrayList(Arrays.asList(clientSupportedCiphersuites));
    }

    public List<SignatureAndHashAlgorithm> getServerSupportedSignatureAndHashAlgorithms() {
        return serverSupportedSignatureAndHashAlgorithms;
    }

    public void setServerSupportedSignatureAndHashAlgorithms(
            List<SignatureAndHashAlgorithm> serverSupportedSignatureAndHashAlgorithms) {
        this.serverSupportedSignatureAndHashAlgorithms = serverSupportedSignatureAndHashAlgorithms;
    }

    public void setServerSupportedSignatureAndHashAlgorithms(
            SignatureAndHashAlgorithm... serverSupportedSignatureAndHashAlgorithms) {
        this.serverSupportedSignatureAndHashAlgorithms = new ArrayList(
                Arrays.asList(serverSupportedSignatureAndHashAlgorithms));
    }

    public ProtocolVersion getSelectedProtocolVersion() {
        return selectedProtocolVersion;
    }

    public void setSelectedProtocolVersion(ProtocolVersion selectedProtocolVersion) {
        this.selectedProtocolVersion = selectedProtocolVersion;
    }

    public ProtocolVersion getHighestClientProtocolVersion() {
        return highestClientProtocolVersion;
    }

    public void setHighestClientProtocolVersion(ProtocolVersion highestClientProtocolVersion) {
        this.highestClientProtocolVersion = highestClientProtocolVersion;
    }

    public ConnectionEndType getTalkingConnectionEndType() {
        return talkingConnectionEndType;
    }

    public void setTalkingConnectionEndType(ConnectionEndType talkingConnectionEndType) {
        this.talkingConnectionEndType = talkingConnectionEndType;
    }

    public byte[] getMasterSecret() {
        return masterSecret;
    }

    public CipherSuite getSelectedCipherSuite() {
        return selectedCipherSuite;
    }

    public void setMasterSecret(byte[] masterSecret) {
        this.masterSecret = masterSecret;
    }

    public void setSelectedCipherSuite(CipherSuite selectedCipherSuite) {
        this.selectedCipherSuite = selectedCipherSuite;
    }

    public byte[] getClientServerRandom() {
        return ArrayConverter.concatenate(clientRandom, serverRandom);
    }

    public byte[] getPreMasterSecret() {
        return preMasterSecret;
    }

    public void setPreMasterSecret(byte[] preMasterSecret) {
        this.preMasterSecret = preMasterSecret;
    }

    public byte[] getClientRandom() {
        return clientRandom;
    }

    public void setClientRandom(byte[] clientRandom) {
        this.clientRandom = clientRandom;
    }

    public byte[] getServerRandom() {
        return serverRandom;
    }

    public void setServerRandom(byte[] serverRandom) {
        this.serverRandom = serverRandom;
    }

    public CompressionMethod getSelectedCompressionMethod() {
        return selectedCompressionMethod;
    }

    public void setSelectedCompressionMethod(CompressionMethod selectedCompressionMethod) {
        this.selectedCompressionMethod = selectedCompressionMethod;
    }

    public byte[] getServerSessionId() {
        return serverSessionId;
    }

    public void setServerSessionId(byte[] serverSessionId) {
        this.serverSessionId = serverSessionId;
    }

    public byte[] getClientSessionId() {
        return clientSessionId;
    }

    public void setClientSessionId(byte[] clientSessionId) {
        this.clientSessionId = clientSessionId;
    }

    public Certificate getServerCertificate() {
        return serverCertificate;
    }

    public void setServerCertificate(Certificate serverCertificate) {
        this.serverCertificate = serverCertificate;
    }

    public Certificate getClientCertificate() {
        return clientCertificate;
    }

    public void setClientCertificate(Certificate clientCertificate) {
        this.clientCertificate = clientCertificate;
    }

    public MessageDigestCollector getDigest() {
        return digest;
    }

    public byte[] getDtlsCookie() {
        return dtlsCookie;
    }

    public void setDtlsCookie(byte[] dtlsCookie) {
        this.dtlsCookie = dtlsCookie;
    }

    public TransportHandler getTransportHandler() {
        return transportHandler;
    }

    public void setTransportHandler(TransportHandler transportHandler) {
        this.transportHandler = transportHandler;
    }

    public RecordLayer getRecordLayer() {
        return recordLayer;
    }

    public void setRecordLayer(RecordLayer recordLayer) {
        this.recordLayer = recordLayer;
    }

    public PRFAlgorithm getPrfAlgorithm() {
        return prfAlgorithm;
    }

    public void setPrfAlgorithm(PRFAlgorithm prfAlgorithm) {
        this.prfAlgorithm = prfAlgorithm;
    }

    public byte[] getClientHandshakeTrafficSecret() {
        return clientHandshakeTrafficSecret;
    }

    public void setClientHandshakeTrafficSecret(byte[] clientHandshakeTrafficSecret) {
        this.clientHandshakeTrafficSecret = clientHandshakeTrafficSecret;
    }

    public byte[] getServerHandshakeTrafficSecret() {
        return serverHandshakeTrafficSecret;
    }

    public void setServerHandshakeTrafficSecret(byte[] serverHandshakeTrafficSecret) {
        this.serverHandshakeTrafficSecret = serverHandshakeTrafficSecret;
    }

    public byte[] getClientApplicationTrafficSecret() {
        return clientApplicationTrafficSecret;
    }

    public void setClientApplicationTrafficSecret(byte[] clientApplicationTrafficSecret) {
        this.clientApplicationTrafficSecret = clientApplicationTrafficSecret;
    }

    public byte[] getServerApplicationTrafficSecret() {
        return serverApplicationTrafficSecret;
    }

    public void setServerApplicationTrafficSecret(byte[] serverApplicationTrafficSecret) {
        this.serverApplicationTrafficSecret = serverApplicationTrafficSecret;
    }

    public byte[] getHandshakeSecret() {
        return handshakeSecret;
    }

    public void setHandshakeSecret(byte[] handshakeSecret) {
        this.handshakeSecret = handshakeSecret;
    }

    public List<KSEntry> getClientKeyShareEntryList() {
        return clientKeyShareEntryList;
    }

    public void setClientKeyShareEntryList(List<KSEntry> clientKeyShareEntryList) {
        this.clientKeyShareEntryList = clientKeyShareEntryList;
    }

    public void setClientKSEntryList(KSEntry... clientKSEntryList) {
        this.clientKeyShareEntryList = new ArrayList(Arrays.asList(clientKSEntryList));
    }

    public KSEntry getServerKSEntry() {
        return serverKSEntry;
    }

    public void setServerKSEntry(KSEntry serverKSEntry) {
        this.serverKSEntry = serverKSEntry;
    }

    public byte[] getSessionTicketTLS() {
        return sessionTicketTLS;
    }

    public void setSessionTicketTLS(byte[] sessionTicketTLS) {
        this.sessionTicketTLS = sessionTicketTLS;
    }

    public byte[] getSignedCertificateTimestamp() {
        return signedCertificateTimestamp;
    }

    public void setSignedCertificateTimestamp(byte[] signedCertificateTimestamp) {
        this.signedCertificateTimestamp = signedCertificateTimestamp;
    }

    public byte[] getRenegotiationInfo() {
        return renegotiationInfo;
    }

    public void setRenegotiationInfo(byte[] renegotiationInfo) {
        this.renegotiationInfo = renegotiationInfo;
    }

    public TokenBindingVersion getTokenBindingVersion() {
        return tokenBindingVersion;
    }

    public void setTokenBindingVersion(TokenBindingVersion tokenBindingVersion) {
        this.tokenBindingVersion = tokenBindingVersion;
    }

    public List<TokenBindingKeyParameters> getTokenBindingKeyParameters() {
        return tokenBindingKeyParameters;
    }

    public void setTokenBindingKeyParameters(List<TokenBindingKeyParameters> tokenBindingKeyParameters) {
        this.tokenBindingKeyParameters = tokenBindingKeyParameters;
    }

    public void setTokenBindingNegotiatedSuccessfully(boolean tokenBindingNegotiated) {
        this.tokenBindingNegotiatedSuccessfully = tokenBindingNegotiated;
    }

    public boolean isTokenBindingNegotiatedSuccessfully() {
        return tokenBindingNegotiatedSuccessfully;
    }

    public CertificateStatusRequestType getCertificateStatusRequestExtensionRequestType() {
        return certificateStatusRequestExtensionRequestType;
    }

    public void setCertificateStatusRequestExtensionRequestType(
            CertificateStatusRequestType certificateStatusRequestExtensionRequestType) {
        this.certificateStatusRequestExtensionRequestType = certificateStatusRequestExtensionRequestType;
    }

    public byte[] getCertificateStatusRequestExtensionResponderIDList() {
        return certificateStatusRequestExtensionResponderIDList;
    }

    public void setCertificateStatusRequestExtensionResponderIDList(
            byte[] certificateStatusRequestExtensionResponderIDList) {
        this.certificateStatusRequestExtensionResponderIDList = certificateStatusRequestExtensionResponderIDList;
    }

    public byte[] getCertificateStatusRequestExtensionRequestExtension() {
        return certificateStatusRequestExtensionRequestExtension;
    }

    public void setCertificateStatusRequestExtensionRequestExtension(
            byte[] certificateStatusRequestExtensionRequestExtension) {
        this.certificateStatusRequestExtensionRequestExtension = certificateStatusRequestExtensionRequestExtension;
    }

    public byte[] getAlpnAnnouncedProtocols() {
        return AlpnAnnouncedProtocols;
    }

    public void setAlpnAnnouncedProtocols(byte[] AlpnAnnouncedProtocols) {
        this.AlpnAnnouncedProtocols = AlpnAnnouncedProtocols;
    }

    public byte[] getSecureRemotePasswordExtensionIdentifier() {
        return secureRemotePasswordExtensionIdentifier;
    }

    public void setSecureRemotePasswordExtensionIdentifier(byte[] secureRemotePasswordExtensionIdentifier) {
        this.secureRemotePasswordExtensionIdentifier = secureRemotePasswordExtensionIdentifier;
    }

    public List<SrtpProtectionProfiles> getSecureRealTimeTransportProtocolProtectionProfiles() {
        return secureRealTimeTransportProtocolProtectionProfiles;
    }

    public void setSecureRealTimeTransportProtocolProtectionProfiles(
            List<SrtpProtectionProfiles> secureRealTimeTransportProtocolProtectionProfiles) {
        this.secureRealTimeTransportProtocolProtectionProfiles = secureRealTimeTransportProtocolProtectionProfiles;
    }

    public byte[] getSecureRealTimeProtocolMasterKeyIdentifier() {
        return secureRealTimeProtocolMasterKeyIdentifier;
    }

    public void setSecureRealTimeProtocolMasterKeyIdentifier(byte[] secureRealTimeProtocolMasterKeyIdentifier) {
        this.secureRealTimeProtocolMasterKeyIdentifier = secureRealTimeProtocolMasterKeyIdentifier;
    }

    public UserMappingExtensionHintType getUserMappingExtensionHintType() {
        return userMappingExtensionHintType;
    }

    public void setUserMappingExtensionHintType(UserMappingExtensionHintType userMappingExtensionHintType) {
        this.userMappingExtensionHintType = userMappingExtensionHintType;
    }

    public List<CertificateType> getCertificateTypeDesiredTypes() {
        return certificateTypeClientDesiredTypes;
    }

    public void setCertificateTypeDesiredTypes(List<CertificateType> certificateTypeDesiredTypes) {
        this.certificateTypeClientDesiredTypes = certificateTypeDesiredTypes;
    }

    public List<AuthzDataFormat> getClientAuthzDataFormatList() {
        return clientAuthzDataFormatList;
    }

    public void setClientAuthzDataFormatList(List<AuthzDataFormat> clientAuthzDataFormatList) {
        this.clientAuthzDataFormatList = clientAuthzDataFormatList;
    }

    public List<AuthzDataFormat> getServerAuthzDataFormatList() {
        return serverAuthzDataFormatList;
    }

    public void setServerAuthzDataFormatList(List<AuthzDataFormat> serverAuthzDataFormatList) {
        this.serverAuthzDataFormatList = serverAuthzDataFormatList;
    }

    public void setTokenBindingKeyParameters(TokenBindingKeyParameters... tokenBindingKeyParameters) {
        this.tokenBindingKeyParameters = new ArrayList(Arrays.asList(tokenBindingKeyParameters));
    }

    public byte[] getCertificateRequestContext() {
        return certificateRequestContext;
    }

    public void setCertificateRequestContext(byte[] certificateRequestContext) {
        this.certificateRequestContext = certificateRequestContext;
    }

    public List<CertificateType> getClientCertificateTypeDesiredTypes() {
        return clientCertificateTypeDesiredTypes;
    }

    public void setClientCertificateTypeDesiredTypes(List<CertificateType> clientCertificateTypeDesiredTypes) {
        this.clientCertificateTypeDesiredTypes = clientCertificateTypeDesiredTypes;
    }

    public List<CertificateType> getServerCertificateTypeDesiredTypes() {
        return serverCertificateTypeDesiredTypes;
    }

    public void setServerCertificateTypeDesiredTypes(List<CertificateType> serverCertificateTypeDesiredTypes) {
        this.serverCertificateTypeDesiredTypes = serverCertificateTypeDesiredTypes;
    }

    public boolean isCachedInfoExtensionClientState() {
        return cachedInfoExtensionClientState;
    }

    public void setCachedInfoExtensionClientState(boolean cachedInfoExtensionClientState) {
        this.cachedInfoExtensionClientState = cachedInfoExtensionClientState;
    }

    public List<CachedObject> getCachedInfoExtensionObjects() {
        return cachedInfoExtensionObjects;
    }

    public void setCachedInfoExtensionObjects(List<CachedObject> cachedInfoExtensionObjects) {
        this.cachedInfoExtensionObjects = cachedInfoExtensionObjects;
    }

    public List<TrustedAuthority> getTrustedCaIndicationExtensionCas() {
        return trustedCaIndicationExtensionCas;
    }

    public void setTrustedCaIndicationExtensionCas(List<TrustedAuthority> trustedCaIndicationExtensionCas) {
        this.trustedCaIndicationExtensionCas = trustedCaIndicationExtensionCas;
    }

    public List<RequestItemV2> getStatusRequestV2RequestList() {
        return statusRequestV2RequestList;
    }

    public void setStatusRequestV2RequestList(List<RequestItemV2> statusRequestV2RequestList) {
        this.statusRequestV2RequestList = statusRequestV2RequestList;
    }

    public BigInteger getServerRSAPrivateKey() {
        return serverRSAPrivateKey;
    }

    public void setServerRSAPrivateKey(BigInteger serverRSAPrivateKey) {
        this.serverRSAPrivateKey = serverRSAPrivateKey;
    }

    public BigInteger getClientRSAPrivateKey() {
        return clientRSAPrivateKey;
    }

    public void setClientRSAPrivateKey(BigInteger clientRSAPrivateKey) {
        this.clientRSAPrivateKey = clientRSAPrivateKey;
    }

    public boolean isEarlyCleanShutdown() {
        return earlyCleanShutdown;
    }

    public Random getRandom() {
        return random;
    }

    public void setEarlyCleanShutdown(boolean earlyCleanShutdown) {
        this.earlyCleanShutdown = earlyCleanShutdown;
    }

    public void setRandom(Random random) {
        this.random = random;
    }

    public BadRandom getBadSecureRandom() {
        return new BadRandom(getRandom(), null);
    }

    public Config getConfig() {
        return config;
    }

    public ConnectionEnd getConnectionEnd() {
        return connectionEnd;
    }

    public void setConnectionEnd(ConnectionEnd connectionEnd) {
        this.connectionEnd = connectionEnd;
    }

    public RecordLayerType getRecordLayerType() {
        return recordLayerType;
    }

    public void setRecordLayerType(RecordLayerType recordLayerType) {
        this.recordLayerType = recordLayerType;
    }

    public ProtocolVersion getHighestProtocolVersion() {
        return highestProtocolVersion;
    }

    public void setHighestProtocolVersion(ProtocolVersion highestProtocolVersion) {
        this.highestProtocolVersion = highestProtocolVersion;
    }

    public Boolean isClientAuthentication() {
        return clientAuthentication;
    }

    public void setClientAuthentication(Boolean clientAuthentication) {
        this.clientAuthentication = clientAuthentication;
    }

    public byte[] getLastHandledApplicationMessageData() {
        return lastHandledApplicationMessageData;
    }

    public void setLastHandledApplicationMessageData(byte[] lastHandledApplicationMessageData) {
        this.lastHandledApplicationMessageData = lastHandledApplicationMessageData;
    }

    /**
     * Check if the given TLS extension type was proposed by the client.
     *
     * @param ext
     *            The ExtensionType to check for
     * @return true if extension was proposed by client, false otherwise
     */
    public boolean isExtensionProposed(ExtensionType ext) {
        return proposedExtensionSet.contains(ext);
    }

    /**
     * Mark the given TLS extension type as client proposed extension.
     * 
     * @param ext
     *            The ExtensionType that is proposed
     */
    public void addProposedExtension(ExtensionType ext) {
        proposedExtensionSet.add(ext);
    }

    /**
     * Check if the given TLS extension type was sent by the server.
     *
     * @param ext
     *            The ExtensionType to check for
     * @return true if extension was proposed by server, false otherwise
     */
    public boolean isExtensionNegotiated(ExtensionType ext) {
        return negotiatedExtensionSet.contains(ext);
    }

    /**
     * Mark the given TLS extension type as server negotiated extension.
     * 
     * @param ext
     *            The ExtensionType to add
     */
    public void addNegotiatedExtension(ExtensionType ext) {
        negotiatedExtensionSet.add(ext);
    }

    public boolean isUseExtendedMasterSecret() {
        return useExtendedMasterSecret;
    }

    public void setUseExtendedMasterSecret(boolean useExtendedMasterSecret) {
        this.useExtendedMasterSecret = useExtendedMasterSecret;
    }

    /**
     * Initialize the context's transport handler. Start listening or connect to
     * a server, depending on our connection end type.
     */
    public void initTransportHandler() {

        if (transportHandler == null) {
            if (connectionEnd == null) {
                throw new ConfigurationException("Connection end not set");
            }
            transportHandler = TransportHandlerFactory.createTransportHandler(connectionEnd);
        }

        try {
            transportHandler.initialize();
        } catch (NullPointerException | NumberFormatException ex) {
            throw new ConfigurationException("Invalid values in " + connectionEnd.toString(), ex);
        } catch (IOException ex) {
            throw new ConfigurationException("Unable to initialize the transport handler with: "
                    + connectionEnd.toString(), ex);
        }
    }

    /**
     * Initialize the context's record layer.
     */
    public void initRecordLayer() {
        if (recordLayerType == null) {
            throw new ConfigurationException("No record layer type defined");
        }
        recordLayer = RecordLayerFactory.getRecordLayer(recordLayerType, this);
    }

    @Override
    public String toString() {
        StringBuilder info = new StringBuilder();
        info.append("TlsContext{ '").append(connectionEnd.getAlias()).append("'");
        if (connectionEnd.getConnectionEndType() == ConnectionEndType.SERVER) {
            info.append(", listening on port ").append(connectionEnd.getPort());
        } else {
            info.append(", connected to ").append(connectionEnd.getHostname()).append(":")
                    .append(connectionEnd.getPort());
        }
        info.append("}");
        return info.toString();
    }

    /**
     * @return the clientEarlyTrafficSecret
     */
    public byte[] getClientEarlyTrafficSecret() {
        return clientEarlyTrafficSecret;
    }

    /**
     * @param clientEarlyTrafficSecret
     *            the clientEarlyTrafficSecret to set
     */
    public void setClientEarlyTrafficSecret(byte[] clientEarlyTrafficSecret) {
        this.clientEarlyTrafficSecret = clientEarlyTrafficSecret;
    }

    /**
     * @return the maxEarlyDataSize
     */
    public long getMaxEarlyDataSize() {
        return maxEarlyDataSize;
    }

    /**
     * @param maxEarlyDataSize
     *            the maxEarlyDataSize to set
     */
    public void setMaxEarlyDataSize(long maxEarlyDataSize) {
        this.maxEarlyDataSize = maxEarlyDataSize;
    }

    /**
     * @return the psk
     */
    public byte[] getPsk() {
        return psk;
    }

    /**
     * @param psk
     *            the psk to set
     */
    public void setPsk(byte[] psk) {
        this.psk = psk;
    }

    /**
     * @return the earlySecret
     */
    public byte[] getEarlySecret() {
        return earlySecret;
    }

    /**
     * @param earlySecret
     *            the earlySecret to set
     */
    public void setEarlySecret(byte[] earlySecret) {
        this.earlySecret = earlySecret;
    }

    /**
     * @return the encryptedEndOfEarlyData
     */
    public boolean isEncryptedEndOfEarlyData() {
        return encryptedEndOfEarlyData;
    }

    /**
     * @param encryptedEndOfEarlyData
     *            the encryptedEndOfEarlyData to set
     */
    public void setEncryptedEndOfEarlyData(boolean encryptedEndOfEarlyData) {
        this.encryptedEndOfEarlyData = encryptedEndOfEarlyData;
    }

    /**
     * @return the storedSequenceNumberDec
     */
    public long getStoredSequenceNumberDec() {
        return storedSequenceNumberDec;
    }

    /**
     * @param storedSequenceNumberDec
     *            the storedSequenceNumberDec to set
     */
    public void setStoredSequenceNumberDec(long storedSequenceNumberDec) {
        this.storedSequenceNumberDec = storedSequenceNumberDec;
    }

    /**
     * @return the earlyDataCipherSuite
     */
    public CipherSuite getEarlyDataCipherSuite() {
        return earlyDataCipherSuite;
    }

    /**
     * @param earlyDataCipherSuite
     *            the earlyDataCipherSuite to set
     */
    public void setEarlyDataCipherSuite(CipherSuite earlyDataCipherSuite) {
        this.earlyDataCipherSuite = earlyDataCipherSuite;
    }

    /**
     * @return the earlyDataPSKIdentity
     */
    public byte[] getEarlyDataPSKIdentity() {
        return earlyDataPSKIdentity;
    }

    /**
     * @param earlyDataPSKIdentity
     *            the earlyDataPSKIdentity to set
     */
    public void setEarlyDataPSKIdentity(byte[] earlyDataPSKIdentity) {
        this.earlyDataPSKIdentity = earlyDataPSKIdentity;
    }

    /**
     * @return the selectedIdentityIndex
     */
    public int getSelectedIdentityIndex() {
        return selectedIdentityIndex;
    }

    /**
     * @param selectedIdentityIndex
     *            the selectedIdentityIndex to set
     */
    public void setSelectedIdentityIndex(int selectedIdentityIndex) {
        this.selectedIdentityIndex = selectedIdentityIndex;
    }

    /**
     * @return the storedSequenceNumberEnc
     */
    public long getStoredSequenceNumberEnc() {
        return storedSequenceNumberEnc;
    }

    /**
     * @param storedSequenceNumberEnc
     *            the storedSequenceNumberEnc to set
     */
    public void setStoredSequenceNumberEnc(long storedSequenceNumberEnc) {
        this.storedSequenceNumberEnc = storedSequenceNumberEnc;
    }

    /**
     * @return the clientPskKeyExchangeModes
     */
    public List<PskKeyExchangeMode> getClientPskKeyExchangeModes() {
        return clientPskKeyExchangeModes;
    }

    /**
     * @param clientPskKeyExchangeModes
     *            the clientPskKeyExchangeModes to set
     */
    public void setClientPskKeyExchangeModes(List<PskKeyExchangeMode> clientPskKeyExchangeModes) {
        this.clientPskKeyExchangeModes = clientPskKeyExchangeModes;
    }

    /**
     * @return the activeKeySetType
     */
    public Tls13KeySetType getActiveKeySetType() {
        return activeKeySetType;
    }

    /**
     * @param activeKeySetType
     *            the activeKeySetType to set
     */
    public void setActiveKeySetType(Tls13KeySetType activeKeySetType) {
        this.activeKeySetType = activeKeySetType;
    }

    /**
     * @return the expectingEndOfEarlyData
     */
    public boolean isExpectingEndOfEarlyData() {
        return expectingEndOfEarlyData;
    }

    /**
     * @param expectingEndOfEarlyData
     *            the expectingEndOfEarlyData to set
     */
    public void setExpectingEndOfEarlyData(boolean expectingEndOfEarlyData) {
        this.expectingEndOfEarlyData = expectingEndOfEarlyData;
    }

}
