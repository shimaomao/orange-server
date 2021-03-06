package cn.orangeiot.memenet.verticle;

import cn.orangeiot.memenet.handler.RegisterHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.zookeeper.ZookeeperClusterManager;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * @author zhang bo
 * @version 1.0
 * @Description
 * @date 2017-11-23
 */
@SuppressWarnings("Duplicates")
public class MemeNetVerticle extends AbstractVerticle {

    private static Logger logger = LogManager.getLogger(MemeNetVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        /**
         * 加载zk config
         */
        InputStream zkIn = MemeNetVerticle.class.getResourceAsStream("/zkConf.json");
        InputStream configIn = MemeNetVerticle.class.getResourceAsStream("/config.json");//全局配置
        String zkConf = "";//jdbc连接配置
        String config = "";
        try {
            zkConf = IOUtils.toString(zkIn, "UTF-8");//获取配置
            config = IOUtils.toString(configIn, "UTF-8");

            if (!zkConf.equals("")) {
                JsonObject json = new JsonObject(zkConf);
                JsonObject configJson = new JsonObject(config);

                if (Objects.nonNull(System.getProperty("CLUSTER")))
                    json.put("rootPath", System.getProperty("CLUSTER"));

                System.setProperty("vertx.zookeeper.hosts", json.getString("hosts.zookeeper"));
                ClusterManager mgr = new ZookeeperClusterManager(json);
                VertxOptions options = new VertxOptions().setClusterManager(mgr);
                if (Objects.nonNull(json.getValue("node.host")))
                    options.setClusterHost(json.getString("node.host"));

                //集群
                RegisterHandler registerHandler = new RegisterHandler(configJson);
                Vertx.clusteredVertx(options, registerHandler::consumer);
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            startFuture.failed();
        } finally {
            if (null != zkIn)
                zkIn.close();
            if (null != configIn)
                configIn.close();
        }


        startFuture.complete();
    }
}
