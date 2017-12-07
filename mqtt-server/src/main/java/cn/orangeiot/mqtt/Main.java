package cn.orangeiot.mqtt;

import cn.orangeiot.mqtt.prometheus.PromMetricsExporter;
import cn.orangeiot.mqtt.rest.RestApiVerticle;
import cn.orangeiot.mqtt.verticle.PublishVerticle;
import cn.orangeiot.mqtt.verticle.SubscibeVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.spi.cluster.zookeeper.ZookeeperClusterManager;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Giovanni Baleani on 13/11/2015.
 */
public class Main {

    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        start(args);
    }

    static CommandLine cli(String[] args) {
        CLI cli = CLI.create("java -jar <mqtt-broker>-fat.jar")
                .setSummary("A vert.x MQTT Broker")
                .addOption(new Option()
                        .setLongName("conf")
                        .setShortName("c")
                        .setDescription("vert.x config file (in json format)")
                        .setRequired(true)
                )
                .addOption(new Option()
                        .setLongName("zookeeper-conf")
                        .setShortName("hc")
                        .setDescription("vert.x zookeeper configuration file")
                        .setRequired(false)
                )
                .addOption(new Option()
                        .setLongName("zookeeper-host")
                        .setShortName("hh")
                        .setDescription("vert.x zookeeper ip address of this node (es. -hh 10.0.0.1)")
                        .setRequired(false)
                );

        // parsing
        CommandLine commandLine = null;
        try {
            List<String> userCommandLineArguments = Arrays.asList(args);
            commandLine = cli.parse(userCommandLineArguments);
        } catch (CLIException e) {
            // usage
            StringBuilder builder = new StringBuilder();
            cli.usage(builder);
            System.out.println(builder.toString());
//            throw e;
        }
        return commandLine;
    }

    public static void start(String[] args) {
        CommandLine commandLine = cli(args);
        if (commandLine == null)
            System.exit(-1);

        String confFilePath = commandLine.getOptionValue("c");
        String zookeeperConfFilePath = commandLine.getOptionValue("hc");

        DeploymentOptions deploymentOptions = new DeploymentOptions();
        if (confFilePath != null) {
            try {
                String json = FileUtils.readFileToString(new File(confFilePath), "UTF-8");
                JsonObject config = new JsonObject(json);
                deploymentOptions.setConfig(config);
            } catch (IOException e) {
                logger.fatal(e.getMessage(), e);
            }
        }

        //zookeeper集群
        if (zookeeperConfFilePath != null) {
            try {
                String zkJson = FileUtils.readFileToString(new File(zookeeperConfFilePath), "UTF-8");
                JsonObject zkConfig = new JsonObject(zkJson);

                System.setProperty("vertx.zookeeper.hosts",zkConfig.getString("hosts.zookeeper"));
                ClusterManager mgr = new ZookeeperClusterManager(zkConfig);
                VertxOptions options = new VertxOptions().setClusterManager(mgr);

                Vertx.clusteredVertx(options, res -> {
                    if (res.succeeded()) {
                        Vertx vertx = res.result();
                        vertx.deployVerticle(MQTTBroker.class.getName(), deploymentOptions);
                        vertx.deployVerticle(RestApiVerticle.class.getName(), deploymentOptions);
                        vertx.deployVerticle(PromMetricsExporter.class.getName(), deploymentOptions);

                        vertx.deployVerticle(SubscibeVerticle.class.getName(), deploymentOptions);
                        vertx.deployVerticle(PublishVerticle.class.getName(), deploymentOptions);

                    } else {
                        // failed!
                        logger.fatal(res.cause().getMessage(), res.cause());
                    }
                });
            } catch (IOException e) {
                logger.fatal(e.getMessage(), e);
            }

//        // use Vert.x CLI per gestire i parametri da riga di comando
//        if(hazelcastConfFilePath!=null) {
//            try {
//                Config hazelcastConfig = new FileSystemXmlConfig(hazelcastConfFilePath);
//                if(hazelcastMembers!=null) {
//                    NetworkConfig network = hazelcastConfig.getNetworkConfig();
//                    JoinConfig join = network.getJoin();
//                    join.getMulticastConfig().setEnabled(false);
//                    TcpIpConfig tcpIp = join.getTcpIpConfig();
//                    for (String member : hazelcastMembers) {
//                        tcpIp.addMember(member);
//                    }
//                    tcpIp.setEnabled(true);
//                }
//
//                ClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
//
//                VertxOptions options = new VertxOptions().setClusterManager(mgr).setClustered(true);
//                if(clusterHost != null) {
//                    options.setClusterHost(clusterHost);
//
//                    NetworkConfig network = hazelcastConfig.getNetworkConfig();
//                    InterfacesConfig interfaces = network.getInterfaces();
//                    interfaces.setEnabled(true);
//                    interfaces.addInterface(clusterHost);
//                }
//
//                logger.info("Hazelcast public address: " +
//                        hazelcastConfig.getNetworkConfig().getPublicAddress());
//                logger.info("Hazelcast tcp-ip members: " +
//                        hazelcastConfig.getNetworkConfig().getJoin().getTcpIpConfig().getMembers());
//                logger.info("Hazelcast port: " +
//                        hazelcastConfig.getNetworkConfig().getPort());
//                logger.info("Hazelcast poutbound ports: " +
//                        hazelcastConfig.getNetworkConfig().getOutboundPorts());
//                logger.info("Hazelcast interfaces: " +
//                        hazelcastConfig.getNetworkConfig().getInterfaces());
//                logger.info("Hazelcast network config: " +
//                        hazelcastConfig.getNetworkConfig().toString());
//
//                options.setMetricsOptions(new DropwizardMetricsOptions()
//                        .setEnabled(true)
//                        .setJmxEnabled(true)
//                );
//                Vertx.clusteredVertx(options, res -> {
//                    if (res.succeeded()) {
//                        Vertx vertx = res.result();
//                        vertx.deployVerticle(MQTTBroker.class.getName(), deploymentOptions);
//                        vertx.deployVerticle(RestApiVerticle.class.getName(), deploymentOptions);
//                        vertx.deployVerticle(PromMetricsExporter.class.getName(), deploymentOptions);
//                    } else {
//                        // failed!
//                        logger.fatal(res.cause().getMessage(), res.cause());
//                    }
//                });
//            } catch (FileNotFoundException e) {
//                logger.fatal(e.getMessage(), e);
//            }
        } else {
            VertxOptions options = new VertxOptions();
            options.setMetricsOptions(new DropwizardMetricsOptions()
                    .setEnabled(true)
                    .setJmxEnabled(true)
            );

            Vertx vertx = Vertx.vertx(options);
            vertx.deployVerticle(MQTTBroker.class.getName(), deploymentOptions);
            vertx.deployVerticle(RestApiVerticle.class.getName(), deploymentOptions);
            vertx.deployVerticle(PromMetricsExporter.class.getName(), deploymentOptions);

            vertx.deployVerticle(SubscibeVerticle.class.getName(), deploymentOptions);
            vertx.deployVerticle(PublishVerticle.class.getName(), deploymentOptions);
        }

    }


    public static void stop(String[] args) {
        System.exit(0);
    }

}
