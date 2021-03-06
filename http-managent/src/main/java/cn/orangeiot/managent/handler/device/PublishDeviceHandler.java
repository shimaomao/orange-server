package cn.orangeiot.managent.handler.device;

import cn.orangeiot.common.genera.ErrorType;
import cn.orangeiot.common.genera.Result;
import cn.orangeiot.common.options.SendOptions;
import cn.orangeiot.common.utils.DataType;
import cn.orangeiot.common.utils.UUIDUtils;
import cn.orangeiot.managent.utils.ExcelUtil;
import cn.orangeiot.managent.verify.VerifyParamsUtil;
import cn.orangeiot.reg.EventbusAddr;
import cn.orangeiot.reg.adminlock.AdminlockAddr;
import cn.orangeiot.reg.memenet.MemenetAddr;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.impl.AsyncFileImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.omg.CORBA.INTERNAL;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import scala.util.parsing.json.JSONArray;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * @author zhang bo
 * @version 1.0
 * @Description
 * @date 2018-01-02
 */
public class PublishDeviceHandler implements EventbusAddr {

    private static Logger logger = LogManager.getLogger(PublishDeviceHandler.class);


    private Vertx vertx;

    private JsonObject config;

    public PublishDeviceHandler(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }


    /**
     * @Description 生產測試用戶
     * @author zhang bo
     * @date 18-12-25
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void productionTestUser(RoutingContext routingContext) {
        logger.info("==PublishDeviceHandler=productionDeviceSN==params->" + routingContext.getBodyAsString());
        if (Objects.nonNull(routingContext.request().getParam("prefix"))
                && Objects.nonNull(routingContext.request().getParam("count"))) {
            vertx.eventBus().send(AdminlockAddr.class.getName() + PRODUCTION_TEST_USER, new JsonObject().put("prefix"
                    , routingContext.request().getParam("prefix")).put("count",
                    routingContext.request().getParam("count")), (AsyncResult<Message<JsonArray>> res) -> {
                if (res.failed()) {
                    logger.error(res.cause().getMessage(), res.cause());
                    routingContext.fail(501);
                } else {
                    if (Objects.nonNull(res.result()) && Objects.nonNull(res.result().body())) {
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        ExcelUtil.exportModelExcel("測試帳號", new HashMap<String, String>() {{
                            put("num", "序号");
                            put("userMail", "帳號");
                            put("userPwd","密碼");
                        }}, res.result().body(), null, 0, os);
                        byte[] content = os.toByteArray();
                        routingContext.response().setChunked(true).putHeader("Content-type", "application/octet-stream")
                                .putHeader("Content-Disposition", " attachment; filename=production_TestUsers.xlsx")
                                .write(Buffer.buffer().appendBytes(content)).end();//分块编码

                    } else {//失败
                        routingContext.response().end(JsonObject.mapFrom(new Result<String>()
                                .setErrorMessage(ErrorType.PRODUCTION_DEVICESN_FAIL.getKey(), ErrorType.PRODUCTION_DEVICESN_FAIL.getValue())).toString());
                    }
                }
            });
        } else {
            routingContext.fail(401);
        }
    }


    /**
     * @Description 生产设备SN号导入
     * @author zhang bo
     * @date 18-1-2
     * @version 1.0
     */
    public void productionDeviceSN(RoutingContext routingContext) {
        logger.info("==PublishDeviceHandler=productionDeviceSN==params->" + routingContext.getBodyAsString());
        if (Objects.nonNull(routingContext.request().getParam("count"))
                && Objects.nonNull(routingContext.request().getParam("child"))
                && Objects.nonNull(routingContext.request().getParam("model"))) {
            //数据入库
            vertx.eventBus().send(AdminlockAddr.class.getName() + MODEL_PRODUCT, new JsonObject()
                    .put("count", Integer.parseInt(routingContext.request().getParam("count")))
                    .put("child", routingContext.request().getParam("child"))
                    .put("model", routingContext.request().getParam("model"))
                    .put("secret", false), (AsyncResult<Message<JsonArray>> rs) -> {
                if (rs.failed()) {
                    logger.error(rs.cause().getMessage(), rs.cause());
                    routingContext.fail(501);
                } else {
                    if (Objects.nonNull(rs.result())) {
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        ExcelUtil.exportModelExcel("注册的SN设备号", new HashMap<String, String>() {{
                            put("num", "序号");
                            put("SN", "设备devuuid号");
                        }}, rs.result().body(), null, 0, os);
                        byte[] content = os.toByteArray();
                        routingContext.response().setChunked(true).putHeader("Content-type", "application/octet-stream")
                                .putHeader("Content-Disposition", " attachment; filename=production_deviceSN.xlsx")
                                .write(Buffer.buffer().appendBytes(content)).end();//分块编码

                    } else {//失败
                        routingContext.response().end(JsonObject.mapFrom(new Result<String>()
                                .setErrorMessage(ErrorType.PRODUCTION_DEVICESN_FAIL.getKey(), ErrorType.PRODUCTION_DEVICESN_FAIL.getValue())).toString());
                    }
                }

            });
        } else {
            routingContext.fail(401);
        }
    }


