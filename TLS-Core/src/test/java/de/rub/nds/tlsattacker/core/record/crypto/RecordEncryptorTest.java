/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.record.crypto;

import de.rub.nds.modifiablevariable.util.ArrayConverter;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.ExtensionType;
import de.rub.nds.tlsattacker.core.constants.ProtocolMessageType;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.record.Record;
import de.rub.nds.tlsattacker.core.record.cipher.cryptohelper.KeySetGenerator;
import de.rub.nds.tlsattacker.core.record.cipher.RecordAEADCipher;
import de.rub.nds.tlsattacker.core.record.cipher.RecordBlockCipher;
import de.rub.nds.tlsattacker.core.record.cipher.RecordCipher;
import de.rub.nds.tlsattacker.core.record.cipher.RecordStreamCipher;
import de.rub.nds.tlsattacker.core.state.TlsContext;
import de.rub.nds.tlsattacker.transport.ServerConnectionEnd;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Random;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.test.TestRandomData;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Nurullah Erinola <nurullah.erinola@rub.de>
 */
public class RecordEncryptorTest {

    private RecordCipher recordCipher;
    private TlsContext context;
    private Record record;
    public RecordEncryptor encryptor;

    public RecordEncryptorTest() {
    }

    @Before
    public void setUp() {
        Security.addProvider(new BouncyCastleProvider());
        context = new TlsContext();
        record = new Record();
        record.setContentType(ProtocolMessageType.HANDSHAKE.getValue());
        record.setProtocolVersion(ProtocolVersion.TLS10.getValue());
        record.setSequenceNumber(BigInteger.ZERO);
    }

    /**
     * Test of the encrypt method for TLS 1.3, of class RecordEncryptor.
     */
    @Test
    public void testEncryptTLS13() throws NoSuchAlgorithmException {
        context.setSelectedProtocolVersion(ProtocolVersion.TLS13);
        context.setSelectedCipherSuite(CipherSuite.TLS_AES_128_GCM_SHA256);
        context.setEncryptActive(true);
        context.setClientHandshakeTrafficSecret(ArrayConverter
                .hexStringToByteArray("4B63051EABCD514D7CB6D1899F472B9F56856B01BDBC5B733FBB47269E7EBDC2"));
        context.setServerHandshakeTrafficSecret(ArrayConverter
                .hexStringToByteArray("ACC9DB33EE0968FAE7E06DAA34D642B146092CE7F9C9CF47670C66A0A6CE1C8C"));
        context.setConnectionEnd(new ServerConnectionEnd());
        record.setCleanProtocolMessageBytes(ArrayConverter.hexStringToByteArray("080000020000"));
        record.setContentMessageType(ProtocolMessageType.HANDSHAKE);
        record.setPaddingLength(0);
        recordCipher = new RecordAEADCipher(context, KeySetGenerator.generateKeySet(context));
        encryptor = new RecordEncryptor(recordCipher, context);
        encryptor.encrypt(record);
        assertTrue(record.getProtocolMessageBytes().getValue().length == 23);
        assertArrayEquals(record.getProtocolMessageBytes().getValue(),
                ArrayConverter.hexStringToByteArray("1BB3293A919E0D66F145AE830488E8D89BE5EC16688229"));
    }

