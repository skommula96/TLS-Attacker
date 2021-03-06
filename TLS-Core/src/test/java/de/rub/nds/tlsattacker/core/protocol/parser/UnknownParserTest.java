/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2020 Ruhr University Bochum, Paderborn University,
 * and Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.parser;

import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.protocol.message.UnknownMessage;
import static org.junit.Assert.*;
import org.junit.Test;

public class UnknownParserTest {

    private UnknownParser parser;
    private final Config config = Config.createConfig();

    /**
     * Test of parse method, of class UnknownParser.
     */
    @Test
    public void testParse() {
        parser = new UnknownParser(0, new byte[] { 0, 1, 2, 3 }, ProtocolVersion.TLS12, config);
        UnknownMessage message = parser.parse();
        assertArrayEquals(new byte[] { 0, 1, 2, 3 }, message.getCompleteResultingMessage().getValue());
        parser = new UnknownParser(1, new byte[] { 0, 1, 2, 3 }, ProtocolVersion.TLS12, config);
        message = parser.parse();
        assertArrayEquals(new byte[] { 1, 2, 3 }, message.getCompleteResultingMessage().getValue());
    }

}
