package edu.unc.mapseq.ws;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPBinding;

import org.junit.Test;

import edu.unc.mapseq.ws.bergmips.vc.BergMIPsVariantCallingService;

public class ServiceImplTest {

    @Test
    public void testAssertDirectoryExists() {
        QName serviceQName = new QName("http://vc.bergmips.ws.mapseq.unc.edu", "BergMIPsVariantCallingService");
        QName portQName = new QName("http://vc.bergmips.ws.mapseq.unc.edu", "BergMIPsVariantCallingPort");
        Service service = Service.create(serviceQName);
        service.addPort(portQName, SOAPBinding.SOAP11HTTP_MTOM_BINDING,
                String.format("http://%s:%d/cxf/BergMIPsVariantCallingService", "152.54.3.109", 8181));
        BergMIPsVariantCallingService variantCallingService = service.getPort(BergMIPsVariantCallingService.class);
        System.out.println(variantCallingService.getMetrics("FakeOSI02"));
    }

}