    @Test
    public void testEncryptTLS12Block() throws NoSuchAlgorithmException {
        Random random = new TestRandomData(
                ArrayConverter.hexStringToByteArray("91A3B6AAA2B64D126E5583B04C113259C948E1D0B39BB9560CD5409B6ECAFEDB"));//
        // explicit
        // IV's
        context.setRandom(random);
        context.setSelectedProtocolVersion(ProtocolVersion.TLS12);
        context.setSelectedCipherSuite(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA);
        context.setMasterSecret(ArrayConverter
                .hexStringToByteArray("4F04374497508D697BE7E39983B26E77DB6C60178C9B47D437E4E37F910923ECD779FDBC11D55B69E311A58CBF2FDC8C"));
        context.setClientRandom(ArrayConverter
                .hexStringToByteArray("EB9DB77B60B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C"));
        context.setServerRandom(ArrayConverter
                .hexStringToByteArray("59D5EAEF4D34A1FC14E3417E6ED24FD5FB39B5009D9CB3181CECDAFB46D1EBD4"));
        record.setCleanProtocolMessageBytes(ArrayConverter.hexStringToByteArray("1400000CB692015BE123B8364314FE1C"));
        record.setContentMessageType(ProtocolMessageType.HANDSHAKE);
        record.setProtocolVersion(ProtocolVersion.TLS12.getValue());
        recordCipher = new RecordBlockCipher(context, KeySetGenerator.generateKeySet(context));
        encryptor = new RecordEncryptor(recordCipher, context);
        encryptor.encrypt(record);
        assertArrayEquals(ArrayConverter.hexStringToByteArray("00000000000000001603030010"), record
                .getAuthenticatedMetaData().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("1400000CB692015BE123B8364314FE1C"), record
                .getNonMetaDataMaced().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("BD527A01EDCA68BF5A7918C190942A9AECA971CA"), record
                .getMac().getValue());
        assertArrayEquals(
                ArrayConverter
                        .hexStringToByteArray("1400000CB692015BE123B8364314FE1CBD527A01EDCA68BF5A7918C190942A9AECA971CA"),
                record.getUnpaddedRecordBytes().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("0B0B0B0B0B0B0B0B0B0B0B0B"), record.getPadding()
                .getValue());
        assertArrayEquals(
                ArrayConverter
                        .hexStringToByteArray("1400000CB692015BE123B8364314FE1CBD527A01EDCA68BF5A7918C190942A9AECA971CA0B0B0B0B0B0B0B0B0B0B0B0B"),
                record.getPlainRecordBytes().getValue());
        assertNull(record.getInitialisationVector());
        assertTrue(12 == record.getPaddingLength().getValue());
        assertArrayEquals(
                record.getProtocolMessageBytes().getValue(),
                ArrayConverter
                        .hexStringToByteArray("91A3B6AAA2B64D126E5583B04C1132591010FF2EE70446DA41EA4D83FE2DA55ADFAB9A17F5ACED2BA0068A95B30825119705383687AE8F0DC1BFC17E6D407CF9"));
    }

    @Test
    public void testEncryptTLS12Stream() throws NoSuchAlgorithmException {
        context.setSelectedProtocolVersion(ProtocolVersion.TLS12);
        context.setSelectedCipherSuite(CipherSuite.TLS_RSA_WITH_RC4_128_SHA);
        context.setMasterSecret(ArrayConverter
                .hexStringToByteArray("CD119411443999A5D3CBAF5B4DDC3D3CC51AFEB39FE987A8C0EA6E25D91FCDD1A1A7C46C68660EC4D0F3B6D44EAF988C"));
        context.setClientRandom(ArrayConverter
                .hexStringToByteArray("1669F42460B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C"));
        context.setServerRandom(ArrayConverter
                .hexStringToByteArray("A36EF73D371D709E9ED42DDA38DA8F750AD4BB6D21D4E496A22A20BD6349EFFA"));
        record.setCleanProtocolMessageBytes(ArrayConverter.hexStringToByteArray("1400000CDF6663DF2F42C83E1EA94381"));
        record.setContentMessageType(ProtocolMessageType.HANDSHAKE);
        record.setProtocolVersion(ProtocolVersion.TLS12.getValue());
        recordCipher = new RecordStreamCipher(context, KeySetGenerator.generateKeySet(context));
        encryptor = new RecordEncryptor(recordCipher, context);
        encryptor.encrypt(record);
        assertArrayEquals(ArrayConverter.hexStringToByteArray("00000000000000001603030010"), record
                .getAuthenticatedMetaData().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("1400000CDF6663DF2F42C83E1EA94381"), record
                .getNonMetaDataMaced().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("CE4D3A05054892056A014B28E3AF613105583FAA"), record
                .getMac().getValue());
        assertArrayEquals(
                ArrayConverter
                        .hexStringToByteArray("1400000CDF6663DF2F42C83E1EA94381CE4D3A05054892056A014B28E3AF613105583FAA"),
                record.getUnpaddedRecordBytes().getValue());
        assertArrayEquals(new byte[0], record.getPadding().getValue());
        assertArrayEquals(
                ArrayConverter
                        .hexStringToByteArray("1400000CDF6663DF2F42C83E1EA94381CE4D3A05054892056A014B28E3AF613105583FAA"),
                record.getPlainRecordBytes().getValue());
        assertNull(record.getInitialisationVector());
        assertTrue(0 == record.getPaddingLength().getValue());
        assertArrayEquals(
                record.getProtocolMessageBytes().getValue(),
                ArrayConverter
                        .hexStringToByteArray("75EA7164E864B8CC30D625BC4B546C8B36E238A1391FAF2580ED287EC56585FE022B2326"));
    }

