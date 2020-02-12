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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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

        Setup images = HttpServers.create(address, port);

        images.get("/images/{file}").serve((req, resp) -> {

            resp.contentType(MediaType.IMAGE_PNG);
            resp.header("Cache-Control", "max-age=31556926, public");
            resp.file(new File(folder, req.param("file")));

            return resp;
        });

        System.out.println("Image Server Initialized at " + address + ":" + port);
    }
}
