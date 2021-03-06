package de.fraunhofer.isst.dataspaceconnector.services.usagecontrol;

import de.fraunhofer.iais.eis.Action;
import de.fraunhofer.iais.eis.Contract;
import de.fraunhofer.iais.eis.Duty;
import de.fraunhofer.iais.eis.Permission;
import de.fraunhofer.isst.dataspaceconnector.model.RequestedResource;
import de.fraunhofer.isst.dataspaceconnector.services.resource.RequestedResourceRepository;
import de.fraunhofer.isst.dataspaceconnector.services.resource.RequestedResourceService;
import de.fraunhofer.isst.ids.framework.spring.starter.SerializerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.xml.datatype.DatatypeConfigurationException;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

/**
 * This class implements automated policy check and usage control enforcement.
 *
 * @author Julia Pampus
 * @version $Id: $Id
 */
@Component
@EnableScheduling
public class PolicyEnforcement {
    /** Constant <code>LOGGER</code> */
    public static final Logger LOGGER = LoggerFactory.getLogger(PolicyEnforcement.class);

    private PolicyVerifier policyVerifier;

    private RequestedResourceService requestedResourceService;
    private RequestedResourceRepository requestedResourceRepository;

    private SerializerProvider serializerProvider;

    @Autowired
    /**
     * <p>Constructor for PolicyEnforcement.</p>
     *
     * @param policyVerifier a {@link de.fraunhofer.isst.dataspaceconnector.services.usagecontrol.PolicyVerifier} object.
     * @param requestedResourceService a {@link de.fraunhofer.isst.dataspaceconnector.services.resource.RequestedResourceService} object.
     * @param requestedResourceRepository a {@link de.fraunhofer.isst.dataspaceconnector.services.resource.RequestedResourceRepository} object.
     * @param serializerProvider a {@link de.fraunhofer.isst.ids.framework.spring.starter.SerializerProvider} object.
     */
    public PolicyEnforcement(PolicyVerifier policyVerifier, RequestedResourceService requestedResourceService,
                             RequestedResourceRepository requestedResourceRepository, SerializerProvider serializerProvider) {
        this.policyVerifier = policyVerifier;
        this.requestedResourceService = requestedResourceService;
        this.requestedResourceRepository = requestedResourceRepository;
        this.serializerProvider = serializerProvider;
    }

    /**
     * Checks all resources every minute.
     * 1000 = 1 sec * 60 * 60 = every hour (3600000)
     */
    @Scheduled(fixedDelay = 60000)
    public void schedule() {
        LOGGER.info("Check data...");
        try {
            checkResources();
        } catch (ParseException | IOException e) {
            LOGGER.error(e.toString());
        }
    }

    /**
     * Checks all know resources and its policies to delete them if necessary.
     *
     * @throws java.text.ParseException if any.
     * @throws java.io.IOException if any.
     */
    public void checkResources() throws ParseException, IOException {
        for (RequestedResource resource : requestedResourceRepository.findAll()) {
            String policy = resource.getResourceMetadata().getPolicy();
            try {
                Contract contract = serializerProvider.getSerializer().deserialize(policy, Contract.class);
                if (contract.getPermission() != null && contract.getPermission().get(0) != null) {
                    Permission permission = contract.getPermission().get(0);
                    ArrayList<? extends Duty> postDuties = permission.getPostDuty();

                    if (postDuties != null && postDuties.get(0) != null) {
                        Action action = postDuties.get(0).getAction().get(0);
                        if (action == Action.DELETE) {
                            if (policyVerifier.checkForDelete(postDuties.get(0))) {
                                requestedResourceService.deleteResource(resource.getUuid());
                            }
                        }
                    }
                }

            } catch (IOException e) {
                throw new IOException("The policy could not be read. Please check the policy syntax.");
            }
        }
    }
}
