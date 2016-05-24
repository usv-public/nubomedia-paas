/*
 * Copyright (c) 2015-2016 Fraunhofer FOKUS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.project.openbaton.nubomedia.paas.api;

import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.common.LifecycleEvent;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.sdk.api.exception.SDKException;
import org.project.openbaton.nubomedia.paas.utils.PaaSProperties;
import org.project.openbaton.nubomedia.paas.core.OpenbatonManager;
import org.project.openbaton.nubomedia.paas.core.OpenshiftManager;
import org.project.openbaton.nubomedia.paas.exceptions.ApplicationNotFoundException;
import org.project.openbaton.nubomedia.paas.messages.*;
import org.project.openbaton.nubomedia.paas.core.openbaton.MediaServerGroup;
import org.project.openbaton.nubomedia.paas.model.openbaton.OpenbatonEvent;
import org.project.openbaton.nubomedia.paas.exceptions.openbaton.StunServerException;
import org.project.openbaton.nubomedia.paas.exceptions.openbaton.turnServerException;
import org.project.openbaton.nubomedia.paas.exceptions.openshift.DuplicatedException;
import org.project.openbaton.nubomedia.paas.exceptions.openshift.NameStructureException;
import org.project.openbaton.nubomedia.paas.exceptions.openshift.UnauthorizedException;
import org.project.openbaton.nubomedia.paas.model.persistence.Application;
import org.project.openbaton.nubomedia.paas.model.persistence.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;


/**
 * Created by maa on 28.09.15.
 */

@RestController
@RequestMapping("/api/v1/nubomedia/paas")
public class PaaSAPI {

    private static Map<String, MediaServerGroup> deploymentMap = new HashMap<>();
    private Logger logger;
    private SecureRandom appIDGenerator;

    @Autowired private OpenshiftManager osmanager;
    @Autowired private OpenbatonManager obmanager;
    @Autowired private ApplicationRepository appRepo;
    @Autowired private PaaSProperties paaSProperties;

    @PostConstruct
    private void init() {
        System.setProperty("javax.net.ssl.trustStore", paaSProperties.getKeystore());
        this.logger = LoggerFactory.getLogger(this.getClass());
        this.appIDGenerator = new SecureRandom();
    }

    @RequestMapping(value = "/app",  method = RequestMethod.POST)
    public @ResponseBody NubomediaCreateAppResponse createApp(@RequestHeader("Auth-Token") String token, @RequestBody NubomediaCreateAppRequest request) throws SDKException, UnauthorizedException, DuplicatedException, NameStructureException, turnServerException, StunServerException {

        if(token == null){
            throw new UnauthorizedException("No auth-token header");
        }

        if(request.getAppName().length() > 18){

            throw new NameStructureException("Name is too long");

        }

        if(request.getAppName().contains(".")){

            throw new NameStructureException("Name can't contains dots");

        }

        if(request.getAppName().contains("_")){
            throw new NameStructureException("Name can't contain underscore");
        }

        if(!request.getAppName().matches("[a-z0-9]+(?:[._-][a-z0-9]+)*")){
            throw new NameStructureException("Name must match [a-z0-9]+(?:[._-][a-z0-9]+)*");
        }

        if (!appRepo.findByAppName(request.getAppName()).isEmpty()) {
            throw new DuplicatedException("Application with " + request.getAppName() + " already exist");
        }

        logger.debug("REQUEST" + request.toString());

        List<String> protocols = new ArrayList<>();
        List<Integer> targetPorts = new ArrayList<>();
        List<Integer> ports = new ArrayList<>();

        for (int i = 0; i < request.getPorts().length; i++){

            protocols.add(request.getPorts()[i].getProtocol());
            targetPorts.add(request.getPorts()[i].getTargetPort());
            ports.add(request.getPorts()[i].getPort());

        }

        NubomediaCreateAppResponse res = new NubomediaCreateAppResponse();
        String appID = new BigInteger(130,appIDGenerator).toString(64);
        logger.debug("App ID " + appID + "\n");

        logger.debug("request params " + request.getAppName() + " " + request.getGitURL() + " " + request.getProjectName() + " " + ports + " " + protocols + " " + request.getReplicasNumber());

        //Openbaton MediaServer Request
        logger.info("[PAAS]: EVENT_APP_CREATE " + new Date().getTime());
        MediaServerGroup mediaServerGroup = obmanager.getMediaServerGroupID(request.getFlavor(),appID,paaSProperties.getInternalURL(),request.isCloudRepository(), request.isCdnConnector(), request.getQualityOfService(),request.isTurnServerActivate(),request.getTurnServerUrl(),request.getTurnServerUsername(),request.getTurnServerPassword(),request.isStunServerActivate(), request.getStunServerIp(), request.getStunServerPort(), request.getScaleInOut(),request.getScale_out_threshold());
        mediaServerGroup.setToken(token);

        deploymentMap.put(appID, mediaServerGroup);

        Application persistApp = new Application(appID,request.getFlavor(),request.getAppName(),request.getProjectName(),"", mediaServerGroup.getMediaServerGroupID(), request.getGitURL(), targetPorts, ports, protocols,null, request.getReplicasNumber(),request.getSecretName(),false);
        appRepo.save(persistApp);

        res.setApp(persistApp);
        res.setCode(200);
        return res;
    }

