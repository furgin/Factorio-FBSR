package com.demod.fbsr.app;

import com.demod.fbsr.Blueprint;
import com.demod.fbsr.BlueprintStringData;
import com.demod.fbsr.FBSR;
import com.demod.fbsr.TaskReporting;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;

class LocalFileStorage {

    private static String md5(String message) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(message.getBytes());
        byte[] digest = md.digest();
        return DatatypeConverter.printHexBinary(digest).toLowerCase();
    }

    static File localStorageFile(File folder, Blueprint blueprint) throws Exception {
        String str = BlueprintStringData.encode(blueprint.json());
        String fileName = md5(str) + ".png";
        return new File(folder, fileName);
    }

    static File saveToLocalStorage(File folder, BufferedImage image, Blueprint blueprint) throws Exception {
        if (!folder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            folder.mkdirs();
        }

        File imageFile = localStorageFile(folder, blueprint);
        ImageIO.write(image, "PNG", imageFile);
        return imageFile;
    }

    @NotNull
    static Optional<File> loadFromLocalStorage(File folder, Blueprint blueprint) throws Exception {
        File imageFile = localStorageFile(folder, blueprint);
        if(imageFile.exists()) {
            return Optional.of(imageFile);
        } else {
            return Optional.empty();
        }
    }

    @NotNull
    static File loadOrRender(File folder, Blueprint blueprint, TaskReporting reporting) throws Exception {
        File imageFile = LocalFileStorage
                .loadFromLocalStorage(folder, blueprint)
                .orElseGet(()->{
                    try {
                        BufferedImage renderedImage = FBSR.renderBlueprint(blueprint, reporting, new JSONObject());
                        return LocalFileStorage.saveToLocalStorage(folder, renderedImage, blueprint);
                    } catch (Exception e) {
                        reporting.addException(e);
                        return null;
                    }
                });
        return Objects.requireNonNull(imageFile);
    }

}
