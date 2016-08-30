package Mutator;

import Certificate.ClientCertificateStructure;
import Mutator.Certificate.CertificateMutator;
import Certificate.ServerCertificateStructure;
import Mutator.Mutator;
import Config.EvolutionaryFuzzerConfig;
import Config.Mutator.SimpleMutatorConfig;
import Helper.FuzzingHelper;
import static Helper.FuzzingHelper.executeModifiableVariableModification;
import static Helper.FuzzingHelper.getAllModifiableVariableFieldsRecursively;
import Modification.ChangeClientCertificateModification;
import Modification.ChangeServerCertificateModification;
import Modification.Modification;
import de.rub.nds.tlsattacker.modifiablevariable.util.ModifiableVariableField;
import de.rub.nds.tlsattacker.tls.constants.ConnectionEnd;
import de.rub.nds.tlsattacker.tls.protocol.ModifiableVariableHolder;
import de.rub.nds.tlsattacker.tls.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.util.UnoptimizedDeepCopy;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import Result.ResultContainer;
import TestVector.TestVector;
import java.io.File;
import javax.xml.bind.JAXB;

/**
 * 
 * @author Robert Merget - robert.merget@rub.de
 */
public class SimpleMutator extends Mutator {

    private static final Logger LOG = Logger.getLogger(SimpleMutator.class.getName());

    private SimpleMutatorConfig simpleConfig;

    /**
     * 
     * @param config
     */
    public SimpleMutator(EvolutionaryFuzzerConfig evoConfig, CertificateMutator certMutator) {
	super(evoConfig, certMutator);
	File f = new File(evoConfig.getMutatorConfigFolder() + "simple.conf");
	if (f.exists()) {
	    simpleConfig = JAXB.unmarshal(f, SimpleMutatorConfig.class);
	} else {
	    simpleConfig = new SimpleMutatorConfig();
	    JAXB.marshal(simpleConfig, f);
	}
    }

    /**
     * 
     * @return
     */
    @Override
    public TestVector getNewMutation() {
	Random r = new Random();
	// chose a random trace from the list
	TestVector tempVector;
	WorkflowTrace trace = null;
	ServerCertificateStructure serverKeyCertPair;
	ClientCertificateStructure clientKeyCertPair;
	TestVector newTestVector;
	boolean modified = false;
	do {
	    if (ResultContainer.getInstance().getGoodVectors().isEmpty()) {
		tempVector = new TestVector(new WorkflowTrace(), certMutator.getServerCertificateStructure(),
			certMutator.getClientCertificateStructure(), config.getActionExecutorConfig()
				.getRandomExecutorType(), null);
		ResultContainer.getInstance().getGoodVectors().add(tempVector);
		modified = true;
	    } else {
		// Choose a random Trace to modify
		tempVector = ResultContainer.getInstance().getGoodVectors()
			.get(r.nextInt(ResultContainer.getInstance().getGoodVectors().size()));
	    }
	    serverKeyCertPair = tempVector.getServerKeyCert();
	    clientKeyCertPair = tempVector.getClientKeyCert();
	    trace = (WorkflowTrace) UnoptimizedDeepCopy.copy(tempVector.getTrace());
	    Modification modification = null;
	    if (r.nextInt(100) <= simpleConfig.getChangeServerCert()) {
		serverKeyCertPair = certMutator.getServerCertificateStructure();
		modification = new ChangeServerCertificateModification(serverKeyCertPair);
		modified = true;
	    }
	    if (r.nextInt(100) <= simpleConfig.getChangeClientCert()) {
		clientKeyCertPair = certMutator.getClientCertificateStructure();
		modification = new ChangeClientCertificateModification(clientKeyCertPair);
		modified = true;
	    }
	    newTestVector = new TestVector(trace, serverKeyCertPair, clientKeyCertPair, tempVector.getExecutorType(),
		    tempVector);
	    if (modification != null) {
		newTestVector.addModification(modification);
	    }
	    // perhaps add a flight
	    if (trace.getTLSActions().isEmpty() || r.nextInt(100) < simpleConfig.getAddFlightPercentage()) {
		newTestVector.addModification(FuzzingHelper.addMessageFlight(trace));
		modified = true;
	    }
	    if (r.nextInt(100) < simpleConfig.getAddMessagePercentage()) {
		newTestVector.addModification(FuzzingHelper.addRandomMessage(trace));
		modified = true;
	    }
	    // perhaps remove a message
	    if (r.nextInt(100) <= simpleConfig.getRemoveMessagePercentage()) {
		newTestVector.addModification(FuzzingHelper.removeRandomMessage(trace));
		modified = true;
	    }
            // perhaps toggle Encryption
            if (r.nextInt(100) <= simpleConfig.getAddToggleEncrytionPercentage()) {
		newTestVector.addModification(FuzzingHelper.addToggleEncrytionActionModification(trace));
		modified = true;
	    }
	    // perhaps add records
	    if (r.nextInt(100) <= simpleConfig.getAddRecordPercentage()) {
		newTestVector.addModification(FuzzingHelper.addRecordAtRandom(trace));
		modified = true;
	    }
	    // Modify a random field:
	    if (r.nextInt(100) <= simpleConfig.getModifyVariablePercentage()) {
		List<ModifiableVariableField> variableList = getAllModifiableVariableFieldsRecursively(trace);
		// LOG.log(Level.INFO, ""+trace.getProtocolMessages().size());
		if (variableList.size() > 0) {
		    ModifiableVariableField field = variableList.get(r.nextInt(variableList.size()));
		    // String currentFieldName = field.getField().getName();
		    // String currentMessageName =
		    // field.getObject().getClass().getSimpleName();
		    // LOG.log(Level.INFO, "Fieldname:{0} Message:{1}", new
		    // Object[]{currentFieldName, currentMessageName});
		    newTestVector.addModification(executeModifiableVariableModification(
			    (ModifiableVariableHolder) field.getObject(), field.getField()));
		    modified = true;
		}
	    }
	    if (r.nextInt(100) <= simpleConfig.getDuplicateMessagePercentage()) {
		newTestVector.addModification(FuzzingHelper.duplicateRandomProtocolMessage(trace));
		modified = true;
	    }
	} while (!modified || r.nextInt(100) <= simpleConfig.getMultipleModifications());
	return newTestVector;

    }

}