    /**
     * @Description 生产BLE SN设备号 password1
     * @author zhang bo
     * @date 18-1-23
     * @version 1.0
     */
    public void productionBLESN(RoutingContext routingContext) {
        logger.info("==PublishDeviceHandler=productionBLESN==params->" + routingContext.getBodyAsString());
        //校验数据
        if (Objects.nonNull(routingContext.request().getParam("count"))
                && Objects.nonNull(routingContext.request().getParam("child"))
                && Objects.nonNull(routingContext.request().getParam("model"))) {
            //数据入库
            vertx.eventBus().send(AdminlockAddr.class.getName() + MODEL_PRODUCT, new JsonObject()
                            .put("count", Integer.parseInt(routingContext.request().getParam("count")))
                            .put("child", routingContext.request().getParam("child"))
                            .put("model", routingContext.request().getParam("model"))
                            .put("secret", true)
                    , (AsyncResult<Message<JsonArray>> as) -> {
                        if (as.failed()) {
                            routingContext.fail(501);
                        } else {
                            if (Objects.nonNull(as.result().body())) {
                                ByteArrayOutputStream os = new ByteArrayOutputStream();
                                ExcelUtil.exportModelExcel("模块PN设备号", new HashMap<String, String>() {{
                                    put("num", "序号");
                                    put("SN", "模块PN号");
                                    put("password1", "password1");
                                }}, as.result().body(), null, 0, os);
                                byte[] content = os.toByteArray();
                                routingContext.response().setChunked(true).putHeader("Content-type", "application/octet-stream")
                                        .putHeader("Content-Disposition", " attachment; filename=production_ModelSN.xlsx")
                                        .write(Buffer.buffer().appendBytes(content)).end();//分块编码
                            }
                        }
                    });
        } else {
            routingContext.fail(401);
        }

    }


