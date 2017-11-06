/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.workflow.factory;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.protocol.message.ApplicationMessage;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateMessage;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateVerifyMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ChangeCipherSpecMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ClientHelloMessage;
import de.rub.nds.tlsattacker.core.protocol.message.FinishedMessage;
import de.rub.nds.tlsattacker.core.protocol.message.HeartbeatMessage;
import de.rub.nds.tlsattacker.core.protocol.message.HelloVerifyRequestMessage;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.action.MessageAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceivingAction;


public class WorkflowConfigurationFactoryTest {

    private Config config;
    private WorkflowConfigurationFactory workflowConfigurationFactory;

    public WorkflowConfigurationFactoryTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        config = Config.createConfig();
        workflowConfigurationFactory = new WorkflowConfigurationFactory(config);
    }

    @After
    public void tearDown() {
    }

    /**
     * Checks if the left and right WorkflowTrace contain the same amount and
     * combination of MessageActions and their respective Messages. The Messages
     * are matched by their Class.
     */
    private static boolean workflowTracesEqual(WorkflowTrace left, WorkflowTrace right) {
        if (left.getMessageActions().size() != right.getMessageActions().size()
                || left.getReceivingActions().size() != right.getReceivingActions().size()
                || left.getSendingActions().size() != right.getSendingActions().size()) {
            return false;
        }
        for (int i = 0; i < left.getMessageActions().size(); i++) {
            final MessageAction leftMessageAction = left.getMessageActions().get(i);
            final MessageAction rightMessageAction = right.getMessageActions().get(i);
            if (left.getMessageActions().size() != right.getMessageActions().size()
                    || !left.getMessageActions().get(i).getClass().equals(right.getMessageActions().get(i).getClass())) {
                return false;
            }
            for (int j = 0; j < leftMessageAction.getMessages().size(); j++) {
                if (!leftMessageAction.getMessages().get(i).getClass()
                        .equals(rightMessageAction.getMessages().get(i).getClass())) {
                    return false;
                }
            }
            if (leftMessageAction instanceof ReceivingAction) {
                if (!(rightMessageAction instanceof ReceivingAction)) {
                    return false;
                }
                final ReceiveAction leftReceiveAction = (ReceiveAction) leftMessageAction;
                final ReceiveAction rightReceiveAction = (ReceiveAction) rightMessageAction;
                if (leftReceiveAction.getExpectedMessages().size() != rightReceiveAction.getExpectedMessages().size()) {
                    return false;
                }
                for (int j = 0; j < leftReceiveAction.getMessages().size(); j++) {
                    if (!leftReceiveAction.getExpectedMessages().get(i).getClass()
                            .equals(rightReceiveAction.getExpectedMessages().get(i).getClass())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Test of createWorkflowTrace method, of class
     * WorkflowConfigurationFactory.
     */
    @Test
    public void testCreateWorkflowTrace() {

        final WorkflowTrace hello0 = workflowConfigurationFactory.createWorkflowTrace(WorkflowTraceType.HELLO);
        final WorkflowTrace hello1 = workflowConfigurationFactory.createWorkflowTrace(WorkflowTraceType.HELLO);

        Assert.assertTrue(workflowTracesEqual(hello0, hello1));

        final List<WorkflowTrace> list = new ArrayList<>(WorkflowTraceType.values().length);

        for (WorkflowTraceType workflowTraceType : WorkflowTraceType.values()) {
            if (workflowTraceType == WorkflowTraceType.SIMPLE_MITM_PROXY) {
                continue;
            }
            WorkflowTrace newTrace = workflowConfigurationFactory.createWorkflowTrace(workflowTraceType);
            Assert.assertNotNull(newTrace.getMessageActions());
            Assert.assertFalse(newTrace.getMessageActions().isEmpty());
            for (MessageAction action : newTrace.getMessageActions()) {
                if (action instanceof ReceiveAction) {
                    Assert.assertNotNull(((ReceiveAction) action).getExpectedMessages());
                    Assert.assertFalse(((ReceiveAction) action).getExpectedMessages().isEmpty());
                } else {
                    Assert.assertNotNull(action.getMessages());
                    Assert.assertFalse(action.getMessages().isEmpty());
                }
            }
            for (WorkflowTrace trace : list) {
                if (workflowTracesEqual(trace, newTrace)) {
                    Assert.fail(MessageFormat.format(
                            "The WorkflowConfigurationFactory is expected to produce different WorkflowTraces "
                                    + "for each WorkflowTraceType but there is a duplicate pair: {0} {1}", trace,
                            newTrace));
                }
            }
            list.add(newTrace);
        }
    }

    /**
     * Test of createHelloWorkflow method, of class
     * WorkflowConfigurationFactory.
     */
    @Test
    public void testCreateHelloWorkflow() {
        WorkflowTrace helloWorkflow;
        MessageAction firstAction;
        MessageAction messageAction1;
        MessageAction messageAction2;
        ReceiveAction lastAction;
        ClientHelloMessage clientHelloMessage;

        // Invariants Test: We will always obtain a WorkflowTrace containing at
        // least two TLS-Actions with exactly one message for the first
        // TLS-Action and at least one message for the last TLS-Action, which
        // would be the basic Client/Server-Hello:
        helloWorkflow = workflowConfigurationFactory.createHelloWorkflow();

        Assert.assertThat(helloWorkflow.getMessageActions().size(), Matchers.greaterThanOrEqualTo(2));

        firstAction = helloWorkflow.getMessageActions().get(0);

        Assert.assertEquals(ReceiveAction.class, helloWorkflow.getLastMessageAction().getClass());

        lastAction = (ReceiveAction) helloWorkflow.getLastMessageAction();

        Assert.assertEquals(1, firstAction.getMessages().size());
        Assert.assertThat(lastAction.getExpectedMessages().size(), Matchers.greaterThanOrEqualTo(1));

        Assert.assertEquals(firstAction.getMessages().get(0).getClass(),
                de.rub.nds.tlsattacker.core.protocol.message.ClientHelloMessage.class);
        Assert.assertEquals(lastAction.getExpectedMessages().get(0).getClass(),
                de.rub.nds.tlsattacker.core.protocol.message.ServerHelloMessage.class);

        // Variants Test: if (highestProtocolVersion == DTLS10)
        config.setHighestProtocolVersion(ProtocolVersion.DTLS10);
        config.setClientAuthentication(false);
        workflowConfigurationFactory = new WorkflowConfigurationFactory(config);
        helloWorkflow = workflowConfigurationFactory.createHelloWorkflow();

        firstAction = helloWorkflow.getMessageActions().get(0);
        clientHelloMessage = (ClientHelloMessage) firstAction.getMessages().get(0);
        Assert.assertFalse(clientHelloMessage.getIncludeInDigest());

        Assert.assertThat(helloWorkflow.getMessageActions().size(), Matchers.greaterThanOrEqualTo(4));
        Assert.assertNotNull(helloWorkflow.getMessageActions().get(1));
        Assert.assertNotNull(helloWorkflow.getMessageActions().get(2));
        messageAction1 = helloWorkflow.getMessageActions().get(1);
        messageAction2 = helloWorkflow.getMessageActions().get(2);

        Assert.assertEquals(ReceiveAction.class, messageAction1.getClass());
        Assert.assertEquals(HelloVerifyRequestMessage.class, ((ReceiveAction) messageAction1).getExpectedMessages()
                .get(0).getClass());
        Assert.assertEquals(ClientHelloMessage.class, messageAction2.getMessages().get(0).getClass());

        // if (highestProtocolVersion != TLS13)
        lastAction = (ReceiveAction) helloWorkflow.getLastMessageAction();
        Assert.assertEquals(lastAction.getExpectedMessages().get(1).getClass(),
                de.rub.nds.tlsattacker.core.protocol.message.CertificateMessage.class);

        // if config.getDefaultSelectedCipherSuite().isEphemeral()
        config.setHighestProtocolVersion(ProtocolVersion.DTLS10);
        config.setClientAuthentication(true);
        config.setDefaultSelectedCipherSuite(CipherSuite.TLS_DHE_DSS_WITH_AES_256_GCM_SHA384);
        workflowConfigurationFactory = new WorkflowConfigurationFactory(config);
        helloWorkflow = workflowConfigurationFactory.createHelloWorkflow();

        lastAction = (ReceiveAction) helloWorkflow.getLastMessageAction();
        Assert.assertNotNull(lastAction.getExpectedMessages().get(2));
        Assert.assertEquals(lastAction.getExpectedMessages().get(3).getClass(),
                de.rub.nds.tlsattacker.core.protocol.message.CertificateRequestMessage.class);
    }

    /**
     * Test of createHandshakeWorkflow method, of class
     * WorkflowConfigurationFactory.
     */
    @Test()
    public void testCreateHandshakeWorkflow() {
        WorkflowTrace handshakeWorkflow;
        MessageAction lastAction;
        MessageAction messageAction4;
        ReceiveAction receiveAction;

        config.setHighestProtocolVersion(ProtocolVersion.TLS13);
        config.setClientAuthentication(false);
        workflowConfigurationFactory = new WorkflowConfigurationFactory(config);
        handshakeWorkflow = workflowConfigurationFactory.createHandshakeWorkflow();

        // Invariants
        Assert.assertThat(handshakeWorkflow.getMessageActions().size(), Matchers.greaterThanOrEqualTo(3));
        Assert.assertNotNull(handshakeWorkflow.getLastMessageAction());

        lastAction = handshakeWorkflow.getLastMessageAction();

        Assert.assertEquals(FinishedMessage.class, lastAction.getMessages().get(lastAction.getMessages().size() - 1)
                .getClass());

        // Variants
        // if(config.isClientAuthentication())
        config.setClientAuthentication(true);
        workflowConfigurationFactory = new WorkflowConfigurationFactory(config);
        handshakeWorkflow = workflowConfigurationFactory.createHandshakeWorkflow();

        lastAction = handshakeWorkflow.getLastMessageAction();

        Assert.assertEquals(CertificateMessage.class, lastAction.getMessages().get(0).getClass());
        Assert.assertEquals(CertificateVerifyMessage.class, lastAction.getMessages().get(1).getClass());
        Assert.assertEquals(FinishedMessage.class, lastAction.getMessages().get(2).getClass());

        // ! TLS13 config.setHighestProtocolVersion(ProtocolVersion.TLS13);
        config.setHighestProtocolVersion(ProtocolVersion.DTLS10);
        config.setClientAuthentication(true);
        workflowConfigurationFactory = new WorkflowConfigurationFactory(config);
        handshakeWorkflow = workflowConfigurationFactory.createHandshakeWorkflow();

        Assert.assertThat(handshakeWorkflow.getMessageActions().size(), Matchers.greaterThanOrEqualTo(6));

        messageAction4 = handshakeWorkflow.getMessageActions().get(4);

        Assert.assertEquals(CertificateMessage.class, messageAction4.getMessages().get(0).getClass());
        Assert.assertEquals(CertificateVerifyMessage.class,
                messageAction4.getMessages().get(messageAction4.getMessages().size() - 3).getClass());
        Assert.assertEquals(ChangeCipherSpecMessage.class,
                messageAction4.getMessages().get(messageAction4.getMessages().size() - 2).getClass());
        Assert.assertEquals(FinishedMessage.class,
                messageAction4.getMessages().get(messageAction4.getMessages().size() - 1).getClass());

        receiveAction = (ReceiveAction) handshakeWorkflow.getLastMessageAction();

        Assert.assertEquals(ChangeCipherSpecMessage.class, receiveAction.getExpectedMessages().get(0).getClass());
        Assert.assertEquals(FinishedMessage.class, receiveAction.getExpectedMessages().get(1).getClass());
    }

    /**
     * Test of createFullWorkflow method, of class WorkflowConfigurationFactory.
     */
    @Test
    public void testCreateFullWorkflow() {
        MessageAction messageAction3;
        MessageAction messageAction4;
        MessageAction messageAction5;

        config.setHighestProtocolVersion(ProtocolVersion.TLS13);
        config.setClientAuthentication(true);
        config.setServerSendsApplicationData(false);
        config.setAddHeartbeatExtension(false);
        workflowConfigurationFactory = new WorkflowConfigurationFactory(config);
        WorkflowTrace fullWorkflow = workflowConfigurationFactory.createFullWorkflow();

        // Invariants
        Assert.assertThat(fullWorkflow.getMessageActions().size(), Matchers.greaterThanOrEqualTo(4));

        messageAction3 = fullWorkflow.getMessageActions().get(3);

        Assert.assertEquals(ApplicationMessage.class, messageAction3.getMessages().get(0).getClass());

        // Invariants
        config.setServerSendsApplicationData(true);
        config.setAddHeartbeatExtension(true);
        workflowConfigurationFactory = new WorkflowConfigurationFactory(config);
        fullWorkflow = workflowConfigurationFactory.createFullWorkflow();

        Assert.assertThat(fullWorkflow.getMessageActions().size(), Matchers.greaterThanOrEqualTo(6));

        messageAction3 = fullWorkflow.getMessageActions().get(3);
        messageAction4 = fullWorkflow.getMessageActions().get(4);
        messageAction5 = fullWorkflow.getMessageActions().get(5);

        Assert.assertEquals(ReceiveAction.class, messageAction3.getClass());
        Assert.assertEquals(ApplicationMessage.class, ((ReceiveAction) messageAction3).getExpectedMessages().get(0)
                .getClass());
        Assert.assertEquals(ApplicationMessage.class, messageAction4.getMessages().get(0).getClass());
        Assert.assertEquals(HeartbeatMessage.class, messageAction4.getMessages().get(1).getClass());
        Assert.assertEquals(ReceiveAction.class, messageAction5.getClass());
        Assert.assertEquals(HeartbeatMessage.class, ((ReceiveAction) messageAction5).getExpectedMessages().get(0)
                .getClass());
    }

}
