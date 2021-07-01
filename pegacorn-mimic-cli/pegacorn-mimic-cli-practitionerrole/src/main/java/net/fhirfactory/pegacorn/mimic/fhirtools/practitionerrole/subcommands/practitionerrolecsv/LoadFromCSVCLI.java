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
package net.fhirfactory.pegacorn.mimic.fhirtools.practitionerrole.subcommands.practitionerrolecsv;

import java.util.List;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.View;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RpcDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.fhirfactory.buildingblocks.esr.resources.PractitionerRoleESR;
import net.fhirfactory.pegacorn.mimic.fhirtools.csvloaders.cvsentries.PractitionerRoleCSVEntry;
import net.fhirfactory.pegacorn.mimic.fhirtools.csvloaders.intermediary.PractitionerRoleESRApproximate;
import picocli.CommandLine;

@CommandLine.Command(
        name="loadCSV",
        description="Loads PractitionerRole (and some Role & Organization) Elements into a FHIR Service"
)
public class LoadFromCSVCLI implements Runnable{
    private static final Logger LOG = LoggerFactory.getLogger(LoadFromCSVCLI.class);

    private Address actualAddress;
    private JChannel organizationRPCClient;
    private RpcDispatcher rpcDispatcher;

    @CommandLine.Option(names = {"-f", "--filename"})
    private String fileName;

    @Override
    public void run() {
        doLoadFromCSV();
        System.exit(0);
    }

    public void doLoadFromCSV(){
        LOG.info(".doLoadFromCSV(): Entry");

        PractitionerRoleCSVReader cvsReader = new PractitionerRoleCSVReader();
        List<PractitionerRoleCSVEntry> prCSVSet = cvsReader.readOPractitionerRoleCSV(this.fileName);
        List<PractitionerRoleESRApproximate> prSet = cvsReader.convertCSVEntry2PractitionerRoleLite(prCSVSet);
        initialiseJGroupsChannel();
        RequestOptions requestOptions = new RequestOptions(ResponseMode.GET_FIRST, 5000);
        Class classes[] = new Class[1];
        classes[0] = String.class;
        for(PractitionerRoleESR currentPR: prSet){
            Object objectSet[] = new Object[1];
            String currentSimplisticPR = PractitionerRoleLiteAsJSONString(currentPR);
            objectSet[0] = currentSimplisticPR;
            try {
                LOG.info(".doLoadFromCSV(): Sending request --> {}", currentSimplisticPR);
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
            this.organizationRPCClient = new JChannel("udp.xml").name("PractitionerRoleRCPClient");
            this.organizationRPCClient.connect("ResourceCLI");
            View view = this.organizationRPCClient.view();
            List<Address> members = view.getMembers();
            for(Address member: members) {
                if ("PractitionerRoleRPC".contentEquals(member.toString())) {
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

    private String PractitionerRoleLiteAsJSONString(PractitionerRoleESR practitionerRoleESR){
        try{
            ObjectMapper jsonMapper = new ObjectMapper();
            String prAsString = jsonMapper.writeValueAsString(practitionerRoleESR);
            return(prAsString);
        } catch(Exception ex){
            ex.printStackTrace();
        }
        return("");
    }
}