    @RequestMapping(value = "/app/{id}", method =  RequestMethod.GET)
    public @ResponseBody Application getApp(@RequestHeader("Auth-token") String token, @PathVariable("id") String id) throws ApplicationNotFoundException, UnauthorizedException {

        logger.info("Request status for " + id + " app");

        if(token == null){
            throw new UnauthorizedException("no auth-token header");
        }

        if(!appRepo.exists(id)){
            throw new ApplicationNotFoundException("Application with ID not found");
        }

        Application app = appRepo.findFirstByAppID(id);
        logger.debug("Retrieving status for " + app.toString() + "\nwith status " + app.getStatus());

        switch (app.getStatus()){
            case CREATED:
                app.setStatus(obmanager.getStatus(app.getNsrID()));
                break;
            case INITIALIZING:
                app.setStatus(obmanager.getStatus(app.getNsrID()));
                break;
            case INITIALISED:
                try {
                    app.setStatus(osmanager.getStatus(token, app.getAppName(), app.getProjectName()));
                }catch (ResourceAccessException e){
                    app.setStatus(BuildingStatus.PAAS_RESOURCE_MISSING);
                }
                break;
            case BUILDING:
                try{
                    app.setStatus(osmanager.getStatus(token, app.getAppName(),app.getProjectName()));
                }catch (ResourceAccessException e){
                    app.setStatus(BuildingStatus.PAAS_RESOURCE_MISSING);
                }
                break;
            case DEPLOYNG:
                try{
                    app.setStatus(osmanager.getStatus(token, app.getAppName(),app.getProjectName()));
                }catch (ResourceAccessException e){
                    app.setStatus(BuildingStatus.PAAS_RESOURCE_MISSING);
                }
                break;
            case FAILED:
                logger.debug("FAILED: app has resource ok? " + app.isResourceOK());
                if (!app.isResourceOK()){
                    app.setStatus(BuildingStatus.FAILED);
                    break;
                }
                else {
                    try {
                        app.setStatus(osmanager.getStatus(token, app.getAppName(), app.getProjectName()));
                    } catch (ResourceAccessException e) {
                        app.setStatus(BuildingStatus.PAAS_RESOURCE_MISSING);
                    }
                }
                break;
            case RUNNING:
                try{
                    app.setStatus(osmanager.getStatus(token, app.getAppName(),app.getProjectName()));
                    app.setPodList(osmanager.getPodList(token,app.getAppName(),app.getProjectName()));
                }catch (ResourceAccessException e){
                    app.setStatus(BuildingStatus.PAAS_RESOURCE_MISSING);
                }
                break;
            case PAAS_RESOURCE_MISSING:
                app.setStatus(osmanager.getStatus(token,app.getAppName(),app.getProjectName()));
                break;
        }

        appRepo.save(app);

        return app;

    }

    @RequestMapping(value = "/app/{id}/buildlogs", method = RequestMethod.GET)
    public @ResponseBody NubomediaBuildLogs getBuildLogs(@RequestHeader("Auth-token") String token, @PathVariable("id") String id) throws UnauthorizedException {

        if(token == null){
            throw new UnauthorizedException("no auth-token header");
        }

        NubomediaBuildLogs res = new NubomediaBuildLogs();

        if(!appRepo.exists(id)){
            return null;
        }

        Application app = appRepo.findFirstByAppID(id);

        if(app.getStatus().equals(BuildingStatus.FAILED) && !app.isResourceOK()){

            res.setId(id);
            res.setAppName(app.getAppName());
            res.setProjectName(app.getProjectName());
            res.setLog("Something wrong on retrieving resources");

        } else if(app.getStatus().equals(BuildingStatus.CREATED) || app.getStatus().equals(BuildingStatus.INITIALIZING)){
            res.setId(id);
            res.setAppName(app.getAppName());
            res.setProjectName(app.getProjectName());
            res.setLog("The application is retrieving resources " + app.getStatus());

            return res;
        } else if (app.getStatus().equals(BuildingStatus.PAAS_RESOURCE_MISSING)){
            res.setId(id);
            res.setAppName(app.getAppName());
            res.setProjectName(app.getProjectName());
            res.setLog("PaaS components are missing, send an email to the administrator to check the PaaS status");

            return res;
        } else {

            res.setId(id);
            res.setAppName(app.getAppName());
            res.setProjectName(app.getProjectName());
            try {
                res.setLog(osmanager.getBuildLogs(token, app.getAppName(), app.getProjectName()));
            } catch (ResourceAccessException e) {
                app.setStatus(BuildingStatus.PAAS_RESOURCE_MISSING);
                appRepo.save(app);
                res.setLog("Openshift is not responding, app " + app.getAppName() + " is not anymore available");
            }
        }

        return res;
    }

