/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.tls.protocol.handler;

import de.rub.nds.tlsattacker.tls.constants.CipherSuite;
import de.rub.nds.tlsattacker.tls.constants.CompressionMethod;
import de.rub.nds.tlsattacker.tls.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.tls.protocol.message.extension.ExtensionMessage;
import de.rub.nds.tlsattacker.tls.protocol.message.ClientHelloMessage;
import de.rub.nds.tlsattacker.tls.protocol.parser.ClientHelloParser;
import de.rub.nds.tlsattacker.tls.protocol.parser.Parser;
import de.rub.nds.tlsattacker.tls.protocol.preparator.ClientHelloPreparator;
import de.rub.nds.tlsattacker.tls.protocol.preparator.Preparator;
import de.rub.nds.tlsattacker.tls.protocol.serializer.ClientHelloSerializer;
import de.rub.nds.tlsattacker.tls.protocol.serializer.Serializer;
import de.rub.nds.tlsattacker.tls.workflow.TlsContext;
import de.rub.nds.tlsattacker.util.ArrayConverter;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Juraj Somorovsky <juraj.somorovsky@rub.de>
 * @author Philip Riese <philip.riese@rub.de>
 * @param <Message>
 */
public class ClientHelloHandler extends HandshakeMessageHandler<ClientHelloMessage> {

    private static final Logger LOGGER = LogManager.getLogger("HANDLER");

    public ClientHelloHandler(TlsContext tlsContext) {
        super(tlsContext);
    }

    @Override
    public ClientHelloParser getParser(byte[] message, int pointer) {
        return new ClientHelloParser(pointer, message, tlsContext.getLastRecordVersion());
    }

    @Override
    public ClientHelloPreparator getPreparator(ClientHelloMessage message) {
        return new ClientHelloPreparator(tlsContext, message);
    }

    @Override
    public ClientHelloSerializer getSerializer(ClientHelloMessage message) {
        return new ClientHelloSerializer(message, tlsContext.getSelectedProtocolVersion());
    }

    @Override
    protected void adjustTLSContext(ClientHelloMessage message) {
        adjustRandomContext(message);
        adjustProtocolVersion(message);
        adjustClientSupportedCipherSuites(message);
        adjustClientSupportedCompressions(message);
        if (isCookieFieldSet(message)) {
            adjustDTLSCookie(message);
        }
        adjustSessionID(message);
        for (ExtensionMessage extension : message.getExtensions()) {
            throw new UnsupportedOperationException("Get extensionHandlers here and adjust context");
        }
    }

    private boolean isCookieFieldSet(ClientHelloMessage message) {
        return message.getCookie() != null;
    }

    private void adjustClientSupportedCipherSuites(ClientHelloMessage message) {
        List<CipherSuite> suiteList = convertCipherSuites(message.getCipherSuites().getValue());
        tlsContext.setClientSupportedCiphersuites(suiteList);
        LOGGER.debug("Set ClientSupportedCiphersuites in Context to " + suiteList.toString());
    }

    private void adjustClientSupportedCompressions(ClientHelloMessage message) {
        List<CompressionMethod> compressionList = convertCompressionMethods(message.getCompressions().getValue());
        tlsContext.setClientSupportedCompressions(compressionList);
        LOGGER.debug("Set ClientSupportedCompressions in Context to " + compressionList.toString());
    }

    private void adjustDTLSCookie(ClientHelloMessage message) {
        byte[] dtlsCookie = message.getCookie().getValue();
        tlsContext.setDtlsHandshakeCookie(dtlsCookie);
        LOGGER.debug("Set DTLS Cookie in Context to " + ArrayConverter.bytesToHexString(dtlsCookie));
    }

    private void adjustSessionID(ClientHelloMessage message) {
        byte[] sessionId = message.getSessionId().getValue();
        tlsContext.setSessionID(sessionId);
        LOGGER.debug("Set SessionId in Context to " + ArrayConverter.bytesToHexString(sessionId, false));
    }

    private void adjustProtocolVersion(ClientHelloMessage message) {
        ProtocolVersion version = ProtocolVersion.getProtocolVersion(message.getProtocolVersion().getValue());
        tlsContext.setHighestClientProtocolVersion(version);
        LOGGER.debug("Set HighestClientProtocolVersion in Context to " + version.name());
    }

    private void adjustRandomContext(ClientHelloMessage message) {
        setClientRandomContext(message.getUnixTime().getValue(), message.getRandom().getValue());
        LOGGER.debug("Set ClientRandom in Context to " + ArrayConverter.bytesToHexString(tlsContext.getClientRandom()));
    }

    private void setClientRandomContext(byte[] unixTime, byte[] random) {
        tlsContext.setClientRandom(ArrayConverter.concatenate(unixTime, random));
    }

    private List<CompressionMethod> convertCompressionMethods(byte[] bytesToConvert) {
        List<CompressionMethod> list = new LinkedList<>();
        for (byte b : bytesToConvert) {
            CompressionMethod method = CompressionMethod.getCompressionMethod(b);
            if (method == null) {
                LOGGER.warn("Could not convert " + b + " into a CompressionMethod");
            } else {
                list.add(method);
            }
        }
        return list;
    }

    private List<CipherSuite> convertCipherSuites(byte[] bytesToConvert) {
        if (bytesToConvert.length % 2 != 0) {
            LOGGER.warn("Cannot convert:" + ArrayConverter.bytesToHexString(bytesToConvert, false)
                    + " to a List<CipherSuite>");
            return null;
        }
        List<CipherSuite> list = new LinkedList<>();

        for (int i = 0; i < bytesToConvert.length; i = i + 2) {
            byte[] copied = new byte[2];
            copied[0] = bytesToConvert[i];
            copied[1] = bytesToConvert[i + 1];
            CipherSuite suite = CipherSuite.getCipherSuite(copied);
            if (suite == null) {
                LOGGER.warn("Cannot convert:" + ArrayConverter.bytesToHexString(copied) + " to a CipherSuite");
            } else {
                list.add(suite);
            }
        }
        return list;
    }
}
