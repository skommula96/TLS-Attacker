/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.attacks.padding;

import de.rub.nds.tlsattacker.attacks.constants.PaddingRecordGeneratorType;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.AlertDescription;
import de.rub.nds.tlsattacker.core.constants.AlertLevel;
import de.rub.nds.tlsattacker.core.constants.RunningModeType;
import de.rub.nds.tlsattacker.core.protocol.message.AlertMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ApplicationMessage;
import de.rub.nds.tlsattacker.core.record.AbstractRecord;
import de.rub.nds.tlsattacker.core.record.Record;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.SendAction;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowTraceType;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author robert
 */
public class ClassicCloseNotifyVectorGenerator extends PaddingVectorGenerator {

    public ClassicCloseNotifyVectorGenerator(PaddingRecordGeneratorType recordGeneratorType) {
        super(recordGeneratorType);
    }

    @Override
    public List<WorkflowTrace> getPaddingOracleVectors(Config config) {
        List<WorkflowTrace> traceList = new LinkedList<>();
        for (Record record : recordGenerator.getRecords(config.getDefaultSelectedCipherSuite(),
                config.getDefaultSelectedProtocolVersion())) {
            WorkflowTrace trace = new WorkflowConfigurationFactory(config).createWorkflowTrace(
                    WorkflowTraceType.HANDSHAKE, RunningModeType.CLIENT);
            ApplicationMessage applicationMessage = new ApplicationMessage(config);
            AlertMessage alert = new AlertMessage();
            alert.setConfig(AlertLevel.FATAL, AlertDescription.CLOSE_NOTIFY);
            SendAction sendAction = new SendAction(applicationMessage, alert);
            sendAction.setRecords(new LinkedList<AbstractRecord>());
            sendAction.getRecords().add(record);
            sendAction.getRecords().add(new Record(config));

            trace.addTlsAction(sendAction);
            AlertMessage alertMessage = new AlertMessage(config);
            trace.addTlsAction(new ReceiveAction(alertMessage));
            traceList.add(trace);
        }
        return traceList;
    }
}