    @RequestMapping(value = "/app/{id}/logs/{podName}", method = RequestMethod.GET)
    public @ResponseBody String getApplicationLogs(@RequestHeader("Auth-token") String token, @PathVariable("id") String id,@PathVariable("podName") String podName) throws UnauthorizedException, ApplicationNotFoundException {

        if(token == null){
            throw new UnauthorizedException("no auth-token header");
        }

        if(!appRepo.exists(id)){
            throw new ApplicationNotFoundException("Application with ID not found");
        }

        Application app = appRepo.findFirstByAppID(id);

        if(!app.getStatus().equals(BuildingStatus.RUNNING)){
            return "Application Status " + app.getStatus() + ", logs are not available until the status is RUNNING";
        }

        return osmanager.getApplicationLog(token,app.getAppName(),app.getProjectName(),podName);

    }

    @RequestMapping(value = "/app", method = RequestMethod.GET)
    public @ResponseBody Iterable<Application> getApps(@RequestHeader("Auth-token") String token) throws UnauthorizedException, ApplicationNotFoundException {

        if(token == null){
            throw new UnauthorizedException("no auth-token header");
        }
        //BETA
        Iterable<Application> applications = this.appRepo.findAll();

        for (Application app : applications){
            app.setStatus(this.getStatus(token,app));
        }

        this.appRepo.save(applications);

        return applications;
    }

    @RequestMapping(value = "/app/{id}", method = RequestMethod.DELETE)
    public @ResponseBody NubomediaDeleteAppResponse deleteApp(@RequestHeader("Auth-token") String token, @PathVariable("id") String id) throws UnauthorizedException {

        if(token == null){
            throw new UnauthorizedException("no auth-token header");
        }

        logger.debug("id " + id);

        if(!appRepo.exists(id)){
            return new NubomediaDeleteAppResponse(id,"Application not found","null",404);
        }

        Application app = appRepo.findFirstByAppID(id);
        app.setStatus(this.getStatus(token,app));
        logger.debug("Deleting " + app.toString());

        if (!app.isResourceOK()){

            String name = app.getAppName();
            String projectName = app.getProjectName();

            if(app.getStatus().equals(BuildingStatus.CREATED) || app.getStatus().equals(BuildingStatus.INITIALIZING) || app.getStatus().equals(BuildingStatus.FAILED)){
                MediaServerGroup server = deploymentMap.get(id);
                obmanager.deleteDescriptor(server.getNsdID());
                obmanager.deleteEvent(server.getEventAllocatedID());
                obmanager.deleteEvent(server.getEventErrorID());

                if (!app.getStatus().equals(BuildingStatus.FAILED) && obmanager.existRecord(server.getMediaServerGroupID())) {
                    obmanager.deleteRecord(app.getNsrID());
                }
                deploymentMap.remove(app.getAppID());

            }

            appRepo.delete(app);
            return new NubomediaDeleteAppResponse(id,name,projectName,200);

        }

        if (app.getStatus().equals(BuildingStatus.PAAS_RESOURCE_MISSING)){
            obmanager.deleteRecord(app.getNsrID());
            appRepo.delete(app);

            return new NubomediaDeleteAppResponse(id,app.getAppName(),app.getProjectName(),200);
        }

//        if (app.getStatus().equals(BuildingStatus.CREATED) || app.getStatus().equals(BuildingStatus.INITIALIZING)){
//
//            obmanager.deleteRecord(app.getNsrID());
//            return new NubomediaDeleteAppResponse(id,app.getAppName(),app.getProjectName(),200);
//
//        }


        obmanager.deleteRecord(app.getNsrID());
        HttpStatus resDelete = HttpStatus.BAD_REQUEST;
        try {
            resDelete = osmanager.deleteApplication(token, app.getAppName(), app.getProjectName());
        } catch (ResourceAccessException e){
            logger.info("PaaS Missing");
        }

        appRepo.delete(app);

        return new NubomediaDeleteAppResponse(id,app.getAppName(),app.getProjectName(),resDelete.value());
    }

