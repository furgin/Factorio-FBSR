package com.demod.fbsr.app;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import javax.swing.text.html.Option;
import javax.xml.bind.DatatypeConverter;

import com.demod.factorio.Config;
import com.demod.fbsr.Blueprint;
import com.demod.fbsr.BlueprintFinder;
import com.demod.fbsr.BlueprintStringData;
import com.demod.fbsr.FBSR;
import com.demod.fbsr.TaskReporting;
import com.google.common.util.concurrent.AbstractIdleService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.rapidoid.http.Req;
import org.rapidoid.http.Resp;
import org.rapidoid.setup.App;
import org.rapidoid.setup.Setup;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class BlueprintBotSlackService extends AbstractIdleService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String X_SLACK_REQUEST_TIMESTAMP = "x-slack-request-timestamp";
    private static final String X_SLACK_SIGNATURE = "x-slack-signature";
    private static OkHttpClient client = new OkHttpClient();
    private JSONObject configJson;
    private final ExecutorService exec = Executors.newFixedThreadPool(2);


    private static Optional<String> hash(String sharedKey, String message) {
        try {
            Mac hasher = Mac.getInstance("HmacSHA256");
            hasher.init(new SecretKeySpec(sharedKey.getBytes(), "HmacSHA256"));
            byte[] hash = hasher.doFinal(message.getBytes());
            return Optional.ofNullable(DatatypeConverter.printHexBinary(hash));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return Optional.empty();
        }
    }

    private void sendMessage(JSONObject result, String responseUrl) throws IOException {
        RequestBody body = RequestBody.create(JSON, result.toString());
        Request request = new Request.Builder()
                .url(responseUrl)
                .post(body)
                .build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            System.out.println("response: " + response.body().string());
        }
        response.close();
    }

    private class FindBlueprintTask implements Runnable {

        private final String content;
        private final String responseUrl;

        private FindBlueprintTask(String content, String responseUrl) {
            this.content = content;
            this.responseUrl = responseUrl;
        }

        @Override
        public void run() {
            TaskReporting reporting = new TaskReporting();

            try {
                JSONObject result = new JSONObject();
                JSONArray blocks = new JSONArray();
                result.put("blocks", blocks);

                List<BlueprintStringData> blueprintStrings = BlueprintFinder.search(content, reporting);
                List<Blueprint> blueprints = blueprintStrings.stream().flatMap(s -> s.getBlueprints().stream())
                        .collect(Collectors.toList());

                JSONObject messageSection = new JSONObject();
                messageSection.put("type", "section");

                if (!blueprints.isEmpty()) {

                    messageSection.put("type", "section");

                    JSONObject text = new JSONObject();
                    text.put("type", "mrkdwn");
                    text.put("text", "Blueprint Images");
                    messageSection.put("text", text);

                    for (Blueprint blueprint : blueprints) {
                        exec.execute(new BlueprintTask(blueprint, responseUrl, content));
                    }
                } else {

                    JSONObject text = new JSONObject();
                    text.put("type", "mrkdwn");
                    text.put("text", "No images were generated");
                    messageSection.put("text", text);

                }
                blocks.put(messageSection);

                sendMessage(result, responseUrl);

            } catch (Exception e) {
                reporting.addException(e);
            }

            if (!reporting.getExceptions().isEmpty()) {
                reporting.addInfo(
                        "There was a problem completing your request. I have contacted my programmer to fix it for you!");
                reporting.getExceptions().forEach(Exception::printStackTrace);
            }
        }

    }

    private class BlueprintTask implements Runnable {
        private final Blueprint blueprint;
        private final String responseUrl;
        private final String content;

        private BlueprintTask(Blueprint blueprint, String responseUrl, String content) {
            this.blueprint = blueprint;
            this.responseUrl = responseUrl;
            this.content = content;
        }

        @Override
        public void run() {

            TaskReporting reporting = new TaskReporting();
            try {

                JSONObject result = new JSONObject();
                result.put("response_type", "in_channel");

                JSONArray blocks = new JSONArray();
                result.put("blocks", blocks);

                JSONObject messageSection = new JSONObject();
                messageSection.put("type", "section");

                File localStorageFolder = new File(configJson.getString("local-storage"));
                File imageFile = LocalFileStorage.loadOrRender(localStorageFolder, blueprint, reporting);
                String imageLink = Objects.requireNonNull(imageFile).getName();

                reporting.addImage(blueprint.getLabel(), imageLink);
                reporting.addLink(imageLink);

                JSONObject imageTitle = new JSONObject();
                imageTitle.put("type", "plain_text");
                imageTitle.put("text", blueprint.getLabel().orElse("No Name"));
                imageTitle.put("emoji", false);

                JSONObject imageBlock = new JSONObject();
                imageBlock.put("alt_text", blueprint.getLabel().orElse("No Name"));
                imageBlock.put("title", imageTitle);
                imageBlock.put("image_url", configJson.getString("base-url") + imageLink);
                imageBlock.put("type", "image");

                blocks.put(messageSection);
                blocks.put(imageBlock);

                sendMessage(result, responseUrl);

            } catch (Exception e) {
                e.printStackTrace();
                reporting.addException(e);
            }

            if (!reporting.getExceptions().isEmpty()) {
                reporting.addInfo(
                        "There was a problem completing your request. I have contacted my programmer to fix it for you!");
                reporting.getExceptions().forEach(Exception::printStackTrace);
            }

        }
    }

    @Override
    protected void shutDown() {
        ServiceFinder.removeService(this);
        App.shutdown();
        exec.shutdown();
    }


    @Override
    protected void startUp() throws Exception {
        ServiceFinder.addService(this);
        configJson = Config.get().getJSONObject("slack");
        String address = configJson.optString("bind", "0.0.0.0");
        int port = configJson.optInt("port", 80);

        Setup slack = HttpServers.create(address, port);

//        slack.post("/events").serve((req, resp) -> {
//
//            System.out.println("Events Post!");
//            if (!validate(configJson.getString("signing-secret"), req, resp)) {
//                return resp;
//            }
//
//            JSONTokener tokener = new JSONTokener(new ByteArrayInputStream(req.body()));
//            JSONObject root = new JSONObject(tokener);
//
//            switch (root.getString("type")) {
//                case "url_verification":
//
//                    resp.header("Cache-Control", "no-cache, no-store, must-revalidate");
//                    resp.header("Pragma", "no-cache");
//                    resp.header("Expires", "0");
//                    resp.body(root.getString("challenge").getBytes());
//                    break;
//                case "event_callback":
//                    if (!validate(configJson.getString("signing-secret"), req, resp)) {
//                        return resp;
//                    }
//                    resp.code(200);
//                    break;
//                default:
//                    System.out.println("invalid event type: " + root.getString("type"));
//                    break;
//            }
//            return resp;
//        });

        slack.post("/command").serve((req, resp) -> {

            System.out.println("Slack Command POST!");
            TaskReporting reporting = new TaskReporting();

            if (req.body() == null) {
                resp.code(400);
                resp.plain("Body is empty!");
                reporting.addException(new IllegalArgumentException("Body is empty!"));
                return resp;
            }

            String content = req.posted("text");
            exec.execute(new FindBlueprintTask(content, req.posted().get("response_url").toString()));

            JSONObject result = new JSONObject();
            JSONArray blocks = new JSONArray();
            result.put("blocks", blocks);

            JSONObject messageSection = new JSONObject();
            messageSection.put("type", "section");

            JSONObject text = new JSONObject();
            text.put("type", "mrkdwn");
            text.put("text", "Parsing blueprint...");
            messageSection.put("text", text);
            blocks.put(messageSection);

            resp.contentType(org.rapidoid.http.MediaType.APPLICATION_JSON);
            resp.body(result.toString().getBytes());

            return resp;
        });

        System.out.println("Slack Bot Initialized at " + address + ":" + port);
    }

    private boolean validate(String secret, Req req, Resp resp) {

        if (!req.headers().containsKey(X_SLACK_REQUEST_TIMESTAMP)) {
            System.out.println("No timestamp header");
            resp.code(401);
            resp.body("No timestamp header".getBytes());
            return false;
        }

        if (!req.headers().containsKey(X_SLACK_SIGNATURE)) {
            System.out.println("Not signed");
            resp.code(401);
            resp.body("Not signed".getBytes());
            return false;
        }

        long timeStamp = Long.parseLong(req.header(X_SLACK_REQUEST_TIMESTAMP));
        long fiveMinutesAgo = (System.currentTimeMillis() / 1000) - 60 * 5;
        if (timeStamp < fiveMinutesAgo) {
            System.out.println("Timestamp expired: " + timeStamp + " < " + fiveMinutesAgo);
            resp.code(401);
            resp.body("Timestamp expired".getBytes());
            return false;
        }

        String message = "v0:" + timeStamp + ":" + new String(req.body());
        Optional<String> hash = hash(secret, message);

        if (hash.isPresent()) {
            String check = "v0=" + hash.get().toLowerCase();
            return check.equals(req.header(X_SLACK_SIGNATURE));
        } else {
            return false;
        }
    }

}
