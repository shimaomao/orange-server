package cn.orangeiot.apidao.handler.dao.ota;

import cn.orangeiot.apidao.client.MongoClient;
import com.sun.org.apache.bcel.internal.generic.PUTFIELD;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author zhang bo
 * @version 1.0
 * @Description
 * @date 2018-03-29
 */
public class OtaDao {

    private static Logger logger = LogManager.getLogger(OtaDao.class);


    /**
     * @Description 查詢產品類型
     * @author zhang bo
     * @date 18-3-29
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void selectModelType(Message<String> message) {
        logger.info("==selectModelType==params -> {}", message.body());

        MongoClient.client.runCommand("aggregate", new JsonObject().put("aggregate", "kdsModelInfo")
                .put("pipeline", new JsonArray().add(new JsonObject().put("$group", new JsonObject().put("_id"
                        , new JsonObject().put("modelCode", "$modelCode").put("childCode", "$childCode")
                                .put("time", "$time")))).add(new JsonObject().put("$sort", new JsonObject().put("_id.time", 1)))), rs -> {
            if (rs.failed()) {
                rs.cause().printStackTrace();
            } else {
                logger.info("selectModelType==mongo== result -> {}", rs.result());
                message.reply(rs.result());
            }
        });
    }


    /**
     * @Description 查詢時期範圍
     * @author zhang bo
     * @date 18-3-29
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void selectDateRange(Message<JsonObject> message) {
        logger.info("==selectDateRange==params -> {}", message.body());

        MongoClient.client.findWithOptions("kdsModelInfo", new JsonObject().put("modelCode", message.body().getString("modelCode"))
                        .put("childCode", message.body().getString("childCode"))
                , new FindOptions().setFields(new JsonObject().put("yearCode", 1).put("weekCode", 1).put("_id", 0))
                        .setSort(new JsonObject().put("weekCode", 1)), rs -> {
                    if (rs.failed()) {
                        rs.cause().printStackTrace();
                    } else {
                        logger.info("selectModelType==mongo== result -> {}", rs.result());
                        message.reply(new JsonArray(rs.result()));
                    }
                });
    }


    /**
     * @Description 查询編號範圍
     * @author zhang bo
     * @date 18-3-29
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void selectNumRange(Message<JsonObject> message) {
        logger.info("==selectNumRange==params -> {}", message.body());

        MongoClient.client.findWithOptions("kdsModelInfo", new JsonObject().put("modelCode", message.body().getString("modelCode"))
                        .put("childCode", message.body().getString("childCode")).put("yearCode", message.body().getString("yearCode"))
                        .put("weekCode", message.body().getString("weekCode"))
                , new FindOptions().setFields(new JsonObject().put("count", 1).put("_id", 0)), rs -> {
                    if (rs.failed()) {
                        rs.cause().printStackTrace();
                    } else {
                        logger.info("selectModelType==mongo== result -> {}", rs.result());
                        int sum = 0;
                        if (rs.result().size() > 0) {
                            sum = rs.result().stream().mapToInt(e -> new JsonObject(e.toString()).getInteger("count")).sum();
                        }
                        message.reply(sum);
                    }
                });
    }


    /**
     * @Description 提交ota升级的数据
     * @author zhang bo
     * @date 18-4-2
     * @version 1.0
     */
    public void submitOTAUpgrade(Message<JsonObject> message) {
        logger.info("==submitOTAUpgrade==params -> {}", message.body());

        MongoClient.client.insert("kdsOtaUpgrade", message.body().put("time",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))), rs -> {
            if (rs.failed()) rs.cause().printStackTrace();
        });

    }


    /**
     * @Description 獲取升級的設備
     * @author zhang bo
     * @date 18-4-11
     * @version 1.0
     */
    public void getUpgradeDevice(Message<JsonObject> message) {
        logger.info("==getUpgradeDevice==params -> {}", message.body());

        JsonObject paramsJsonObject = new JsonObject().put("modelCode", message.body().getString("modelCode"))
                .put("childCode", message.body().getString("childCode"));

        //生產日期範圍
        if (!message.body().getString("yearCode").equals("ALL")) {
            paramsJsonObject.put("yearCode", message.body().getString("yearCode"));
            if (!message.body().getString("weekCode").equals("ALL"))
                paramsJsonObject.put("weekCode", message.body().getString("weekCode"));
        }

        String[] arrs;
        //區分範圍
        if (message.body().getString("range").indexOf("-") > 0) {
            arrs = message.body().getString("range").split("-");
            paramsJsonObject.put("position", new JsonObject().put("$gte", Integer.parseInt(arrs[0])).put("$lte"
                    , Integer.parseInt(arrs[1])));
        } else {
            arrs = message.body().getString("range").split(",");
            List<Integer> lists = Arrays.stream(arrs).map(e -> Integer.parseInt(e)).collect(Collectors.toList());
            paramsJsonObject.put("position", new JsonObject().put("$in", new JsonArray(lists)));
        }

        //獲取設備PN號
        MongoClient.client.findWithOptions("kdsProductInfoList", paramsJsonObject,
                new FindOptions().setFields(new JsonObject().put("SN", 1).put("_id", 0)), rs -> {
                    if (rs.failed()) {
                        rs.cause().printStackTrace();
                        message.reply(null);
                    } else {
                        JsonObject pnJsonObject = new JsonObject();
                        List<String> _ids = rs.result().stream().map(e -> new JsonObject(e.toString()).getString("SN"))
                                .collect(Collectors.toList());
                        switch (message.body().getInteger("modelType")) {
                            case 1://网关
                                pnJsonObject.put("deviceSN", new JsonObject().put("$in", new JsonArray(_ids)));
                                break;
                            case 2://挂载设备
                                pnJsonObject.put("deviceList.devid"
                                        , new JsonObject().put("$in", new JsonArray(_ids)));
                                break;
                            default:
                                logger.warn("modelType type is not params -> {}", message.body().getInteger("modelType"));
                                message.reply(null);
                                return;
                        }
                        MongoClient.client.findWithOptions("kdsGatewayDeviceList", pnJsonObject
                                , new FindOptions().setFields(new JsonObject().put("deviceList.devid", 1)
                                        .put("deviceSN", 1).put("_id", 0).put("deviceList.status", 1)
                                        .put("adminuid", 1)), as -> {
                                    if (as.failed()) {
                                        as.cause().printStackTrace();
                                        message.reply(null);
                                    } else {
                                        message.reply(new JsonArray(
                                                as.result().stream().distinct().collect(Collectors.toList())));
                                    }
                                });
                    }
                });
    }

    /**
     * @Description 記錄審批時間日志
     * @author zhang bo
     * @date 18-4-27
     * @version 1.0
     */
    public void otaApprovateRecord(Message<JsonObject> message) {
        logger.info("params -> {}", message.body());
        MongoClient.client.insert("kdsOTARecord", message.body(), rs -> {
            if (rs.failed()) rs.cause().printStackTrace();
        });
    }
}
