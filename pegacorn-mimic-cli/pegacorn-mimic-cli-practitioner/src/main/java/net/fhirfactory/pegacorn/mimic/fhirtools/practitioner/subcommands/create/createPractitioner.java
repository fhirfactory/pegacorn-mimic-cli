/*
 * Copyright (c) 2021 Mark Hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.fhirfactory.pegacorn.mimic.fhirtools.practitioner.subcommands.create;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.fhirfactory.pegacorn.mimic.fhirtools.csvloaders.intermediary.SimplisticOrganization;
import net.fhirfactory.pegacorn.mimic.fhirtools.csvloaders.intermediary.TrivialOrganization;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.View;
import org.jgroups.blocks.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(
        name="create",
        description="Creates a Practitioner (and associated) Resources"
)
public class createPractitioner implements Runnable{
    private static final Logger LOG = LoggerFactory.getLogger(createPractitioner.class);
    private Address actualAddress;
    private JChannel organizationRPCClient;
    private RpcDispatcher rpcDispatcher;

    @CommandLine.Option(names = {"-n", "--name"})
    private String practitionerName;

    @CommandLine.Option(names = {"-e", "--email"})
    private String practitionerEmail;

    @CommandLine.Option(names = {"-x", "--extension"})
    private String extensionNumber;

    @CommandLine.Option(names = {"-m", "--mobile"})
    private String mobileNumber;

    @Override
    public void run() {
        LOG.info(".run(): Entry, Practitioner Name: {}, Practitioner Email: {}, Practitioner Extension: {}, Practitioner Mobile: {}",
                practitionerName, practitionerEmail, extensionNumber, mobileNumber);
        doCreate();
        System.exit(0);
    }

    public void doCreate(){
        LOG.info(".doLoadFromCSV(): Entry");

        OrganizationCSVReader cvsReader = new OrganizationCSVReader();
        cvsReader.readOrganizationCSV(this.extensionNumber);
        List<SimplisticOrganization> simplisticOrganizations = cvsReader.organiseOrganizationHierarchy();
        initialiseJGroupsChannel();
        RequestOptions requestOptions = new RequestOptions(ResponseMode.GET_FIRST, 5000);
        Class classes[] = new Class[1];
        classes[0] = String.class;
        for(SimplisticOrganization currentOrganization: simplisticOrganizations){
            Object objectSet[] = new Object[1];
            TrivialOrganization trivOrg = makeTrivial(currentOrganization);
            String currentTrivialOrganization = TrivalObjectAsJSONString(trivOrg);
            objectSet[0] = currentTrivialOrganization;
            try {
                LOG.info(".doLoadFromCSV(): Sending request --> {}", currentTrivialOrganization);
                String response = rpcDispatcher.callRemoteMethod(this.actualAddress, "processRequest", objectSet, classes, requestOptions);
                LOG.info(".doLoadFromCSV(): Response --> {}", response);
            } catch(Exception ex){
                ex.printStackTrace();
                LOG.error(".doLoadFromCSV(): Error --> {}", ex.toString());
            }
        }
        LOG.info(".doLoadFromCSV(): Exit");
    }

    void initialiseJGroupsChannel(){
        try {
            LOG.info(".initialiseJGroupsChannel(): Entry");
            this.organizationRPCClient = new JChannel("udp.xml").name("OrganizationRCPClient");
            this.organizationRPCClient.connect("ResourceCLI");
            View view = this.organizationRPCClient.view();
            List<Address> members = view.getMembers();
            for(Address member: members) {
                if ("OrganizationRPC".contentEquals(member.toString())) {
                    LOG.info(".initialiseJGroupsChannel(): Found Server Endpoint");
                    this.actualAddress = member;
                    break;
                }
            }
            this.rpcDispatcher = new RpcDispatcher(this.organizationRPCClient,null);
        } catch(Exception ex){
            LOG.error(".initialiseJGroupsChannel(): Error --> " + ex.toString());
        }
    }

    private String TrivalObjectAsJSONString(TrivialOrganization org){
        try{
            ObjectMapper jsonMapper = new ObjectMapper();
            String orgAsString = jsonMapper.writeValueAsString(org);
            return(orgAsString);
        } catch(Exception ex){
            ex.printStackTrace();
        }
        return("");
    }

    private TrivialOrganization makeTrivial(SimplisticOrganization simple){
        TrivialOrganization trivialOrganization = new TrivialOrganization();
        trivialOrganization.setOrganizationLongName(simple.getOrganizationLongName());
        trivialOrganization.setOrganizationShortName(simple.getOrganizationShortName());
        trivialOrganization.setOrganizationTypeCode(simple.getOrganizationTypeCode());
        trivialOrganization.setOrganizationTypeName(simple.getOrganizationTypeName());
        trivialOrganization.setParentOrganizationShortName(simple.getParentOrganizationShortName());
        for(SimplisticOrganization currentOrg: simple.getContainedOrgs()){
            trivialOrganization.getContainedOrganisationShortNames().add(currentOrg.getOrganizationShortName());
        }
        return(trivialOrganization);
    }

    public Address getActualAddress() {
        return actualAddress;
    }

    public void setActualAddress(Address actualAddress) {
        this.actualAddress = actualAddress;
    }

    public JChannel getOrganizationRPCClient() {
        return organizationRPCClient;
    }

    public void setOrganizationRPCClient(JChannel organizationRPCClient) {
        this.organizationRPCClient = organizationRPCClient;
    }

    public RpcDispatcher getRpcDispatcher() {
        return rpcDispatcher;
    }

    public void setRpcDispatcher(RpcDispatcher rpcDispatcher) {
        this.rpcDispatcher = rpcDispatcher;
    }

    public String getPractitionerName() {
        return practitionerName;
    }

    public void setPractitionerName(String practitionerName) {
        this.practitionerName = practitionerName;
    }

    public String getPractitionerEmail() {
        return practitionerEmail;
    }

    public void setPractitionerEmail(String practitionerEmail) {
        this.practitionerEmail = practitionerEmail;
    }

    public String getExtensionNumber() {
        return extensionNumber;
    }

    public void setExtensionNumber(String extensionNumber) {
        this.extensionNumber = extensionNumber;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }
}