    /**
     * @Description 上传mac地址
     * @author zhang bo
     * @date 18-1-26
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void uploadMacAddr(RoutingContext routingContext) {
        logger.info("==PublishDeviceHandler=productionDeviceSN==params->" + routingContext.getBodyAsString());
        VerifyParamsUtil.verifyParams(routingContext, new JsonObject().put("SN", DataType.STRING)
                .put("password1", DataType.STRING).put("mac", DataType.STRING), rs -> {
            if (rs.failed()) {
                routingContext.fail(401);
            } else {
                vertx.eventBus().send(AdminlockAddr.class.getName() + MODEL_MAC_IN, rs.result()
                        , SendOptions.getInstance(), as -> {
                            if (as.failed()) {
                                routingContext.fail(501);
                            } else {
                                if (Objects.nonNull(as.result())) {
                                    routingContext.response().end(JsonObject.mapFrom(new Result<>()).toString());
                                } else {
                                    if (!as.result().headers().isEmpty())
                                        routingContext.response().end(JsonObject.mapFrom(
                                                new Result<>().setErrorMessage(Integer.parseInt(as.result().headers().get("code")), as.result().headers().get("msg"))).toString());
                                    else
                                        routingContext.fail(501);
                                }
                            }
                        });
            }
        });
    }


    /**
     * @Description 上传文件映射mac地址
     * @author zhang bo
     * @date 18-1-26
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void uploadMacFile(RoutingContext routingContext) {
        for (FileUpload f : routingContext.fileUploads()) {
            Buffer fileByteBuffer = vertx.fileSystem().readFileBlocking(f.uploadedFileName());

            try {
                //解析数据集
                JsonArray jsonArray = ExcelUtil.readExcelContent(new ByteArrayInputStream(fileByteBuffer.getBytes()));

                vertx.eventBus().send(AdminlockAddr.class.getName() + MODEL_MANY_MAC_IN, jsonArray,
                        new DeliveryOptions().setSendTimeout(30000), rs -> {
                            if (rs.failed()) {
                                routingContext.fail(501);
                            } else {
                                routingContext.response().end(JsonObject.mapFrom(
                                        new Result<>().setData(rs.result().body())).toString());
                            }
                        });

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    /**
     * @Description 上传设备测试信息
     * @author zhang bo
     * @date 18-1-26
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void uploadDeviceTestInfo(RoutingContext routingContext) {
        for (FileUpload f : routingContext.fileUploads()) {
            Buffer fileByteBuffer = vertx.fileSystem().readFileBlocking(f.uploadedFileName());

            try {
                //解析数据集
                JsonArray jsonArray = ExcelUtil.readExcelContent(new ByteArrayInputStream(fileByteBuffer.getBytes()));

                vertx.eventBus().send(AdminlockAddr.class.getName() + DEVICE_TEST_INFO_IN, jsonArray,
                        new DeliveryOptions().setSendTimeout(30000), rs -> {
                            if (rs.failed()) {
                                routingContext.fail(501);
                            } else {
                                routingContext.response().end(JsonObject.mapFrom(
                                        new Result<>().setData(rs.result().body())).toString());
                            }
                        });

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }


    /**
     * @Description 上傳設備預綁定
     * @author zhang bo
     * @date 18-1-26
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void uploadDeviceBind(RoutingContext routingContext) {
        for (FileUpload f : routingContext.fileUploads()) {
            Buffer fileByteBuffer = vertx.fileSystem().readFileBlocking(f.uploadedFileName());

            try {
                //解析数据集
                JsonArray jsonArray = ExcelUtil.readExcelContent(new ByteArrayInputStream(fileByteBuffer.getBytes()));

                vertx.eventBus().send(AdminlockAddr.class.getName() + UPDATE_PRE_BIND_DEVICE, jsonArray,
                        new DeliveryOptions().setSendTimeout(30000), rs -> {
                            if (rs.failed()) {
                                routingContext.fail(501);
                            } else {
                                routingContext.response().end(JsonObject.mapFrom(
                                        new Result<>().setData(rs.result().body())).toString());
                            }
                        });

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }


    /**
     * @Description 获取mac写入结果
     * @author zhang bo
     * @date 18-1-26
     * @version 1.0
     */
    @SuppressWarnings("Duplicates")
    public void getWriteMacResult(RoutingContext routingContext) {
        VerifyParamsUtil.verifyParams(routingContext, new JsonObject().put("modelCode", DataType.STRING)
                .put("childCode", DataType.STRING).put("yearCode", DataType.STRING).put("weekCode", DataType.STRING), rs -> {
            if (rs.failed()) {
                routingContext.fail(401);
            } else {
                vertx.eventBus().send(AdminlockAddr.class.getName() + GET_WRITE_MAC_RESULT, rs.result(), as -> {
                    if (as.failed()) {
                        routingContext.fail(501);
                    } else {
                        if (Objects.nonNull(as.result().body())) {
                            routingContext.response().end(JsonObject.mapFrom(
                                    new Result<>().setErrorMessage(ErrorType.UOLOAD_VERIFY_DATA_MAC_FIAL.getKey()
                                            , ErrorType.UOLOAD_VERIFY_DATA_MAC_FIAL.getValue())
                                            .setData(as.result().body())).toString());
                        } else {
                            routingContext.response().end(JsonObject.mapFrom(
                                    new Result<>()).toString());
                        }
                    }
                });
            }
        });
    }

}
