package org.project.openbaton.nubomedia.api.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.annotations.Expose;
import org.project.openbaton.nubomedia.api.messages.BuildingStatus;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * Created by maa on 28.09.15.
 */
@Entity
public class Application {

    @Id
    private String appID;
    private String appName;
    private String projectName;
    private String route;
    private String nsrID;
    private String gitURL;
    private int[] targetPorts;
    private int[] ports;
    private String[] protocols;
    private int replicasNumber;
    private String secretName;
    private String flavor;
    private BuildingStatus status;
    @Expose(serialize = false,deserialize = false) private boolean resourceOK;

    public Application(String appID,String flavor, String appName, String projectName, String route, String nsrID, String gitURL, int[] targetPorts, int[] ports, String[] protocols, int replicasNumber, String secretName,boolean resourceOK) {
        this.appID = appID;
        this.flavor = flavor;
        this.appName = appName;
        this.projectName = projectName;
        this.route = route;
        this.nsrID = nsrID;
        this.gitURL = gitURL;
        this.targetPorts = targetPorts;

        if(ports == null){
            this.ports = targetPorts;
        }
        else{
            this.ports = ports;
        }

        this.protocols = protocols;
        this.replicasNumber = replicasNumber;
        this.secretName = secretName;
        this.status = BuildingStatus.CREATED;
        this.resourceOK = resourceOK;
    }

    public Application() {
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getNsrID() {
        return nsrID;
    }

    public void setNsrID(String nsrID) {
        this.nsrID = nsrID;
    }

    public String getGitURL() {
        return gitURL;
    }

    public void setGitURL(String gitURL) {
        this.gitURL = gitURL;
    }

    public int[] getTargetPorts() {
        return targetPorts;
    }

    public void setTargetPorts(int[] targetPorts) {
        this.targetPorts = targetPorts;
    }

    public int[] getPorts() {
        return ports;
    }

    public void setPorts(int[] ports) {
        this.ports = ports;
    }

    public String[] getProtocols() {
        return protocols;
    }

    public void setProtocols(String[] protocols) {
        this.protocols = protocols;
    }

    public int getReplicasNumber() {
        return replicasNumber;
    }

    public void setReplicasNumber(int replicasNumber) {
        this.replicasNumber = replicasNumber;
    }

    public String getSecretName() {
        return secretName;
    }

    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }

    public String getFlavor() {
        return flavor;
    }

    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }

    public String getAppID() {
        return appID;
    }

    public void setAppID(String appID) {
        this.appID = appID;
    }

    public BuildingStatus getStatus() {
        return status;
    }

    public void setStatus(BuildingStatus status) {
        this.status = status;
    }

    public boolean isResourceOK() {
        return resourceOK;
    }

    public void setResourceOK(boolean resourceOK) {
        this.resourceOK = resourceOK;
    }

    @Override
    public String toString(){
        return "Application with ID: " + appID  + "\n" +
                "Application name: " + appName + "\n" +
                "Project: " + projectName + "\n" +
                "Route: " + route + "\n" +
                "Git URL: " + gitURL;
    }
}
