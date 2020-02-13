package com.demod.fbsr.app;

import com.demod.factorio.Config;
import com.demod.fbsr.Blueprint;
import com.demod.fbsr.BlueprintFinder;
import com.demod.fbsr.BlueprintStringData;
import com.demod.fbsr.FBSR;
import com.demod.fbsr.TaskReporting;
import com.google.common.util.concurrent.AbstractIdleService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.rapidoid.http.MediaType;
import org.rapidoid.setup.App;
import org.rapidoid.setup.Setup;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ImageService extends AbstractIdleService {

    private JSONObject configJson;

    @Override
    protected void shutDown() throws Exception {
        ServiceFinder.removeService(this);
        App.shutdown();
    }

    @Override
    protected void startUp() throws Exception {
        ServiceFinder.addService(this);
        configJson = Config.get().getJSONObject("images");
        String address = configJson.optString("bind", "0.0.0.0");
        int port = configJson.optInt("port", 80);
        File folder = new File(configJson.optString("local-storage"));
        File factorioFolder = new File(Config.get().optString("factorio") + "/data/base/graphics/icons/");

        Setup images = HttpServers.create(address, port);

        images.get("/images/{file}").serve((req, resp) -> {

            resp.contentType(MediaType.IMAGE_PNG);
            resp.header("Cache-Control", "max-age=31556926, public");
            resp.file(new File(folder, req.param("file")));

            return resp;
        });

        images.get("/icons/{file}").serve((req, resp) -> {

            File iconFile = new File(factorioFolder, req.param("file"));
            if (iconFile.exists()) {
                BufferedImage image = ImageIO.read(iconFile);
                Image iconImage = (BufferedImage) image
                        .getSubimage(0, 0, image.getHeight(), image.getHeight());
                BufferedImage finalImage = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = (Graphics2D) finalImage.getGraphics();
                g2.drawImage(iconImage, 0, 0, 32, 32, null);
                g2.dispose();

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ImageIO.write(finalImage, "png", buffer);

                resp.contentType(MediaType.IMAGE_PNG);
                resp.header("Cache-Control", "max-age=31556926, public");
                resp.body(buffer.toByteArray());
            } else {
                resp.body("Not Found".getBytes());
                resp.code(404);
            }

            return resp;
        });

        System.out.println("Image Server Initialized at " + address + ":" + port);
    }
}