    @RequestMapping(value = "/secret", method = RequestMethod.POST)
    public @ResponseBody String createSecret(@RequestHeader("Auth-token") String token, @RequestBody NubomediaCreateSecretRequest ncsr) throws UnauthorizedException {

        if(token == null){
            throw new UnauthorizedException("no auth-token header");
        }

        logger.debug("Creating new secret for " + ncsr.getProjectName() + " with key " + ncsr.getPrivateKey());
        return osmanager.createSecret(token, ncsr.getPrivateKey(), ncsr.getProjectName());
    }

    @RequestMapping(value = "/secret/{projectName}/{secretName}", method = RequestMethod.DELETE)
    public @ResponseBody NubomediaDeleteSecretResponse deleteSecret (@RequestHeader("Auth-token") String token, @PathVariable("secretName") String secretName, @PathVariable("projectName") String projectName) throws UnauthorizedException {

        if(token == null){
            throw new UnauthorizedException("no auth-token header");
        }

        HttpStatus deleteStatus = osmanager.deleteSecret(token, secretName, projectName);
        return new NubomediaDeleteSecretResponse(secretName,projectName,deleteStatus.value());
    }

    @RequestMapping(value = "/auth", method = RequestMethod.POST)
    public @ResponseBody NubomediaAuthorizationResponse authorize(@RequestBody NubomediaAuthorizationRequest request) throws UnauthorizedException {

        String token = osmanager.authenticate(request.getUsername(),request.getPassword());
        if (token.equals("Unauthorized")){
            return new NubomediaAuthorizationResponse(token,401);
        }
        else if (token.equals("PaaS Missing")){
            return new NubomediaAuthorizationResponse(token,404);
        }
        else{
            return new NubomediaAuthorizationResponse(token,200);
        }
    }