    @Test
    public void testEncryptTLS12AEAD() throws NoSuchAlgorithmException {
        context.setSelectedProtocolVersion(ProtocolVersion.TLS12);
        context.setSelectedCipherSuite(CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256);
        context.setMasterSecret(ArrayConverter
                .hexStringToByteArray("A12C1685B91E4EF31955842FACD5CC9D62F1663DBDC6EE9659A7D724FDDA76FCB5B6033302AAC0AD0D9C3B27C7DCC0D5"));
        context.setClientRandom(ArrayConverter
                .hexStringToByteArray("167D244D60B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C"));
        context.setServerRandom(ArrayConverter
                .hexStringToByteArray("41C2F030D70B2944BE2B1905BB8488C873E056B030413788D5D1ECADC17C3C39"));
        record.setCleanProtocolMessageBytes(ArrayConverter.hexStringToByteArray("1400000C0C821172BB87E255D5C6E078"));
        record.setContentMessageType(ProtocolMessageType.HANDSHAKE);
        record.setProtocolVersion(ProtocolVersion.TLS12.getValue());
        recordCipher = new RecordAEADCipher(context, KeySetGenerator.generateKeySet(context));
        encryptor = new RecordEncryptor(recordCipher, context);
        encryptor.encrypt(record);
        assertArrayEquals(ArrayConverter.hexStringToByteArray("00000000000000001603030010"), record
                .getAuthenticatedMetaData().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("1400000C0C821172BB87E255D5C6E078"), record
                .getNonMetaDataMaced().getValue());
        assertArrayEquals(new byte[0], record.getMac().getValue());
        // assertArrayEquals(new byte[0], record
        // .get().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("1400000C0C821172BB87E255D5C6E078"), record
                .getUnpaddedRecordBytes().getValue());
        assertArrayEquals(new byte[0], record.getPadding().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("1400000C0C821172BB87E255D5C6E078"), record
                .getPlainRecordBytes().getValue());
        assertNull(record.getInitialisationVector());
        assertTrue(0 == record.getPaddingLength().getValue());
        assertArrayEquals(
                record.getProtocolMessageBytes().getValue(),
                ArrayConverter
                        .hexStringToByteArray("0000000000000000FA78825C25329563CD9FEDFBE49AF948797D1023DA64D914704CDFBE26DD2A47"));

    }

    @Test
    public void testEncryptTLS10Block() throws NoSuchAlgorithmException {
        context.setSelectedProtocolVersion(ProtocolVersion.TLS10);
        context.setSelectedCipherSuite(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA);
        context.setMasterSecret(ArrayConverter
                .hexStringToByteArray("293F23671D03203B533C7FCCE835ADD43A822FD34477CD0172129416279F5906013F152B5227A8AF68436DDA651567CE"));
        context.setClientRandom(ArrayConverter
                .hexStringToByteArray("19D50C2360B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C"));
        context.setServerRandom(ArrayConverter
                .hexStringToByteArray("D4370EDB60688FCF49CA9C3A81B55B5E347E0CFC4323FB7196257B17BE344723"));
        record.setCleanProtocolMessageBytes(ArrayConverter.hexStringToByteArray("1400000CD424FAF2BEA85BCE69DC07D2"));
        record.setContentMessageType(ProtocolMessageType.HANDSHAKE);
        record.setProtocolVersion(ProtocolVersion.TLS10.getValue());
        recordCipher = new RecordBlockCipher(context, KeySetGenerator.generateKeySet(context));
        encryptor = new RecordEncryptor(recordCipher, context);
        encryptor.encrypt(record);
        assertArrayEquals(ArrayConverter.hexStringToByteArray("00000000000000001603010010"), record
                .getAuthenticatedMetaData().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("1400000CD424FAF2BEA85BCE69DC07D2"), record
                .getNonMetaDataMaced().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("F4CA3F8A11CE20E1D9BE67BE9B49C2E9305035D0"), record
                .getMac().getValue());
        assertArrayEquals(
                ArrayConverter
                        .hexStringToByteArray("1400000CD424FAF2BEA85BCE69DC07D2F4CA3F8A11CE20E1D9BE67BE9B49C2E9305035D0"),
                record.getUnpaddedRecordBytes().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("0B0B0B0B0B0B0B0B0B0B0B0B"), record.getPadding()
                .getValue());
        assertArrayEquals(
                ArrayConverter
                        .hexStringToByteArray("1400000CD424FAF2BEA85BCE69DC07D2F4CA3F8A11CE20E1D9BE67BE9B49C2E9305035D00B0B0B0B0B0B0B0B0B0B0B0B"),
                record.getPlainRecordBytes().getValue());
        assertNull(record.getInitialisationVector());
        assertTrue(12 == record.getPaddingLength().getValue());
        assertArrayEquals(
                record.getProtocolMessageBytes().getValue(),
                ArrayConverter
                        .hexStringToByteArray("8B975904A7DCF746737181652428EEDEEE9A65AE6BCA9ED0BBB5E43BF1D6C38988AC98F4A72220858A9C85B94FFE67FF"));
    }

    // @Test
    public void testEncryptTLS10Stream() throws NoSuchAlgorithmException {
        context.setSelectedProtocolVersion(ProtocolVersion.TLS10);
        context.setSelectedCipherSuite(CipherSuite.TLS_RSA_WITH_RC4_128_SHA);
        context.setMasterSecret(ArrayConverter
                .hexStringToByteArray("383BA734D8F7B678D56242DE3BB607B7C2EF8B5F2640AD4A7C8E8A740B145F952F2D844B24CD4EF594D87D3AF7E1069C"));
        context.setClientRandom(ArrayConverter
                .hexStringToByteArray("19EBAD6060B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C"));
        context.setServerRandom(ArrayConverter
                .hexStringToByteArray("38EC0B0DC6860F09D55DFE8188ACD81D31E0AFD0BD1EF7177C3FB0BC867271FD"));
        record.setCleanProtocolMessageBytes(ArrayConverter.hexStringToByteArray("1400000C5BD2239FEE954F5C5CC2A66D"));
        record.setContentMessageType(ProtocolMessageType.HANDSHAKE);
        record.setProtocolVersion(ProtocolVersion.TLS10.getValue());
        recordCipher = new RecordStreamCipher(context, KeySetGenerator.generateKeySet(context));
        encryptor = new RecordEncryptor(recordCipher, context);
        encryptor.encrypt(record);
        assertArrayEquals(ArrayConverter.hexStringToByteArray("00000000000000001603010010"), record
                .getAuthenticatedMetaData().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("1400000CD424FAF2BEA85BCE69DC07D2"), record
                .getNonMetaDataMaced().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("4BD82D0657D876744D05455125FF0BE1F7FA293D"), record
                .getMac().getValue());
        assertArrayEquals(
                ArrayConverter
                        .hexStringToByteArray("1400000C5BD2239FEE954F5C5CC2A66D4BD82D0657D876744D05455125FF0BE1F7FA293D"),
                record.getUnpaddedRecordBytes().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("0B0B0B0B0B0B0B0B0B0B0B0B"), record.getPadding()
                .getValue());
        assertArrayEquals(
                ArrayConverter
                        .hexStringToByteArray("1400000C5BD2239FEE954F5C5CC2A66D4BD82D0657D876744D05455125FF0BE1F7FA293D"),
                record.getPlainRecordBytes().getValue());
        assertNull(record.getInitialisationVector());
        assertTrue(12 == record.getPaddingLength().getValue());
        assertArrayEquals(
                record.getProtocolMessageBytes().getValue(),
                ArrayConverter
                        .hexStringToByteArray("9AF018845701F7815658788BFFB824BBCAB299C4F093152814276BD641E935CA29183ABA"));

    }

    // @Test
    public void testEncryptTLS12BlockEncrypthThenMac() throws NoSuchAlgorithmException {
        context.setSelectedProtocolVersion(ProtocolVersion.TLS12);
        context.setSelectedCipherSuite(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA);
        context.addNegotiatedExtension(ExtensionType.ENCRYPT_THEN_MAC);
        context.setMasterSecret(ArrayConverter
                .hexStringToByteArray("E09012941E30BBA91FDDA116BF62153436F2E46E11456208D186E69B846C6ABED890565AF9F6F1860F3495BAB7BC3E3F"));
        context.setClientRandom(ArrayConverter
                .hexStringToByteArray("20C0D18B60B420BB3851D9D47ACB933DBE70399BF6C92DA33AF01D4FB770E98C"));
        context.setServerRandom(ArrayConverter
                .hexStringToByteArray("8E5E9BBF278A9110D9CE6F710CE530163A900CD8FE39DB6E69B5090E46516A78"));
        record.setCleanProtocolMessageBytes(ArrayConverter.hexStringToByteArray("1400000C4F93658C9C52CE070BA2684A"));
        record.setContentMessageType(ProtocolMessageType.HANDSHAKE);
        record.setProtocolVersion(ProtocolVersion.TLS12.getValue());
        recordCipher = new RecordBlockCipher(context, KeySetGenerator.generateKeySet(context));
        encryptor = new RecordEncryptor(recordCipher, context);
        encryptor.encrypt(record);
        assertArrayEquals(ArrayConverter.hexStringToByteArray("00000000000000001603030030"), record
                .getAuthenticatedMetaData().getValue());
        assertArrayEquals(
                ArrayConverter.hexStringToByteArray("1400000C4F93658C9C52CE070BA2684A0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F0F"),
                record.getNonMetaDataMaced().getValue());
        assertArrayEquals(ArrayConverter.hexStringToByteArray("9857CAD82F7B968E9512D7B584752F11B968C812"), record
                .getMac().getValue());
        assertNull(record.getInitialisationVector());
        assertTrue(12 == record.getPaddingLength().getValue());
        assertArrayEquals(
                record.getProtocolMessageBytes().getValue(),
                ArrayConverter
                        .hexStringToByteArray("91A3B6AAA2B64D126E5583B04C11325968765A462612D813DE95DB707EF84D4E19C3521A530C579595BE47E47D1BA1589857CAD82F7B968E9512D7B584752F11B968C812"));

    }

    @Test
    public void testEncryptTLS12StreamEncrypthThenMac() throws NoSuchAlgorithmException {
        context.setSelectedProtocolVersion(ProtocolVersion.TLS12);
        context.setSelectedCipherSuite(CipherSuite.TLS_RSA_WITH_RC4_128_SHA);
        context.addNegotiatedExtension(ExtensionType.ENCRYPT_THEN_MAC);
        context.setMasterSecret(ArrayConverter.hexStringToByteArray(""));
        record.setCleanProtocolMessageBytes(ArrayConverter.hexStringToByteArray("080000020000"));
        record.setContentMessageType(ProtocolMessageType.HANDSHAKE);
        recordCipher = new RecordStreamCipher(context, KeySetGenerator.generateKeySet(context));
        encryptor = new RecordEncryptor(recordCipher, context);
        encryptor.encrypt(record);

    }

    @Test
    public void testEncryptTLS12AEADEncrypthThenMac() throws NoSuchAlgorithmException {
        context.setSelectedProtocolVersion(ProtocolVersion.TLS12);
        context.setSelectedCipherSuite(CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256);
        context.addNegotiatedExtension(ExtensionType.ENCRYPT_THEN_MAC);
        context.setMasterSecret(ArrayConverter.hexStringToByteArray(""));
        record.setCleanProtocolMessageBytes(ArrayConverter.hexStringToByteArray("080000020000"));
        record.setContentMessageType(ProtocolMessageType.HANDSHAKE);
        recordCipher = new RecordAEADCipher(context, KeySetGenerator.generateKeySet(context));
        encryptor = new RecordEncryptor(recordCipher, context);
        encryptor.encrypt(record);
    }

    public void testEncrypthTLS12NullCipher() {

    }

    public void testEncrypthTLS10NullCipher() {

    }
}