/*
 * Copyright 2020-present Open Networking Foundation
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
package nctu.winlab.unicastdhcp;

import com.google.common.collect.ImmutableSet;
import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;

import static org.onlab.util.Tools.get;

/**
additional imports
 */
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.UDP;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.host.HostService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.HostId;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.onosproject.net.Path;
import org.onosproject.net.Link;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.topology.PathService;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;


import java.util.Set;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class unicastDHCP{

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final NameConfigListener cfgListener = new NameConfigListener();

    private final ConfigFactory factory =
        new ConfigFactory<ApplicationId, NameConfig>(
            APP_SUBJECT_FACTORY, NameConfig.class, "UnicastDhcpConfig") {
              @Override
              public NameConfig createConfig(){
                return new NameConfig();
              }
            };

    private dhcpPacketProcessor processor = new dhcpPacketProcessor();

    private ApplicationId appId;

    private String serverLocation;

    private ConnectPoint destination;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.unicastdhcp");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);
        packetService.addProcessor(processor, PacketProcessor.director(2));
        controllerRequests();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);
        controllerWithdraws();
        packetService.removeProcessor(processor);
        processor = null;
        log.info("Stopped");
    }

    private void controllerRequests(){
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol((byte)17)
                .matchUdpSrc(TpPort.tpPort(67))
                .matchUdpDst(TpPort.tpPort(68));
        TrafficSelector.Builder selector1 = DefaultTrafficSelector.builder();
        selector1.matchEthType(Ethernet.TYPE_IPV4)
                 .matchIPProtocol((byte)17)
                 .matchUdpSrc(TpPort.tpPort(68))
                 .matchUdpDst(TpPort.tpPort(67));
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        packetService.requestPackets(selector1.build(), PacketPriority.REACTIVE, appId);
    }

    private void controllerWithdraws(){
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol((byte)17)
                .matchUdpSrc(TpPort.tpPort(67))
                .matchUdpDst(TpPort.tpPort(68));
        TrafficSelector.Builder selector1 = DefaultTrafficSelector.builder();
        selector1.matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol((byte)17)
                .matchUdpSrc(TpPort.tpPort(68))
                .matchUdpDst(TpPort.tpPort(67));
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        packetService.cancelPackets(selector1.build(), PacketPriority.REACTIVE, appId);
    }

    private class NameConfigListener implements NetworkConfigListener{
        @Override
        public void event(NetworkConfigEvent event){
            if((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
              && event.configClass().equals(NameConfig.class)){
                NameConfig config = cfgService.getConfig(appId, NameConfig.class);
                if(config != null){
                  log.info("DHCP server is at {}", config.name());
                  serverLocation = config.name();
                  destination = ConnectPoint.fromString(serverLocation);
                }
              }
        }
    }

    private class dhcpPacketProcessor implements PacketProcessor{
        @Override
        public void process(PacketContext context){
            if(context.isHandled()){
                return;
            }
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            if(ethPkt == null){
                return;
            }
            if(ethPkt.getEtherType() == Ethernet.TYPE_ARP) return;
            Set<Path> paths;
            if(ethPkt.isBroadcast()){
                paths = topologyService.getPaths(topologyService.currentTopology(),
                                                 pkt.receivedFrom().deviceId(),
                                                 destination.deviceId());
                installRule(context, destination);
            } else {
                HostId id = HostId.hostId(ethPkt.getDestinationMAC());
                Host dst =  hostService.getHost(id);
                paths = topologyService.getPaths(topologyService.currentTopology(),
                                                 pkt.receivedFrom().deviceId(),
                                                 dst.location().deviceId());
                //rule from last device to host
                installRule(context, new ConnectPoint(dst.location().deviceId(),
                                                      dst.location().port()));
            }
            if(!paths.isEmpty()){
                Path path = getaPath(paths, pkt.receivedFrom().port());
                for(Link link : path.links()){
                    //log.info("{}", link);
                    installRule(context, link.src());
                }
            }
            context.treatmentBuilder().setOutput(PortNumber.TABLE);
            context.send();
        }
    }

    private Path getaPath(Set<Path> paths, PortNumber notToPort){
        for(Path path : paths){
            if(!path.src().port().equals(notToPort)){
                return path;
            }
        }
        return null;
    }

    private void installRule(PacketContext context, ConnectPoint device){
        Ethernet inPkt = context.inPacket().parsed();
        IPv4 inIPv4 = (IPv4) inPkt.getPayload();
        UDP inUDP = (UDP) inIPv4.getPayload();
        TpPort srcport = TpPort.tpPort(inUDP.getSourcePort());
        TpPort dstport = TpPort.tpPort(inUDP.getDestinationPort());
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
                       .matchIPProtocol((byte)17)
                       .matchUdpSrc(srcport)
                       .matchUdpDst(dstport)
                       .matchEthSrc(inPkt.getSourceMAC())
                       .matchEthDst(inPkt.getDestinationMAC());
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(device.port()).build();
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                                                                            .withSelector(selectorBuilder.build())
                                                                            .withTreatment(treatment)
                                                                            .fromApp(appId)
                                                                            .withPriority(50)
                                                                            .withFlag(ForwardingObjective.Flag.VERSATILE)
                                                                            .makeTemporary(100)
                                                                            .add();
        flowObjectiveService.forward(device.deviceId(), forwardingObjective);
    }

}