    @RequestMapping(value = "/server-ip/", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public String getMediaServerIp() {
        return paaSProperties.getVnfmIP();
    }

    @RequestMapping(value = "/openbaton/{id}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public void startOpenshiftBuild(@RequestBody OpenbatonEvent evt, @PathVariable("id") String id) throws UnauthorizedException{
        logger.debug("starting callback for appId" + id);
        logger.info("Received event " + evt);
        Application app = appRepo.findFirstByAppID(id);
        logger.debug(deploymentMap.toString());
        MediaServerGroup server = deploymentMap.get(id);

        if(evt.getAction().equals(Action.INSTANTIATE_FINISH) && server.getMediaServerGroupID().equals(evt.getPayload().getId())){
            logger.info("[PAAS]: EVENT_FINISH " + new Date().getTime());
            app.setStatus(BuildingStatus.INITIALISED);
            app.setResourceOK(true);
            appRepo.save(app);

            String vnfrID ="";
            String cloudRepositoryIp = null;
            String cloudRepositoryPort = null;
            String cdnServerIp = null;


            for(VirtualNetworkFunctionRecord record : evt.getPayload().getVnfr()){

                if(record.getEndpoint().equals("media-server"))
                    vnfrID = record.getId();

                if(record.getName().contains("mongodb")){
                    cloudRepositoryIp = this.getCloudRepoIP(record);
                    cloudRepositoryPort = "7676";

                    for (LifecycleEvent lce : record.getLifecycle_event()){
                        if(lce.getEvent().name().equals("START")){
                            for (String scriptName : lce.getLifecycle_events()){
                                if (scriptName.equals("start-cdn.sh")){
                                    cdnServerIp = cloudRepositoryIp;
                                    break;
                                }
                            }
                        }
                    }
                }

            }

            logger.debug("retrieved session for " + server.getToken());
            String route = null;
            try {
                int[] ports = new int[app.getPorts().size()];
                int[] targetPorts = new int[app.getTargetPorts().size()];

                for(int i = 0; i < ports.length; i++){
                    ports[i] = app.getPorts().get(i);
                    targetPorts[i] = app.getTargetPorts().get(i);
                }

                logger.info("[PAAS]: CREATE_APP_OS " + new Date().getTime());
                logger.debug("cloudRepositoryPort "+ cloudRepositoryPort + " IP " + cloudRepositoryIp);

                try {
                    route = osmanager.buildApplication(server.getToken(), app.getAppID(), app.getAppName(), app.getProjectName(), app.getGitURL(), ports, targetPorts, app.getProtocols().toArray(new String[0]), app.getReplicasNumber(), app.getSecretName(), vnfrID, paaSProperties.getVnfmIP(), paaSProperties.getVnfmPort(), cloudRepositoryIp, cloudRepositoryPort,cdnServerIp);

                } catch (ResourceAccessException e){
                    obmanager.deleteDescriptor(server.getNsdID());
                    obmanager.deleteEvent(server.getEventAllocatedID());
                    obmanager.deleteEvent(server.getEventErrorID());
                    app.setStatus(BuildingStatus.FAILED);
                    appRepo.save(app);
                    deploymentMap.remove(app.getAppID());
                }
                logger.info("[PAAS]: SCHEDULED_APP_OS " + new Date().getTime());
            } catch (DuplicatedException e) {
                app.setRoute(e.getMessage());
                app.setStatus(BuildingStatus.DUPLICATED);
                appRepo.save(app);
                return;
            }
            obmanager.deleteDescriptor(server.getNsdID());

            obmanager.deleteEvent(server.getEventAllocatedID());
            obmanager.deleteEvent(server.getEventErrorID());
            app.setRoute(route);
            appRepo.save(app);
            deploymentMap.remove(app.getAppID());
        }
        else if (evt.getAction().equals(Action.ERROR)){

            obmanager.deleteDescriptor(server.getNsdID());
            obmanager.deleteEvent(server.getEventErrorID());
            obmanager.deleteEvent(server.getEventAllocatedID());
            obmanager.deleteRecord(server.getMediaServerGroupID());
            app.setStatus(BuildingStatus.FAILED);
            appRepo.save(app);
            deploymentMap.remove(app.getAppID());
        }

    }

    private String getCloudRepoIP(VirtualNetworkFunctionRecord record) {

        for (VirtualDeploymentUnit vdu : record.getVdu()){
            for (VNFCInstance instance : vdu.getVnfc_instance()){
                for (Ip ip : instance.getFloatingIps()){
                    if (ip != null){
                        return ip.getIp();
                    }
                }
            }
        }

        return null;
    }

    private BuildingStatus getStatus(String token, Application app) throws UnauthorizedException {

        BuildingStatus res = null;

        switch (app.getStatus()){
            case CREATED:
                res =  obmanager.getStatus(app.getNsrID());
                break;
            case INITIALIZING:
                res = obmanager.getStatus(app.getNsrID());
                break;
            case INITIALISED:
                try {
                    res = osmanager.getStatus(token, app.getAppName(), app.getProjectName());
                }catch (ResourceAccessException e){
                    res = BuildingStatus.PAAS_RESOURCE_MISSING;
                }
                break;
            case BUILDING:
                try{
                    res = osmanager.getStatus(token, app.getAppName(),app.getProjectName());
                }catch (ResourceAccessException e){
                    res = BuildingStatus.PAAS_RESOURCE_MISSING;
                }
                break;
            case DEPLOYNG:
                try{
                    res = osmanager.getStatus(token, app.getAppName(),app.getProjectName());
                }catch (ResourceAccessException e){
                    res = BuildingStatus.PAAS_RESOURCE_MISSING;
                }
                break;
            case FAILED:
                logger.debug("FAILED: app has resource ok? " + app.isResourceOK());
                if (!app.isResourceOK()){
                    res = BuildingStatus.FAILED;
                    break;
                }
                else {
                    try {
                        res = osmanager.getStatus(token, app.getAppName(), app.getProjectName());
                    } catch (ResourceAccessException e) {
                        res = BuildingStatus.PAAS_RESOURCE_MISSING;
                    }
                }
                break;
            case RUNNING:
                try{
                    res = osmanager.getStatus(token, app.getAppName(),app.getProjectName());
                }catch (ResourceAccessException e){
                    res = BuildingStatus.PAAS_RESOURCE_MISSING;
                }
                break;
            case PAAS_RESOURCE_MISSING:
                res = osmanager.getStatus(token, app.getAppName(),app.getProjectName());
                break;
        }

        return res;
    }

//    @Scheduled(initialDelay = 0,fixedDelay = 200)
//    public void refreshStatus() throws ApplicationNotFoundException, UnauthorizedException {
//
//        for (String id : deploymentMap.keySet()){
//            boolean writed = false;
//            OpenbatonCreateServer ocs = deploymentMap.get(id);
//            Application app = this.getApp(ocs.getToken(),id);
//            if(app.getStatus() == BuildingStatus.RUNNING && !writed){
//                logger.info("[PAAS]: APP_RUNNING " + new Date().getTime());
//                writed = true;
//            }
//        }
//
//    }

}
